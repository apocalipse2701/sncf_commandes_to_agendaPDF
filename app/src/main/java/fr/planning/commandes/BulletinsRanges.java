package fr.planning.commandes;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Bulletins rangés sur le téléphone, comme dans le dossier « commande » du PC :
 *   Documents/Planning Commandes/2026/10 - Octobre/2026-10-06 au 2026-10-12 - Commande.pdf
 * (nom et dossier calculés par la page : app.js, rangement()). Deux copies :
 *   - privée, files/bulletins/… : source du ZIP, toujours lisible par l'application ;
 *   - visible, Documents/Planning Commandes/… : à ouvrir dans l'application Fichiers (MediaStore, sans autorisation
 *     depuis Android 10 ; avant, autorisation « stockage » demandée une fois).
 * Même bulletin déjà rangé (même contenu) : rien n'est ajouté. Même nom mais autre contenu : « (édition du …) »
 * puis « (copie 2) »… comme ranger_commandes (planning_pdf.py).
 */
final class BulletinsRanges {

    static final String DOSSIER_VISIBLE = "Planning Commandes";

    /** Raison du dernier échec de la copie visible (null : la dernière copie a réussi). */
    static volatile String erreurVisible = null;

    private BulletinsRanges() {
    }

    static File racine(Context c) {
        File d = new File(c.getFilesDir(), "bulletins");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Chemin affiché à l'utilisateur. */
    static String cheminVisible() {
        return "Documents/" + DOSSIER_VISIBLE;
    }

    /** Nettoie un morceau de chemin (pas de « .. », pas de caractères interdits). */
    static String propre(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").replace("..", "_").trim();
        return n.length() > 150 ? n.substring(0, 150) : n;
    }

    /**
     * Range un bulletin. @param dossier « 2026/10 - Octobre » ou "" (période non lue : à la racine) ;
     * @param nom sans « .pdf » ; @param edition « édition du 2026-10-05 14h30 » ou "".
     * @return chemin relatif rangé (« 2026/10 - Octobre/… .pdf »), ou "" si le même bulletin y était déjà.
     */
    static String ranger(Context c, String dossier, String nom, String edition, byte[] pdf) throws IOException {
        StringBuilder rel = new StringBuilder();
        for (String morceau : dossier.split("/")) {
            String p = propre(morceau);
            if (!p.isEmpty()) rel.append(p).append('/');
        }
        File cible = new File(racine(c), rel.toString());
        if (!cible.exists() && !cible.mkdirs()) throw new IOException("dossier impossible à créer");
        String base = propre(nom.replaceAll("(?i)\\.pdf$", ""));
        if (base.isEmpty()) base = "commande";
        List<String> candidats = new ArrayList<>();
        candidats.add(base + ".pdf");
        if (!edition.isEmpty()) candidats.add(base + " (" + propre(edition) + ").pdf");
        for (int k = 2; k < 50; k++) candidats.add(base + " (" + (edition.isEmpty() ? "copie" : propre(edition)) + " " + k + ").pdf");
        // déjà rangé ailleurs (même contenu) : rien à faire
        String md5 = md5(pdf);
        for (File f : tous(racine(c))) if (f.length() == pdf.length && md5(lire(f)).equals(md5)) return "";
        for (String nomFichier : candidats) {
            File f = new File(cible, nomFichier);
            if (f.exists()) continue;
            try (OutputStream o = new FileOutputStream(f)) {
                o.write(pdf);
            }
            String chemin = rel + nomFichier;
            try {
                copieVisible(c, rel.toString(), nomFichier, pdf);
                erreurVisible = null;
            } catch (Exception e) {
                // copie visible impossible (stockage plein, autorisation refusée…) : la copie privée suffit au ZIP
                // et à la liste de l'onglet Commandes ; la raison est affichée
                erreurVisible = raison(e);
            }
            return chemin;
        }
        throw new IOException("trop de bulletins du même nom");
    }

    static String raison(Exception e) {
        return e.getMessage() == null || e.getMessage().isEmpty() ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** Dossier Documents/Planning Commandes (chemin de fichier : sert à vérifier qu'il existe, et en secours). */
    @SuppressWarnings("deprecation")
    static File dossierVisible() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DOSSIER_VISIBLE);
    }

    /** Le dossier Documents/Planning Commandes existe-t-il sur le téléphone ? */
    static boolean visibleExiste() {
        try {
            return dossierVisible().isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Copie dans Documents/Planning Commandes/<rel> (dossier visible dans l'application Fichiers).
     * Android 10 et plus : par le MediaStore ; s'il refuse, écriture directe du fichier (permise à l'application
     * dans Documents depuis Android 11). Android 9 et moins : fichier (autorisation « stockage »).
     */
    static void copieVisible(Context c, String rel, String nomFichier, byte[] pdf) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                copieMediaStore(c, rel, nomFichier, pdf);
                return;
            } catch (Exception e) {
                if (Build.VERSION.SDK_INT < 30) throw e instanceof IOException ? (IOException) e : new IOException(raison(e));
                try {
                    copieFichier(rel, nomFichier, pdf);
                    return;
                } catch (Exception e2) {
                    throw new IOException(raison(e) + " / " + raison(e2));
                }
            }
        }
        copieFichier(rel, nomFichier, pdf);
    }

