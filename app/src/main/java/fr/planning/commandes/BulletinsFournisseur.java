package fr.planning.commandes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Donne à la visionneuse PDF du téléphone (lecture seule, le temps de l'ouvrir) un bulletin rangé par l'application :
 *   content://fr.planning.commandes.bulletins/2026/10 - Octobre/2026-10-06 au 2026-10-12 - Commande.pdf
 * Non exporté : seule l'application à qui l'on donne l'adresse (FLAG_GRANT_READ_URI_PERMISSION) peut le lire.
 */
public class BulletinsFournisseur extends ContentProvider {

    static final String AUTORITE = "fr.planning.commandes.bulletins";

    static Uri adresse(String rel) {
        Uri.Builder b = new Uri.Builder().scheme("content").authority(AUTORITE);
        for (String morceau : rel.split("/")) if (!morceau.isEmpty()) b.appendPath(morceau);
        return b.build();
    }

    private File fichier(Uri u) throws FileNotFoundException {
        StringBuilder rel = new StringBuilder();
        for (String morceau : u.getPathSegments()) rel.append(rel.length() > 0 ? "/" : "").append(morceau);
        File f = getContext() == null ? null : BulletinsRanges.fichier(getContext(), rel.toString());
        if (f == null) throw new FileNotFoundException(rel.toString());
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) throw new FileNotFoundException("lecture seule");
        return ParcelFileDescriptor.open(fichier(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri u, String[] colonnes, String selection, String[] args, String tri) {
        File f;
        try {
            f = fichier(u);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (colonnes == null) colonnes = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(colonnes, 1);
        Object[] ligne = new Object[colonnes.length];
        for (int i = 0; i < colonnes.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(colonnes[i])) ligne[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(colonnes[i])) ligne[i] = f.length();
        }
        c.addRow(ligne);
        return c;
    }

    @Override
    public String getType(Uri u) {
        return "application/pdf";
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        throw new UnsupportedOperationException("lecture seule");
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        throw new UnsupportedOperationException("lecture seule");
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException("lecture seule");
    }
}
