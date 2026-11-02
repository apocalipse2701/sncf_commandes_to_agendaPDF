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
        if (avisASuivre(c)) return true;                       // avis de l'établissement attendus du service DII
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
                Map<String, String> de = new LinkedHashMap<>();
                for (int i = 0; i < l.length(); i++) {
                    JSONObject e = l.optJSONObject(i);
                    if (e != null && !e.optString("id").isEmpty() && e.optLong("le") >= limite) {
                        depuis.put(e.optString("id"), e.optLong("le"));
                        de.put(e.optString("id"), e.optString("a"));
                    }
                }
                if (!depuis.isEmpty()) {
                    Map<String, List<CourrierImap.Retour>> r = new CourrierImap().retours(p.getString("serveur", ReleveMail.SERVEUR_DEFAUT),
                            p.getInt("port", ReleveMail.PORT_DEFAUT), adresse, mdp, depuis, null);
                    for (int i = 0; i < l.length(); i++) {
                        JSONObject e = l.optJSONObject(i);
                        if (e == null || !r.containsKey(e.optString("id"))) continue;
                        JSONArray vus = e.optJSONArray("vus");
                        if (vus == null) e.put("vus", vus = new JSONArray());
                        for (CourrierImap.Retour ret : r.get(e.optString("id"))) {
                            String brut = new String(ret.brut, StandardCharsets.UTF_8);
                            long quand = dateDe(brut);
                            String nature = classer(brut, e.optString("id"));
                            if (!nature.isEmpty() && e.optLong(nature, 0) == 0) {
                                e.put(nature, quand);
                                nouveaux.put(new JSONObject().put("jour", e.optString("jour")).put("a", e.optString("a"))
                                        .put("nature", nature).put("quand", quand));
                            }
                        }
                        while (vus.length() > 200) vus.remove(0);
                    }
                    ecrire(c, l);
                }
                // avis de l'établissement : messages du service DII (adresse réglée) avec le formulaire DII joint ;
                // le « Cadre réservé à l'établissement » est vérifié par la page (pdf-lib) à l'ouverture
                List<String> exps = expediteursAvis(c, l);
                if (!exps.isEmpty()) {
                    SharedPreferences pa = c.getSharedPreferences(PREFS_AVIS, Context.MODE_PRIVATE);
                    JSONArray vusAvis = new JSONArray(pa.getString("uids", "[]"));
                    java.util.Set<Long> deja = new java.util.HashSet<>();
                    for (int i = 0; i < vusAvis.length(); i++) deja.add(vusAvis.optLong(i));
                    long[] validite = {pa.getLong("validite", 0)};
                    List<CourrierImap.Retour> msgs = new CourrierImap().avis(p.getString("serveur", ReleveMail.SERVEUR_DEFAUT),
                            p.getInt("port", ReleveMail.PORT_DEFAUT), adresse, mdp, exps,
                            System.currentTimeMillis() - 60 * 86400000L, validite, deja);
                    JSONArray avis = listeAvis(c);
                    File dossier = dossierRetours(c);
                    for (CourrierImap.Retour ret : msgs) {
                        deja.add(ret.uid);
                        long quand = dateDe(new String(ret.brut, StandardCharsets.UTF_8));
                        CourrierImap.Mime.Message m = CourrierImap.Mime.lire(ret.brut);
                        int k = 0;
                        for (CourrierImap.Mime.Partie pdf : m.pdf) {
                            String nom = ("avis-" + ret.uid + "-" + (k++) + ".pdf");
                            try (OutputStream out = new FileOutputStream(new File(dossier, nom))) {
                                out.write(pdf.octets);
                            }
                            avis.put(new JSONObject().put("fichier", nom).put("recu", quand).put("nom", pdf.nom)
                                    .put("de", CourrierImap.adresse(m.de)).put("etat", "a_verifier"));
                            nouveaux.put(new JSONObject().put("jour", "").put("a", CourrierImap.adresse(m.de))
                                    .put("nature", "avis_recu").put("quand", quand));
                        }
                    }
                    JSONArray u = new JSONArray();
                    List<Long> tri = new java.util.ArrayList<>(deja);
                    java.util.Collections.sort(tri);
                    for (long x : tri.subList(Math.max(0, tri.size() - 500), tri.size())) u.put(x);
                    pa.edit().putString("uids", u.toString()).putLong("validite", validite[0]).apply();
                    if (!msgs.isEmpty()) ecrireAvis(c, avis);
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

    static final String PREFS_AVIS = "dii-avis";

    /** Adresses du service DII (réglage « Avis DII attendus de », séparées par des virgules), plus les destinataires
     *  des DII envoyées ces 30 derniers jours. */
    static List<String> expediteursAvis(Context c, JSONArray envois) {
        List<String> l = new java.util.ArrayList<>();
        for (String x : c.getSharedPreferences(PREFS_AVIS, Context.MODE_PRIVATE).getString("de", "").split("[,;\\s]+"))
            if (x.contains("@") && !l.contains(x.trim().toLowerCase(Locale.ROOT))) l.add(x.trim().toLowerCase(Locale.ROOT));
        long limite = System.currentTimeMillis() - JOURS_SUIVI * 86400000L;
        for (int i = 0; i < envois.length(); i++) {
            JSONObject e = envois.optJSONObject(i);
            String a = e == null ? "" : e.optString("a").trim().toLowerCase(Locale.ROOT);
            if (e != null && e.optLong("le") >= limite && a.contains("@") && !l.contains(a)) l.add(a);
        }
        return l;
    }

    static String avisDe(Context c) {
        return c.getSharedPreferences(PREFS_AVIS, Context.MODE_PRIVATE).getString("de", "");
    }

    static void enregistrerAvisDe(Context c, String de) {
        c.getSharedPreferences(PREFS_AVIS, Context.MODE_PRIVATE).edit().putString("de", de == null ? "" : de.trim()).apply();
        ReleveMail.programmer(c);
    }

    static boolean avisASuivre(Context c) {
        return !avisDe(c).trim().isEmpty();
    }

    private static File fichierAvis(Context c) {
        return new File(c.getFilesDir(), "dii-avis.json");
    }

    /** Avis de l'établissement reçus : [{fichier, recu, de, etat (a_verifier, complet, incomplet, ignore), jour, manque, agent, autre, valide}]. */
    static JSONArray listeAvis(Context c) {
        File f = fichierAvis(c);
        if (!f.exists()) return new JSONArray();
        try {
            return new JSONArray(new String(BulletinsRanges.lire(f), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void ecrireAvis(Context c, JSONArray l) {
        JSONArray n = new JSONArray();
        for (int i = Math.max(0, l.length() - 200); i < l.length(); i++) n.put(l.opt(i));
        try (OutputStream out = new FileOutputStream(fichierAvis(c))) {
            out.write(n.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // pas grave
        }
    }

    /** Résultat de la vérification d'un avis par la page ({fichier, etat, jour, manque, agent, autre} ou {fichier, valide}). */
    static JSONArray noterAvisRecu(Context c, JSONObject o) {
        synchronized (VERROU) {
            JSONArray l = listeAvis(c);
            for (int i = 0; i < l.length(); i++) {
                JSONObject x = l.optJSONObject(i);
                if (x == null || !x.optString("fichier").equals(o.optString("fichier"))) continue;
                java.util.Iterator<String> k = o.keys();
                while (k.hasNext()) {
                    String cle = k.next();
                    if (!cle.equals("fichier")) try {
                        x.put(cle, o.get(cle));
                    } catch (org.json.JSONException e) {
                        // impossible
                    }
                }
            }
            ecrireAvis(c, l);
            return l;
        }
    }

    static File dossierRetours(Context c) {
        File d = new File(c.getFilesDir(), "dii-retours");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static boolean contient(JSONArray a, long v) {
        for (int i = 0; i < a.length(); i++) if (a.optLong(i, -1) == v) return true;
        return false;
    }

    /** PDF reçu de l'établissement (base64), ou "". */
    static String lireRetour(Context c, String nom) {
        if (nom == null || !nom.matches("[A-Za-z0-9._-]+")) return "";
        File f = new File(dossierRetours(c), nom);
        try {
            return f.exists() ? android.util.Base64.encodeToString(BulletinsRanges.lire(f), android.util.Base64.NO_WRAP) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Résultat de la vérification par la page d'un PDF reçu : {id, fichier, etat: complet | incomplet | ignore, manque: [..]}.
     * « complet » : l'avis de l'établissement est noté (date du message). Renvoie la liste à jour.
     */
    static JSONArray noterAvis(Context c, JSONObject o) {
        synchronized (VERROU) {
            JSONArray l = liste(c);
            for (int i = 0; i < l.length(); i++) {
                JSONObject e = l.optJSONObject(i);
                if (e == null || !e.optString("id").equals(o.optString("id"))) continue;
                try {
                    if (o.optBoolean("manuel", false)) {               // avis vérifié par l'agent lui-même
                        e.put("avis", System.currentTimeMillis()).put("avisManuel", true);
                        continue;
                    }
                    JSONArray recus = e.optJSONArray("retours");
                    if (recus == null) continue;
                    for (int k = 0; k < recus.length(); k++) {
                        JSONObject x = recus.optJSONObject(k);
                        if (x == null || !x.optString("fichier").equals(o.optString("fichier"))) continue;
                        x.put("etat", o.optString("etat", "ignore"));
                        if (o.has("manque")) x.put("manque", o.optJSONArray("manque"));
                        if ("complet".equals(o.optString("etat")) && e.optLong("avis", 0) == 0) {
                            e.put("avis", x.optLong("le", System.currentTimeMillis())).put("avisFichier", x.optString("fichier"));
                        }
                    }
                } catch (org.json.JSONException ex) {
                    // impossible
                }
            }
            ecrire(c, l);
            return l;
        }
    }

    static String libelle(String nature) {
        switch (nature) {
            case "remise": return "remise au destinataire";
            case "lecture": return "lue par le destinataire";
            case "reponse": return "réponse reçue";
            case "echec": return "ÉCHEC de remise (non distribuée)";
            case "retard": return "remise retardée";
            case "avis_recu": return "avis de l'établissement reçu : ouvrez l'application pour vérifier s'il est accepté";
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