    static void copieMediaStore(Context c, String rel, String nomFichier, byte[] pdf) throws IOException {
        ContentResolver r = c.getContentResolver();
        String chemin = Environment.DIRECTORY_DOCUMENTS + "/" + DOSSIER_VISIBLE + "/" + rel;
        Uri collection = MediaStore.Files.getContentUri("external");
        try (Cursor k = r.query(collection, new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[]{chemin, nomFichier}, null)) {
            if (k != null && k.moveToFirst()) return;                     // déjà là
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, nomFichier);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, chemin);
        Uri u = r.insert(collection, v);
        if (u == null) throw new IOException("Documents refusé par Android");
        try (OutputStream o = r.openOutputStream(u)) {
            if (o == null) throw new IOException("Documents inaccessible");
            o.write(pdf);
        }
    }

    static void copieFichier(String rel, String nomFichier, byte[] pdf) throws IOException {
        File d = new File(dossierVisible(), rel);
        if (!d.exists() && !d.mkdirs()) throw new IOException("Documents inaccessible");
        File f = new File(d, nomFichier);
        if (f.exists()) return;
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(pdf);
        }
    }

    /** Recopie dans Documents les bulletins rangés qui n'y sont pas encore (ex. autorisation accordée après coup). */
    static int synchroniserVisible(Context c) {
        int n = 0;
        File r = racine(c);
        erreurVisible = null;
        for (File f : tous(r)) {
            String rel = relatif(r, f.getParentFile());
            try {
                copieVisible(c, rel, f.getName(), lire(f));
                n++;
            } catch (Exception e) {
                erreurVisible = raison(e);                                // retenté à la prochaine ouverture
            }
        }
        return n;
    }

    /** Chemin de f relatif à r, avec « / » (« 2026/10 - Octobre/… »). */
    static String relatif(File r, File f) {
        String a = r.getAbsolutePath(), b = f.getAbsolutePath();
        if (b.equals(a)) return "";
        String rel = b.startsWith(a + File.separator) ? b.substring(a.length() + 1) : f.getName();
        rel = rel.replace(File.separatorChar, '/');
        return f.isDirectory() ? rel + "/" : rel;
    }

    /** Bulletin rangé désigné par son chemin relatif, ou null s'il sort du dossier / n'existe pas. */
    static File fichier(Context c, String rel) {
        try {
            File r = racine(c).getCanonicalFile();
            File f = new File(r, rel).getCanonicalFile();
            if (!f.getPath().startsWith(r.getPath() + File.separator) || !f.isFile()) return null;
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    /** Tous les bulletins rangés (copie privée), triés par chemin. */
    static List<File> tous(File d) {
        List<File> l = new ArrayList<>();
        File[] f = d.listFiles();
        if (f == null) return l;
        for (File x : f) {
            if (x.isDirectory()) l.addAll(tous(x));
            else if (x.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) l.add(x);
        }
        Collections.sort(l, (a, b) -> a.getPath().compareTo(b.getPath()));
        return l;
    }

    static int nombre(Context c) {
        return tous(racine(c)).size();
    }

    /** ZIP de tous les bulletins rangés, avec leurs dossiers (« Planning Commandes/2026/10 - Octobre/… »). */
    static byte[] zip(Context c) throws IOException {
        File r = racine(c);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (File f : tous(r)) {
                String rel = relatif(r, f);
                ZipEntry e = new ZipEntry(DOSSIER_VISIBLE + "/" + rel);
                e.setTime(f.lastModified());
                z.putNextEntry(e);
                z.write(lire(f));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }

    static byte[] lire(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bloc = new byte[65536];
            int n;
            while ((n = in.read(bloc)) > 0) b.write(bloc, 0, n);
            return b.toByteArray();
        }
    }

    static String md5(byte[] o) {
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest(o);
            StringBuilder s = new StringBuilder();
            for (byte x : h) s.append(String.format("%02x", x));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(java.util.Arrays.hashCode(o));
        }
    }
}
