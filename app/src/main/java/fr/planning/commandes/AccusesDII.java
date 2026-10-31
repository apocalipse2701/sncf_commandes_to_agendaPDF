package fr.planning.commandes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suivi des DII envoyées par l'application : chaque envoi direct est gardé (files/dii-envois.json) avec son
 * Message-ID ; pendant 30 jours, la boîte de réception est fouillée (lecture seule) pour les messages qui le citent :
 * avis de remise (DSN), accusé de lecture (MDN), réponse. Chaque nouveauté est notée et notifiée.
 * Même logique que planning_pdf.py (classer_retour, verifier_accuses_dii).
 */
final class AccusesDII {

    static final int JOURS_SUIVI = 30, MAX_ENVOIS = 90;
    static final String CANAL = "accuses";
    static final int ID_NOTIF = 44;
    static final String[] NATURES = {"remise", "lecture", "reponse", "echec", "retard"};
    private static final Object VERROU = new Object();

    private AccusesDII() {
    }

    private static File fichier(Context c) {
        return new File(c.getFilesDir(), "dii-envois.json");
    }

    /** Les envois gardés, du plus ancien au plus récent. */
    static JSONArray liste(Context c) {
        File f = fichier(c);
        if (!f.exists()) return new JSONArray();
        try {
            return new JSONArray(new String(BulletinsRanges.lire(f), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void ecrire(Context c, JSONArray l) {
        try (OutputStream out = new FileOutputStream(fichier(c))) {
            out.write(l.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // pas grave : le suivi de cet envoi est perdu
        }
    }

    /** Garde un envoi : {id, a, sujet, nom, jour, le (ms), accuse, remiseDemandee}. */
    static void ajouter(Context c, JSONObject e) {
        synchronized (VERROU) {
            JSONArray l = liste(c), n = new JSONArray();
            for (int i = Math.max(0, l.length() - (MAX_ENVOIS - 1)); i < l.length(); i++) n.put(l.opt(i));
            n.put(e);
            ecrire(c, n);
        }
    }

    static boolean aSuivre(Context c) {
        JSONArray l = liste(c);
        long limite = System.currentTimeMillis() - JOURS_SUIVI * 86400000L;
        for (int i = 0; i < l.length(); i++) {
            JSONObject e = l.optJSONObject(i);
            if (e != null && !e.optString("id").isEmpty() && e.optLong("le") >= limite) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ classement d'un message reçu

    /** Nature d'un message qui cite messageId : remise, echec, retard, lecture, reponse ou "" (sans rapport). */
    static String classer(String brut, String messageId) {
        String[] coupe = brut.split("\r?\n\r?\n", 2);
        String entete = coupe[0].replaceAll("\r?\n[ \t]+", " ");
        String mid = messageId == null ? "" : messageId.trim().toLowerCase(Locale.ROOT);
        Matcher propre = Pattern.compile("(?im)^message-id:\\s*(\\S+)").matcher(entete);
        if (propre.find() && propre.group(1).trim().toLowerCase(Locale.ROOT).equals(mid)) return "";   // le message envoyé
        String bas = brut.toLowerCase(Locale.ROOT);
        Matcher su = Pattern.compile("(?im)^subject:\\s*(.*)$").matcher(entete);
        String sujet = su.find() ? su.group(1).trim().toLowerCase(Locale.ROOT) : "";
        if (bas.contains("disposition-notification")) {
            Matcher d = Pattern.compile("(?im)^disposition:\\s*[^;\\r\\n]*;\\s*([a-z-]+)").matcher(brut);
            return !d.find() || d.group(1).equalsIgnoreCase("displayed") ? "lecture" : "";
        }
        if (bas.contains("delivery-status")) {
            Matcher a = Pattern.compile("(?im)^action:\\s*([a-z]+)").matcher(brut);
            boolean remis = false, echec = false, retard = false;
            while (a.find()) {
                String x = a.group(1).toLowerCase(Locale.ROOT);
                if (x.equals("failed")) echec = true;
                else if (x.equals("delivered") || x.equals("relayed") || x.equals("expanded")) remis = true;
                else if (x.equals("delayed")) retard = true;
            }
            return echec ? "echec" : remis ? "remise" : retard ? "retard" : "";
        }
        if (sujet.matches("^(lu|read|lecture)\\s*:.*")) return "lecture";             // accusés « maison » (Exchange…)
        if (sujet.matches("^(remis|delivered|distribu[ée])\\s*:.*")) return "remise";
        StringBuilder refs = new StringBuilder();
        Matcher r = Pattern.compile("(?im)^(?:in-reply-to|references):\\s*(.*)$").matcher(entete);
        while (r.find()) refs.append(r.group(1)).append(' ');
        return !mid.isEmpty() && refs.toString().toLowerCase(Locale.ROOT).contains(mid) ? "reponse" : "";
    }

    /** Date (en-tête Date:) d'un message, ou maintenant. */
    static long dateDe(String brut) {
        Matcher m = Pattern.compile("(?im)^date:\\s*(.+)$").matcher(brut.split("\r?\n\r?\n", 2)[0]);
        if (m.find()) {
            String d = m.group(1).replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
            for (String f : new String[]{"EEE, d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm Z"}) {
                try {
                    Date x = new SimpleDateFormat(f, Locale.ENGLISH).parse(d);
                    if (x != null) return x.getTime();
                } catch (java.text.ParseException e) {
                    // format suivant
                }
            }
        }
        return System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ vérification

    /**
     * Fouille la boîte pour les envois des 30 derniers jours. Renvoie {ok, texte, envois, nouveaux: [{jour, a, nature, quand}]}.
     * notifier : notification Android pour chaque nouveauté. Ne lève jamais d'exception.
     */
    static JSONObject verifier(Context c, boolean notifier) {
        JSONObject res = new JSONObject();
        JSONArray nouveaux = new JSONArray();
        boolean ok = false;
        String texte;
        synchronized (VERROU) {
            try {
                SharedPreferences p = ReleveMail.prefs(c);
                String adresse = p.getString("adresse", ""), mdpChiffre = p.getString("mdp", "");
                if (adresse.isEmpty() || mdpChiffre.isEmpty()) throw new IOException("boîte mail non réglée (Réglages › Commandes par mail)");
                String mdp;
                try {
                    mdp = CoffreMail.dechiffrer(mdpChiffre);
                } catch (Exception e) {
                    throw new IOException("mot de passe à ressaisir dans les réglages");
                }
                JSONArray l = liste(c);
                long limite = System.currentTimeMillis() - JOURS_SUIVI * 86400000L;
                Map<String, Long> depuis = new LinkedHashMap<>();
                for (int i = 0; i < l.length(); i++) {
                    JSONObject e = l.optJSONObject(i);
                    if (e != null && !e.optString("id").isEmpty() && e.optLong("le") >= limite) depuis.put(e.optString("id"), e.optLong("le"));
                }
                if (!depuis.isEmpty()) {
                    Map<String, List<byte[]>> r = new CourrierImap().retours(p.getString("serveur", ReleveMail.SERVEUR_DEFAUT),
                            p.getInt("port", ReleveMail.PORT_DEFAUT), adresse, mdp, depuis);
                    for (int i = 0; i < l.length(); i++) {
                        JSONObject e = l.optJSONObject(i);
                        if (e == null || !r.containsKey(e.optString("id"))) continue;
                        for (byte[] b : r.get(e.optString("id"))) {
                            String brut = new String(b, StandardCharsets.UTF_8);
                            String nature = classer(brut, e.optString("id"));
                            if (nature.isEmpty() || e.optLong(nature, 0) != 0) continue;
                            long quand = dateDe(brut);
                            e.put(nature, quand);
                            nouveaux.put(new JSONObject().put("jour", e.optString("jour")).put("a", e.optString("a"))
                                    .put("nature", nature).put("quand", quand));
                        }
                    }
                    if (nouveaux.length() > 0) ecrire(c, l);
                }
                ok = true;
                texte = nouveaux.length() == 0 ? "Pas de nouvel accusé de réception." : nouveaux.length() + " nouvel"
                        + (nouveaux.length() > 1 ? "les" : "") + " information" + (nouveaux.length() > 1 ? "s" : "") + " de suivi.";
            } catch (Exception e) {
                String m = e.getMessage();
                if (e instanceof java.net.UnknownHostException) m = "pas d'Internet ou serveur introuvable";
                else if (e instanceof java.net.SocketTimeoutException) m = "le serveur ne répond pas";
                texte = "Accusés de réception : échec (" + (m == null || m.isEmpty() ? e.getClass().getSimpleName() : m) + ").";
            }
        }
        if (notifier) for (int i = 0; i < nouveaux.length(); i++) notifier(c, nouveaux.optJSONObject(i));
        try {
            res.put("ok", ok).put("texte", texte).put("envois", liste(c)).put("nouveaux", nouveaux);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return res;
    }

    static String libelle(String nature) {
        switch (nature) {
            case "remise": return "remise au destinataire";
            case "lecture": return "lue par le destinataire";
            case "reponse": return "réponse reçue";
            case "echec": return "ÉCHEC de remise (non distribuée)";
            case "retard": return "remise retardée";
            default: return nature;
        }
    }

    private static void notifier(Context c, JSONObject n) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || n == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Accusés de réception", NotificationManager.IMPORTANCE_DEFAULT);
            canal.setDescription("Remise ou lecture d'une déclaration de grève (DII) envoyée.");
            nm.createNotificationChannel(canal);
        }
        Intent ouvrir = new Intent(c, MainActivity.class);
        ouvrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 9, ouvrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String jour = n.optString("jour");
        String j = jour.length() == 10 ? jour.substring(8, 10) + "/" + jour.substring(5, 7) + "/" + jour.substring(0, 4) : "";
        String nature = n.optString("nature");
        String quand = new SimpleDateFormat("dd/MM 'à' HH:mm", Locale.FRANCE).format(new Date(n.optLong("quand")));
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif_commande)
                .setContentTitle("DII" + (j.isEmpty() ? "" : " du " + j) + " : " + libelle(nature))
                .setContentText(n.optString("a") + " — le " + quand)
                .setColor("echec".equals(nature) ? 0xFFC62828 : 0xFF1B7A3A)
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            nm.notify(ID_NOTIF + (jour + nature).hashCode() % 1000, b.build());
        } catch (SecurityException e) {
            // notifications refusées : visible dans l'application
        }
    }
}
