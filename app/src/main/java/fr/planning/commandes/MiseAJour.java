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
 *   1. lit https://github.com/<dépôt>/releases/download/apk/version.json  ({"version": N}, écrit par la recette GitHub) ;
 *   2. si N > version installée : télécharge l'APK de la même version (PlanningCommandes.apk ou
 *      PlanningCommandes-mail.apk) dans la même release ;
 *   3. l'installe avec le PackageInstaller d'Android (Android demande toujours une confirmation).
 * Le dépôt est inscrit dans l'application au moment de la fabrication (res/values : depot_github, via build.gradle).
 * Seuls les fichiers de ce dépôt GitHub sont téléchargés ; l'APK doit être signé avec la même clé (sinon Android refuse).
 */
final class MiseAJour {

    private MiseAJour() {
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

    private static HttpURLConnection ouvrir(String adresse) throws IOException {
        URL url = new URL(adresse);
        if (!"https".equals(url.getProtocol())) throw new IOException("adresse refusée");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);           // github.com → serveur de fichiers de GitHub (https)
        c.setRequestProperty("User-Agent", "PlanningCommandes");
        int code = c.getResponseCode();
        if (code == 404) throw new IOException("aucune version publiée trouvée (dépôt privé ou release « apk » absente)");
        if (code != 200) throw new IOException("réponse " + code + " de GitHub");
        return c;
    }

    /** Numéro de la dernière version publiée. */
    static long versionPubliee(Context c) throws IOException {
        String depot = depot(c);
        if (depot.isEmpty()) throw new IOException("version fabriquée hors GitHub : mise à jour automatique indisponible");
        HttpURLConnection h = ouvrir(adresse(depot, "version.json"));
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bloc = new byte[4096];
            int n;
            while ((n = in.read(bloc)) > 0 && b.size() < 65536) b.write(bloc, 0, n);
            return new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8)).optLong("version", 0);
        } catch (org.json.JSONException e) {
            throw new IOException("fichier de version illisible");
        } finally {
            h.disconnect();
        }
    }

    interface Progression {
        void etat(String texte);
    }

    /** Télécharge l'APK dans le cache de l'application. */
    static File telecharger(Context c, Progression p) throws IOException {
        HttpURLConnection h = ouvrir(adresse(depot(c), nomApk(c)));
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
        return f;
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
