package fr.planning.commandes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Relevé automatique des commandes dans la boîte mail (Free par défaut : imap.free.fr:993).
 *   - Tâche Android (JobScheduler) toutes les heures, quand le téléphone a Internet, même après un redémarrage.
 *   - Les PDF trouvés (pièces jointes des messages des expéditeurs choisis) sont rangés dans
 *     files/commandes_mail/ ; une notification « Nouvelle commande » est affichée.
 *   - À l'ouverture de l'application, la page les reprend (Android.commandesMail) et les importe
 *     comme un bulletin ajouté à la main ; ils sont alors effacés (Android.commandesMailTraitees).
 * Réglages dans les préférences « mail » ; mot de passe chiffré (CoffreMail).
 */
public class ReleveMail extends JobService {

    /** Fonction présente seulement dans la version « mail » (PlanningCommandes-mail.apk, build.gradle).
     *  Version « manuel » : rien n'est relevé, et une tâche laissée par la version mail est annulée. */
    static final boolean ACTIF = BuildConfig.RELEVE_MAIL;
    static final String PREFS = "mail";
    static final int ID_TACHE = 42, ID_NOTIF = 43;
    static final String CANAL = "commandes";
    static final String ACTION_COMMANDES = "fr.planning.commandes.COMMANDES_MAIL";
    static final String SERVEUR_DEFAUT = "imap.free.fr";
    static final int PORT_DEFAUT = 993;
    private static final Object VERROU = new Object();

