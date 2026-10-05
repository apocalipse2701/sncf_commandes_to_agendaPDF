package fr.planning.commandes;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Mot de passe de la boîte mail, chiffré par une clé du coffre de clés d'Android (AndroidKeyStore) :
 * la clé ne quitte jamais le téléphone et n'est pas lisible, même par l'application.
 * Seul le texte chiffré est gardé dans les préférences ; il ne part dans aucune copie de sauvegarde
 * (allowBackup ne concerne pas les clés du coffre : une copie restaurée ailleurs ne pourrait pas le lire).
 */
final class CoffreMail {

    private static final String ALIAS = "planning_mail";
    private static final String MAGASIN = "AndroidKeyStore";

    private CoffreMail() {
    }

    private static SecretKey cle() throws Exception {
        KeyStore ks = KeyStore.getInstance(MAGASIN);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, MAGASIN);
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return g.generateKey();
    }

    static String chiffrer(String clair) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, cle());
        byte[] iv = c.getIV(), ct = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
        byte[] tout = new byte[iv.length + ct.length + 1];
        tout[0] = (byte) iv.length;
        System.arraycopy(iv, 0, tout, 1, iv.length);
        System.arraycopy(ct, 0, tout, 1 + iv.length, ct.length);
        return Base64.encodeToString(tout, Base64.NO_WRAP);
    }

    static String dechiffrer(String chiffre) throws Exception {
        byte[] tout = Base64.decode(chiffre, Base64.DEFAULT);
        int n = tout[0];
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, cle(), new GCMParameterSpec(128, tout, 1, n));
        return new String(c.doFinal(tout, 1 + n, tout.length - 1 - n), StandardCharsets.UTF_8);
    }
}
