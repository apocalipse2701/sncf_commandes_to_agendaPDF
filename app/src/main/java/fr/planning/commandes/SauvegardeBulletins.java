package fr.planning.commandes;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sauvegarde des bulletins rangés (Documents/Planning Commandes/AAAA/MM - Mois/…) sur un serveur WebDAV :
 * NAS Synology (WebDAV Server), NAS QNAP (WebDAV), kDrive, Nextcloud… Les dossiers année / mois sont recréés
 * sous le dossier choisi. Seuls les bulletins pas encore envoyés (ou modifiés) partent ; « Tout renvoyer » les reprend tous.
 * Mêmes outils que le partage du planning (PartagePlanning : adresse, https obligatoire, certificat accepté, MKCOL).
 * Réglages dans les préférences « sauvegarde » ; mot de passe chiffré (CoffreMail). Liste des envois : files/sauvegarde-envoyes.txt.
 */
final class SauvegardeBulletins {

    static final String PREFS = "sauvegarde";
    static final String DOSSIER_DEFAUT = "Planning/Bulletins";
    private static final Object VERROU = new Object();

    private SauvegardeBulletins() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** {actif (automatique), service, adresse, identifiant, dossier, motDePasse, certificat, etat, envoyes, total}. */
    static JSONObject reglages(Context c) {
        SharedPreferences p = prefs(c);
        JSONObject o = new JSONObject();
        try {
            o.put("actif", p.getBoolean("actif", false));
            o.put("service", p.getString("service", ""));
            o.put("adresse", p.getString("adresse", ""));
            o.put("identifiant", p.getString("identifiant", ""));
            o.put("dossier", p.getString("dossier", DOSSIER_DEFAUT));
            o.put("motDePasse", !p.getString("mdp", "").isEmpty());
            o.put("certificat", p.getString("certificat", ""));
            o.put("etat", p.getString("etat", ""));
            o.put("envoyes", lireEnvoyes(c).size());
            o.put("total", BulletinsRanges.nombre(c));
        } catch (org.json.JSONException e) {
            // impossible
        }
        return o;
    }

    /** Enregistre ; « motDePasse » vide = inchangé ; « depuisPartage » reprend serveur, compte et certificat du partage. */
    static void enregistrer(Context c, JSONObject o) throws Exception {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor ed = p.edit();
        if (o.optBoolean("depuisPartage", false)) {
            SharedPreferences q = PartagePlanning.prefs(c);
            if (q.getString("adresse", "").isEmpty()) throw new IOException("le partage du planning n'est pas réglé");
            ed.putString("service", q.getString("service", "synology"));
            ed.putString("adresse", q.getString("adresse", ""));
            ed.putString("identifiant", q.getString("identifiant", ""));
            ed.putString("mdp", q.getString("mdp", ""));
            ed.putString("certificat", q.getString("certificat", ""));
            String d = q.getString("dossier", "").trim();
            ed.putString("dossier", d.isEmpty() ? DOSSIER_DEFAUT : d.replaceAll("/+$", "") + "/Bulletins");
            ed.apply();
            return;
        }
        String adresse = o.optString("adresse", "").trim();
        if (!adresse.isEmpty()) PartagePlanning.racine(adresse);       // vérifie le format
        if (o.optBoolean("actif", false)) {
            if (adresse.isEmpty()) throw new IOException("indiquez l'adresse du serveur (NAS, numéro du kDrive ou adresse WebDAV)");
            if (o.optString("identifiant", "").trim().isEmpty()) throw new IOException("indiquez l'identifiant");
        }
        if (!adresse.equals(p.getString("adresse", ""))) ed.remove("certificat");
        String dossier = o.optString("dossier", "").trim();
        if (!dossier.equals(p.getString("dossier", DOSSIER_DEFAUT)) || !adresse.equals(p.getString("adresse", ""))) oublierEnvois(c);
        ed.putBoolean("actif", o.optBoolean("actif", false));
        String service = o.optString("service", "synology");
        ed.putString("service", service.matches("synology|qnap|kdrive|autre") ? service : "synology");
        ed.putString("adresse", adresse);
        ed.putString("identifiant", o.optString("identifiant", "").trim());
        ed.putString("dossier", dossier.isEmpty() ? DOSSIER_DEFAUT : dossier);
        String mdp = o.optString("motDePasse", "");
        if (!mdp.isEmpty()) ed.putString("mdp", CoffreMail.chiffrer(mdp));
        if (o.optBoolean("oublier", false)) ed.remove("mdp").putBoolean("actif", false);
        ed.apply();
    }

    static void faireConfiance(Context c, String empreinte) throws IOException {
        String e = empreinte == null ? "" : empreinte.trim().toLowerCase(Locale.ROOT).replace(":", "");
        if (e.isEmpty()) {
            prefs(c).edit().remove("certificat").apply();
            return;
        }
        if (!e.matches("[0-9a-f]{64}")) throw new IOException("empreinte de certificat incorrecte");
        prefs(c).edit().putString("certificat", e).apply();
    }

    // ------------------------------------------------------------------ liste des bulletins déjà envoyés

