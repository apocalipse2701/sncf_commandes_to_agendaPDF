package fr.planning.commandes;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Mise à jour de l'application sans passer par le navigateur :
 *   1. lit la fiche de la dernière version dans le dépôt public des mises à jour (« planning-commandes-maj ») :
 *        https://github.com/<dépôt>/releases/latest/download/maj.json
 *        {"version": N, "nom": "2.8.0", "tag": "v2.8.0-28", "notes": "…", "fichiers": {"PlanningCommandes.apk": {"sha256": …, "taille": …}, …}}
 *      (ancien format, dépôt du code : releases/download/apk/version.json = {"version": N}, encore lu si maj.json n'existe pas) ;
 *   2. si N > version installée : télécharge l'APK de la même variante (PlanningCommandes.apk ou PlanningCommandes-mail.apk)
 *      de cette release, et vérifie son empreinte SHA-256 ;
 *   3. l'installe avec le PackageInstaller d'Android (Android demande toujours une confirmation).
 * Le dépôt est inscrit dans l'application au moment de la fabrication (res/values : depot_github, via build.gradle).
 * Seuls les fichiers de ce dépôt GitHub sont téléchargés ; l'APK doit être signé avec la même clé (sinon Android refuse).
 */
final class MiseAJour {

    private MiseAJour() {
    }

    /** Dernière version publiée (lue par versionPubliee, utilisée par telecharger). */
    static final class Publication {
        long version;
        String nom = "", tag = "", notes = "", sha256 = "";
        boolean fiche;                       // true : maj.json (dépôt des mises à jour) ; false : ancien version.json
    }

    static volatile Publication derniere = null;

    /** Fichier absent (404) : sert à passer de maj.json à l'ancien version.json. */
    static final class Absent extends IOException {
        Absent(String m) {
            super(m);
        }
    }

    static String depot(Context c) {
        try {
            return c.getString(R.string.depot_github).trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** APK de la même version : PlanningCommandes.apk (manuelle) ou PlanningCommandes-mail.apk (commandes par mail). */
    static String nomApk(Context c) {
        try {
            return c.getString(R.string.nom_apk);
        } catch (RuntimeException e) {
            return "PlanningCommandes.apk";
        }
    }

    @SuppressWarnings("deprecation")
    static long versionInstallee(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static String adresse(String depot, String fichier) {
        return "https://github.com/" + depot + "/releases/download/apk/" + fichier;
    }

    private static String adresseFiche(String depot) {
        return "https://github.com/" + depot + "/releases/latest/download/maj.json";
    }

    private static String adresseFichier(String depot, String tag, String fichier) {
        return "https://github.com/" + depot + "/releases/download/" + tag + "/" + fichier;
    }

    /** Lit la fiche maj.json (texte JSON) ; null si elle est illisible. */
    static Publication lireFiche(String json, String nomApk) {
        try {
            JSONObject o = new JSONObject(json);
            Publication p = new Publication();
            p.fiche = true;
            p.version = o.optLong("version", 0);
            p.nom = o.optString("nom", "");
            p.tag = o.optString("tag", "");
            p.notes = o.optString("notes", "");
            JSONObject f = o.optJSONObject("fichiers");
            JSONObject x = f == null ? null : f.optJSONObject(nomApk);
            if (x != null) p.sha256 = x.optString("sha256", "").toLowerCase(java.util.Locale.ROOT);
            if (p.version <= 0 || !p.tag.matches("[\\w.-]+")) return null;
            return p;
        } catch (org.json.JSONException | RuntimeException e) {
            return null;
        }
    }

    private static HttpURLConnection ouvrir(String adresse) throws IOException {
        URL url = new URL(adresse);
        if (!"https".equals(url.getProtocol())) throw new IOException("adresse refusée");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);           // github.com → serveur de fichiers de GitHub (https)
        c.setRequestProperty("User-Agent", "PlanningCommandes");
        int code = c.getResponseCode();
        if (code == 404) throw new Absent("aucune version publiée trouvée");
        if (code != 200) throw new IOException("réponse " + code + " de GitHub");
        return c;
    }

    private static String lireTexte(HttpURLConnection h) throws IOException {
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bloc = new byte[4096];
            int n;
            while ((n = in.read(bloc)) > 0 && b.size() < 262144) b.write(bloc, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            h.disconnect();
        }
    }

    /** Dernière version publiée (fiche maj.json, sinon ancien version.json). */
    static Publication publication(Context c) throws IOException {
        String depot = depot(c);
        if (depot.isEmpty() || !depot.matches("[\\w.-]+/[\\w.-]+"))
            throw new IOException("version fabriquée hors GitHub : mise à jour automatique indisponible");
        Publication p;
        try {
            p = lireFiche(lireTexte(ouvrir(adresseFiche(depot))), nomApk(c));
            if (p == null) throw new IOException("fiche de version illisible");
        } catch (Absent e) {
            // dépôt sans fiche maj.json : ancien format (release « apk » du dépôt du code)
            try {
                p = new Publication();
                p.version = new JSONObject(lireTexte(ouvrir(adresse(depot, "version.json")))).optLong("version", 0);
            } catch (Absent e2) {
                throw new IOException("aucune version publiée trouvée");
            } catch (org.json.JSONException e2) {
                throw new IOException("fichier de version illisible");
            }
        }
        derniere = p;
        return p;
    }

    /** Numéro de la dernière version publiée. */
    static long versionPubliee(Context c) throws IOException {
        return publication(c).version;
    }

    interface Progression {
        void etat(String texte);
    }

    /** Télécharge l'APK dans le cache de l'application. */
    static File telecharger(Context c, Progression p) throws IOException {
        Publication pub = derniere != null ? derniere : publication(c);
        HttpURLConnection h = ouvrir(pub.fiche ? adresseFichier(depot(c), pub.tag, nomApk(c)) : adresse(depot(c), nomApk(c)));
        File f = new File(c.getCacheDir(), "mise-a-jour.apk");
        long total = h.getContentLengthLong(), lu = 0;
        int dernier = -1;
        try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] bloc = new byte[65536];
            int n;
            while ((n = in.read(bloc)) > 0) {
                out.write(bloc, 0, n);
                lu += n;
                int pct = total > 0 ? (int) (lu * 100 / total) : -1;
                if (pct != dernier && pct % 10 == 0) {
                    dernier = pct;
                    p.etat("Téléchargement… " + pct + " %");
                }
            }
        } finally {
            h.disconnect();
        }
        // un APK est une archive zip : « PK » au début
        try (InputStream in = new FileInputStream(f)) {
            if (in.read() != 'P' || in.read() != 'K') throw new IOException("le fichier téléchargé n'est pas une application");
        }
        // empreinte annoncée dans la fiche : fichier complet et intact
        if (!pub.sha256.isEmpty() && !pub.sha256.equals(sha256(f))) {
            f.delete();
            throw new IOException("le fichier téléchargé est abîmé (empreinte différente) : réessayez");
        }
        return f;
    }

    static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bloc = new byte[65536];
            int n;
            while ((n = in.read(bloc)) > 0) md.update(bloc, 0, n);
            StringBuilder s = new StringBuilder();
            for (byte x : md.digest()) s.append(String.format("%02x", x));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    /** Lance l'installation ; la réponse d'Android arrive dans RecepteurInstallation. */
    static void installer(Context c, File apk) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("planning", 0, apk.length())) {
                byte[] bloc = new byte[65536];
                int n;
                while ((n = in.read(bloc)) > 0) out.write(bloc, 0, n);
                s.fsync(out);
            }
            Intent retour = new Intent(c, RecepteurInstallation.class);
            int drapeaux = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);   // Android complète l'intention
            PendingIntent pe = PendingIntent.getBroadcast(c, id, retour, drapeaux);
            s.commit(pe.getIntentSender());
        }
    }
}