    @Override
    public boolean onStartJob(final JobParameters params) {
        if (!ACTIF) {
            programmer(getApplicationContext());           // version manuelle : la tâche s'annule
            return false;
        }
        new Thread(() -> {
            relever(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;                      // interrompu (plus de réseau…) : réessayer plus tard
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static File dossier(Context c) {
        File d = new File(c.getFilesDir(), "commandes_mail");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static List<String> expediteurs(SharedPreferences p) {
        List<String> l = new ArrayList<>();
        for (String e : p.getString("expediteurs", "").split("[\\n,;]+")) if (!e.trim().isEmpty()) l.add(e.trim());
        return l;
    }

    /** Réglages pour la page (sans le mot de passe). */
    static JSONObject reglages(Context c) {
        SharedPreferences p = prefs(c);
        JSONObject o = new JSONObject();
        try {
            o.put("actif", p.getBoolean("actif", false));
            o.put("adresse", p.getString("adresse", ""));
            o.put("serveur", p.getString("serveur", SERVEUR_DEFAUT));
            o.put("port", p.getInt("port", PORT_DEFAUT));
            o.put("expediteurs", new JSONArray(expediteurs(p)));
            o.put("motDePasse", !p.getString("mdp", "").isEmpty());
            o.put("etat", p.getString("etat", ""));
            o.put("enAttente", enAttente(c).length);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return o;
    }

    /** Enregistre les réglages venus de la page ; « motDePasse » vide = inchangé. */
    static void enregistrer(Context c, JSONObject o) throws Exception {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor ed = p.edit();
        String adresse = o.optString("adresse", "").trim();
        if (!adresse.equalsIgnoreCase(p.getString("adresse", ""))) {    // autre boîte : on repart de zéro
            ed.remove("uidvalidite").remove("dernier_uid");
        }
        ed.putString("adresse", adresse);
        String serveur = o.optString("serveur", "").trim();
        ed.putString("serveur", serveur.isEmpty() ? SERVEUR_DEFAUT : serveur);
        ed.putInt("port", o.optInt("port", PORT_DEFAUT) > 0 ? o.optInt("port", PORT_DEFAUT) : PORT_DEFAUT);
        JSONArray ex = o.optJSONArray("expediteurs");
        StringBuilder b = new StringBuilder();
        if (ex != null) for (int i = 0; i < ex.length(); i++) {
            String e = ex.optString(i, "").trim();
            if (!e.isEmpty()) b.append(e).append('\n');
        }
        ed.putString("expediteurs", b.toString().trim());
        ed.putBoolean("actif", ACTIF && o.optBoolean("actif", false));
        String mdp = o.optString("motDePasse", "");
        if (!mdp.isEmpty()) ed.putString("mdp", CoffreMail.chiffrer(mdp));
        if (o.optBoolean("oublier", false)) ed.remove("mdp").putBoolean("actif", false);
        ed.apply();
        programmer(c);
    }

    /** Tâche toutes les heures si le relevé est activé, sinon annulée. */
    static void programmer(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        SharedPreferences p = prefs(c);
        if (!ACTIF || !p.getBoolean("actif", false)) {
            js.cancel(ID_TACHE);
            return;
        }
        JobInfo info = new JobInfo.Builder(ID_TACHE, new ComponentName(c, ReleveMail.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L)
                .setPersisted(true)                          // garde la tâche après un redémarrage
                .build();
        js.schedule(info);
    }

    /** Relève la boîte ; renvoie {ok, texte, nouveaux}. Ne lance jamais d'exception. */
    static JSONObject relever(Context c) {
        synchronized (VERROU) {
            SharedPreferences p = prefs(c);
            JSONObject res = new JSONObject();
            String heure = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
            String texte;
            int nouveaux = 0;
            boolean ok = false;
            try {
                String adresse = p.getString("adresse", ""), mdpChiffre = p.getString("mdp", "");
                if (adresse.isEmpty() || mdpChiffre.isEmpty()) throw new IOException("adresse ou mot de passe manquant");
                List<String> exp = expediteurs(p);
                if (exp.isEmpty()) throw new IOException("aucun expéditeur indiqué");
                String mdp;
                try {
                    mdp = CoffreMail.dechiffrer(mdpChiffre);
                } catch (Exception e) {                    // copie restaurée sur un autre téléphone…
                    throw new IOException("mot de passe à ressaisir dans les réglages");
                }
                CourrierImap.Resultat r = new CourrierImap().relever(p.getString("serveur", SERVEUR_DEFAUT),
                        p.getInt("port", PORT_DEFAUT), adresse, mdp, exp,
                        p.getLong("uidvalidite", 0), p.getLong("dernier_uid", 0));
                File d = dossier(c);
                int k = 0;
                for (CourrierImap.Piece piece : r.pieces) {
                    File f = new File(d, piece.uid + "-" + (k++) + "-" + piece.nom);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(piece.octets);
                    }
                    nouveaux++;
                }
                p.edit().putLong("uidvalidite", r.uidValidite).putLong("dernier_uid", r.dernierUid).apply();
                texte = "Relevé le " + heure + " : " + (nouveaux == 0 ? "aucune nouvelle commande"
                        : nouveaux + (nouveaux > 1 ? " nouvelles commandes" : " nouvelle commande"));
                ok = true;
            } catch (Exception e) {
                String m = e.getMessage();
                if (e instanceof java.net.UnknownHostException) m = "pas d'Internet ou serveur introuvable";
                else if (e instanceof java.net.SocketTimeoutException) m = "le serveur ne répond pas";
                texte = "Relevé le " + heure + " : échec (" + (m == null || m.isEmpty() ? e.getClass().getSimpleName() : m) + ")";
            }
            p.edit().putString("etat", texte).apply();
            if (nouveaux > 0) notifier(c, nouveaux);
            try {
                res.put("ok", ok);
                res.put("texte", texte);
                res.put("nouveaux", nouveaux);
            } catch (org.json.JSONException e) {
                // impossible
            }
            return res;
        }
    }

    /** PDF reçus et pas encore importés, du plus ancien au plus récent. */
    static File[] enAttente(Context c) {
        File[] f = dossier(c).listFiles((dir, nom) -> nom.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if (f == null) return new File[0];
        Arrays.sort(f, (a, b) -> {
            long ua = uid(a.getName()), ub = uid(b.getName());
            return ua != ub ? Long.compare(ua, ub) : a.getName().compareTo(b.getName());
        });
        return f;
    }

    static long uid(String nom) {
        try {
            return Long.parseLong(nom.substring(0, nom.indexOf('-')));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Nom affiché d'un fichier en attente : sans le préfixe « uid-n- ». */
    static String nomAffiche(String fichier) {
        String[] x = fichier.split("-", 3);
        return x.length == 3 ? x[2] : fichier;
    }

    private static void notifier(Context c, int nouveaux) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Nouvelles commandes", NotificationManager.IMPORTANCE_DEFAULT);
            canal.setDescription("Un bulletin de commande est arrivé par mail.");
            nm.createNotificationChannel(canal);
        }
        Intent ouvrir = new Intent(c, MainActivity.class);
        ouvrir.setAction(ACTION_COMMANDES);
        ouvrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 7, ouvrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int total = enAttente(c).length;
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif_commande)
                .setContentTitle(total > 1 ? total + " nouvelles commandes" : "Nouvelle commande")
                .setContentText("Touchez pour mettre à jour le planning.")
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            nm.notify(ID_NOTIF, b.build());
        } catch (SecurityException e) {
            // notifications refusées : la commande sera reprise à la prochaine ouverture
        }
    }

    static void effacerNotification(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID_NOTIF);
    }
}