    private static File fichierEnvoyes(Context c) {
        return new File(c.getFilesDir(), "sauvegarde-envoyes.txt");
    }

    /** chemin relatif → taille, pour chaque bulletin déjà envoyé. */
    static Map<String, Long> lireEnvoyes(Context c) {
        Map<String, Long> m = new HashMap<>();
        File f = fichierEnvoyes(c);
        if (!f.exists()) return m;
        try {
            String t = new String(BulletinsRanges.lire(f), StandardCharsets.UTF_8);
            for (String l : t.split("\n")) {
                int i = l.lastIndexOf('\t');
                if (i <= 0) continue;
                try {
                    m.put(l.substring(0, i), Long.parseLong(l.substring(i + 1).trim()));
                } catch (NumberFormatException e) {
                    // ligne abîmée
                }
            }
        } catch (IOException e) {
            // liste illisible : tout sera renvoyé
        }
        return m;
    }

    private static void ecrireEnvoyes(Context c, Map<String, Long> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Long> e : m.entrySet()) b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        try (OutputStream out = new FileOutputStream(fichierEnvoyes(c))) {
            out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // pas grave : renvoyés la prochaine fois
        }
    }

    static void oublierEnvois(Context c) {
        //noinspection ResultOfMethodCallIgnored
        fichierEnvoyes(c).delete();
    }

    // ------------------------------------------------------------------ envoi

    /**
     * Envoie les bulletins pas encore sauvegardés (tous si « tout »). Renvoie {ok, texte, envoyes, total, certificat}.
     * Ne lève jamais d'exception.
     */
    static JSONObject envoyer(Context c, boolean tout) {
        JSONObject r = new JSONObject();
        String heure = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
        boolean ok = false;
        String texte, certificat = "";
        int n = 0, total = 0;
        synchronized (VERROU) {
            SharedPreferences p = prefs(c);
            Map<String, Long> deja = tout ? new HashMap<>() : lireEnvoyes(c);
            try {
                synchronized (PartagePlanning.VERROU) {      // le certificat accepté (champs statiques) est celui de cette sauvegarde
                    PartagePlanning.epingle = p.getString("certificat", "");
                    PartagePlanning.vue = "";
                    PartagePlanning.nomRefuse = false;
                    try {
                        String mdp = PartagePlanning.lireSecret(p, "mdp");
                        if (p.getString("adresse", "").isEmpty()) throw new IOException("serveur non réglé (Réglages › Sauvegarde des bulletins)");
                        if (mdp.isEmpty()) throw new IOException("mot de passe à saisir (Réglages › Sauvegarde des bulletins)");
                        String racine = PartagePlanning.racine(p.getString("adresse", ""));
                        String dossier = p.getString("dossier", DOSSIER_DEFAUT);
                        String id = p.getString("identifiant", "");
                        File base = BulletinsRanges.racine(c);
                        List<File> fichiers = BulletinsRanges.tous(base);
                        total = fichiers.size();
                        for (File f : fichiers) {
                            String rel = BulletinsRanges.relatif(base, f);
                            Long t = deja.get(rel);
                            if (t != null && t == f.length()) continue;            // déjà envoyé, inchangé
                            int i = rel.lastIndexOf('/');
                            String sous = i < 0 ? "" : rel.substring(0, i);
                            PartagePlanning.deposer(racine, sous.isEmpty() ? dossier : dossier + "/" + sous, f.getName(), id, mdp,
                                    BulletinsRanges.lire(f));
                            deja.put(rel, f.length());
                            n++;
                            if (n % 10 == 0) ecrireEnvois(c, deja, tout);           // progression gardée en cas de coupure
                        }
                        ok = true;
                    } catch (Exception e) {
                        certificat = PartagePlanning.certificatARefuse(e);
                        throw e;
                    }
                }
                texte = n == 0 ? "Sauvegarde vérifiée le " + heure + " : tous les bulletins (" + total + ") y sont déjà."
                        : "Sauvegarde le " + heure + " : " + n + " bulletin" + (n > 1 ? "s" : "") + " envoyé" + (n > 1 ? "s" : "")
                        + " (" + total + " en tout).";
            } catch (Exception e) {
                texte = "Sauvegarde le " + heure + " : échec" + (n > 0 ? " après " + n + " envoi" + (n > 1 ? "s" : "") : "")
                        + " (" + PartagePlanning.raison(e) + ").";
            }
            ecrireEnvois(c, deja, tout);
            p.edit().putString("etat", texte).apply();
        }
        try {
            r.put("ok", ok).put("texte", texte).put("envoyes", n).put("total", total).put("certificat", certificat);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return r;
    }

    private static void ecrireEnvois(Context c, Map<String, Long> m, boolean tout) {
        if (tout) {                           // « tout renvoyer » : ce qui vient d'être envoyé, plus l'ancienne liste
            Map<String, Long> ancien = lireEnvoyes(c);
            ancien.putAll(m);
            ecrireEnvoyes(c, ancien);
        } else {
            ecrireEnvoyes(c, m);
        }
    }
}
