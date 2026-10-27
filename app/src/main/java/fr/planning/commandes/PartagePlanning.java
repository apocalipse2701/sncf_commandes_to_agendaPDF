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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Partage du planning avec un proche (conjoint…), par un dossier WebDAV — Infomaniak kDrive par défaut :
 *   - l'application principale (version manuelle ou « mail ») dépose, après chaque changement, le planning
 *     CHIFFRÉ dans <dossier>/planning-partage.bin (PUT) ;
 *   - l'application « lecture » (PlanningCommandes-lecture.apk) le relit à l'ouverture et toutes les heures
 *     (tâche Android), met à jour ses widgets et prévient d'un « Contrairement ».
 * Chiffrement : AES-256-GCM, clé tirée du CODE DE PARTAGE (PBKDF2, 150 000 tours, sel aléatoire) :
 * sans le code, le fichier est illisible, même pour qui a accès au kDrive.
 * Contenu (avant chiffrement, gzip) : {"version":1, "envoye":"…", "planning":{…}, "codesTxt":"…", "theme":"…", "widget":"…"}.
 * Réglages dans les préférences « partage » ; mot de passe et code chiffrés sur le téléphone (CoffreMail).
 */
public class PartagePlanning extends JobService {

    static final String PREFS = "partage";
    static final String FICHIER = "planning-partage.bin";
    static final String DOSSIER_DEFAUT = "Private/Planning partagé";   // « Private » = « Mes fichiers » du kDrive
    static final int ID_TACHE = 52, ID_NOTIF = 53;
    static final String CANAL = "partage";
    private static final byte[] MAGIQUE = {'P', 'C', 'P', '1'};
    private static final int TOURS = 150000;
    private static final int DELAI_MS = 30000;
    private static final Object VERROU = new Object();

    // ------------------------------------------------------------------ tâche (application « lecture »)

    @Override
    public boolean onStartJob(final JobParameters params) {
        if (!BuildConfig.LECTURE) {
            programmer(getApplicationContext());
            return false;
        }
        new Thread(() -> {
            recevoir(getApplicationContext(), true);
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    /** Lecture : relève le partage toutes les heures (avec Internet), même téléphone redémarré. */
    static void programmer(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!BuildConfig.LECTURE || !prefs(c).getBoolean("actif", false)) {
            js.cancel(ID_TACHE);
            return;
        }
        js.schedule(new JobInfo.Builder(ID_TACHE, new ComponentName(c, PartagePlanning.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    // ------------------------------------------------------------------ réglages

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Réglages pour la page : {actif, adresse, identifiant, dossier, motDePasse (bool), code, etat, recu}. */
    static JSONObject reglages(Context c) {
        SharedPreferences p = prefs(c);
        JSONObject o = new JSONObject();
        try {
            o.put("actif", p.getBoolean("actif", false));
            o.put("adresse", p.getString("adresse", ""));
            o.put("identifiant", p.getString("identifiant", ""));
            o.put("dossier", p.getString("dossier", DOSSIER_DEFAUT));
            o.put("motDePasse", !p.getString("mdp", "").isEmpty());
            o.put("code", lireSecret(p, "code"));
            o.put("etat", p.getString("etat", ""));
            o.put("recu", p.getString("recu", ""));
        } catch (org.json.JSONException e) {
            // impossible
        }
        return o;
    }

    /** Enregistre ; « motDePasse » vide = inchangé. Lève une exception avec un message clair. */
    static void enregistrer(Context c, JSONObject o) throws Exception {
        SharedPreferences.Editor ed = prefs(c).edit();
        String adresse = o.optString("adresse", "").trim();
        if (!adresse.isEmpty()) racine(adresse);                       // vérifie le format
        String code = o.optString("code", "").trim();
        if (o.optBoolean("actif", false)) {
            if (adresse.isEmpty()) throw new IOException("indiquez le numéro du kDrive (ou l'adresse WebDAV)");
            if (o.optString("identifiant", "").trim().isEmpty()) throw new IOException("indiquez l'identifiant (adresse mail du compte)");
            if (normaliser(code).length() < 8) throw new IOException("le code de partage doit avoir au moins 8 lettres ou chiffres");
        }
        ed.putBoolean("actif", o.optBoolean("actif", false));
        ed.putString("adresse", adresse);
        ed.putString("identifiant", o.optString("identifiant", "").trim());
        String dossier = o.optString("dossier", "").trim();
        ed.putString("dossier", dossier.isEmpty() ? DOSSIER_DEFAUT : dossier);
        String mdp = o.optString("motDePasse", "");
        if (!mdp.isEmpty()) ed.putString("mdp", CoffreMail.chiffrer(mdp));
        if (!code.isEmpty()) ed.putString("code", CoffreMail.chiffrer(code));
        if (o.optBoolean("oublier", false)) ed.remove("mdp").putBoolean("actif", false);
        if (o.optBoolean("nouveau", false)) ed.remove("etag").remove("recu");
        ed.apply();
        programmer(c);
    }

    private static String lireSecret(SharedPreferences p, String cle) {
        String s = p.getString(cle, "");
        if (s.isEmpty()) return "";
        try {
            return CoffreMail.dechiffrer(s);
        } catch (Exception e) {
            return "";
        }
    }

    /** Code de partage proposé : 12 caractères faciles à recopier (sans 0/O, 1/l/I). */
    static String nouveauCode() {
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ chiffrement

    static byte[] chiffrer(byte[] clair, String code) throws Exception {
        SecureRandom r = new SecureRandom();
        byte[] sel = new byte[16], iv = new byte[12];
        r.nextBytes(sel);
        r.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, cle(code, sel), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(gzip(clair));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(MAGIQUE);
        b.write(sel);
        b.write(iv);
        b.write(ct);
        return b.toByteArray();
    }

    static byte[] dechiffrer(byte[] donnees, String code) throws IOException {
        if (donnees.length < 4 + 16 + 12 + 16) throw new IOException("fichier de partage abîmé");
        for (int i = 0; i < 4; i++) if (donnees[i] != MAGIQUE[i]) throw new IOException("ce n'est pas un planning partagé");
        byte[] sel = java.util.Arrays.copyOfRange(donnees, 4, 20), iv = java.util.Arrays.copyOfRange(donnees, 20, 32);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle(code, sel), new GCMParameterSpec(128, iv));
            return degzip(c.doFinal(donnees, 32, donnees.length - 32));
        } catch (javax.crypto.AEADBadTagException e) {
            throw new IOException("code de partage incorrect");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("déchiffrement impossible (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static SecretKeySpec cle(String code, byte[] sel) throws Exception {
        // PBKDF2 avec HMAC-SHA1 : disponible dès Android 7 (minSdk 24) ; la longueur du code et les tours font la solidité
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] k = f.generateSecret(new PBEKeySpec(normaliser(code).toCharArray(),
                sel, TOURS, 256)).getEncoded();
        return new SecretKeySpec(k, "AES");
    }

    /** Code de partage comparé sans tenir compte des majuscules, espaces et tirets : « abcd efgh-jklm » = « ABCD-EFGH-JKLM ». */
    static String normaliser(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static byte[] gzip(byte[] o) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(b)) {
            z.write(o);
        }
        return b.toByteArray();
    }

    private static byte[] degzip(byte[] o) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(o))) {
            return lireTout(in, 20 * 1024 * 1024);
        }
    }

    private static byte[] lireTout(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] bloc = new byte[65536];
        int n;
        while ((n = in.read(bloc)) > 0) {
            b.write(bloc, 0, n);
            if (b.size() > max) throw new IOException("fichier trop gros");
        }
        return b.toByteArray();
    }

    // ------------------------------------------------------------------ WebDAV (kDrive)

    /** « 123456 » (numéro du kDrive) → https://123456.connect.kdrive.infomaniak.com/ ; ou adresse WebDAV complète (https). */
    static String racine(String adresse) throws IOException {
        String a = adresse.trim();
        if (a.matches("\\d{3,12}")) return "https://" + a + ".connect.kdrive.infomaniak.com/";
        if (!a.toLowerCase(Locale.ROOT).startsWith("https://")) throw new IOException("adresse WebDAV incorrecte : numéro du kDrive ou adresse en https://");
        return a.endsWith("/") ? a : a + "/";
    }

    private static String segments(String dossier) throws IOException {
        StringBuilder b = new StringBuilder();
        for (String s : dossier.split("/")) {
            s = s.trim();
            if (s.isEmpty() || s.equals(".") || s.equals("..")) continue;
            b.append(URLEncoder.encode(s, "UTF-8").replace("+", "%20")).append('/');
        }
        return b.toString();
    }

    /** Essais seulement : connexions TLS imposées (certificat de test). null = normal. */
    static javax.net.ssl.SSLSocketFactory tlsEssai = null;
    static javax.net.ssl.HostnameVerifier verifEssai = null;

    private static String auth(String identifiant, String mdp) {
        return "Basic " + android.util.Base64.encodeToString(
                (identifiant + ":" + mdp).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }

    private static HttpURLConnection connexion(String url, String methode, String identifiant, String mdp) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("adresse refusée (https obligatoire)");
        HttpURLConnection h = (HttpURLConnection) u.openConnection();
        if (h instanceof javax.net.ssl.HttpsURLConnection) {
            if (tlsEssai != null) ((javax.net.ssl.HttpsURLConnection) h).setSSLSocketFactory(tlsEssai);
            if (verifEssai != null) ((javax.net.ssl.HttpsURLConnection) h).setHostnameVerifier(verifEssai);
        }
        h.setConnectTimeout(DELAI_MS);
        h.setReadTimeout(DELAI_MS);
        h.setInstanceFollowRedirects(false);
        h.setUseCaches(false);
        h.setRequestMethod(methode);
        h.setRequestProperty("User-Agent", "PlanningCommandes");
        h.setRequestProperty("Authorization", auth(identifiant, mdp));
        return h;
    }

    /**
     * MKCOL (création de dossier) : HttpURLConnection ne connaît pas cette méthode WebDAV, d'où une requête HTTP/1.1
     * écrite à la main sur une connexion TLS (nom du serveur vérifié comme pour le reste). Renvoie le code HTTP.
     */
    static int mkcol(String url, String identifiant, String mdp) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("adresse refusée (https obligatoire)");
        String hote = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 443;
        javax.net.ssl.SSLSocketFactory f = tlsEssai != null ? tlsEssai : (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        java.net.Socket brut = new java.net.Socket();
        brut.connect(new java.net.InetSocketAddress(hote, port), DELAI_MS);
        try (javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket) f.createSocket(brut, hote, port, true)) {
            s.setSoTimeout(DELAI_MS);
            // nom du serveur vérifié pendant la poignée de main (comme HttpsURLConnection)…
            javax.net.ssl.SSLParameters prm = s.getSSLParameters();
            prm.setEndpointIdentificationAlgorithm("HTTPS");
            s.setSSLParameters(prm);
            s.startHandshake();
            // … et de nouveau avec le vérificateur d'Android (sur un ordinateur, celui par défaut refuse tout : déjà fait ci-dessus)
            javax.net.ssl.HostnameVerifier v = verifEssai != null ? verifEssai
                    : "Dalvik".equals(System.getProperty("java.vm.name")) ? javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier() : null;
            if (v != null && !v.verify(hote, s.getSession())) throw new IOException("certificat du serveur WebDAV non reconnu");
            String chemin = u.getPath().isEmpty() ? "/" : u.getPath();
            String requete = "MKCOL " + chemin + " HTTP/1.1\r\n"
                    + "Host: " + hote + (u.getPort() > 0 ? ":" + port : "") + "\r\n"
                    + "User-Agent: PlanningCommandes\r\n"
                    + "Authorization: " + auth(identifiant, mdp) + "\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = s.getOutputStream();
            out.write(requete.getBytes(StandardCharsets.UTF_8));
            out.flush();
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
            String l = in.readLine();
            if (l == null || !l.startsWith("HTTP/")) throw new IOException("réponse illisible du serveur WebDAV");
            String[] m = l.split(" ");
            try {
                return Integer.parseInt(m[1]);
            } catch (RuntimeException e) {
                throw new IOException("réponse illisible du serveur WebDAV");
            }
        } finally {
            try { brut.close(); } catch (IOException e) { /* rien */ }
        }
    }

    private static String message(int code, String quoi) {
        if (code == 401) return "identifiant ou mot de passe refusé par le kDrive (utilisez un mot de passe d'application)";
        if (code == 403) return "accès refusé par le kDrive pour " + quoi + " (dossier en lecture seule ?)";
        if (code == 404) return "dossier introuvable sur le kDrive";
        if (code == 507) return "kDrive plein";
        return "réponse " + code + " du kDrive (" + quoi + ")";
    }

    static void deposer(String racine, String dossier, String identifiant, String mdp, byte[] donnees) throws IOException {
        String base = racine + segments(dossier);
        int code = put(base + FICHIER, identifiant, mdp, donnees);
        if (code == 404 || code == 409) {                        // dossier absent : on le crée, niveau par niveau
            String chemin = racine;
            for (String s : segments(dossier).split("/")) {
                if (s.isEmpty()) continue;
                chemin += s + "/";
                int c = mkcol(chemin, identifiant, mdp);
                if (c == 403 || c == 409) throw new IOException("impossible de créer le dossier « " + dossier + " » sur le kDrive : créez-le dans kDrive"
                        + " (par exemple dans Mes fichiers, qui s'appelle « Private » en WebDAV) ou corrigez le chemin");
                if (c != 201 && c != 405 && c != 200 && c != 301) throw new IOException(message(c, "création du dossier"));
            }
            code = put(base + FICHIER, identifiant, mdp, donnees);
        }
        if (code != 200 && code != 201 && code != 204) throw new IOException(message(code, "dépôt du planning"));
    }

    private static int put(String url, String identifiant, String mdp, byte[] donnees) throws IOException {
        HttpURLConnection h = connexion(url, "PUT", identifiant, mdp);
        h.setDoOutput(true);
        h.setFixedLengthStreamingMode(donnees.length);
        h.setRequestProperty("Content-Type", "application/octet-stream");
        try (OutputStream out = h.getOutputStream()) {
            out.write(donnees);
        }
        int c = h.getResponseCode();
        h.disconnect();
        return c;
    }

    /** Fichier partagé : null si inchangé depuis etag (304). */
    static byte[] lire(String racine, String dossier, String identifiant, String mdp, String[] etag) throws IOException {
        HttpURLConnection h = connexion(racine + segments(dossier) + FICHIER, "GET", identifiant, mdp);
        if (etag[0] != null && !etag[0].isEmpty()) h.setRequestProperty("If-None-Match", etag[0]);
        int c = h.getResponseCode();
        try {
            if (c == 304) return null;
            if (c == 404) throw new IOException("aucun planning partagé trouvé (il n'a pas encore été envoyé ?)");
            if (c != 200) throw new IOException(message(c, "lecture du planning"));
            etag[0] = h.getHeaderField("ETag");
            try (InputStream in = h.getInputStream()) {
                return lireTout(in, 30 * 1024 * 1024);
            }
        } finally {
            h.disconnect();
        }
    }

    // ------------------------------------------------------------------ envoi (application principale)

    /** Chiffre et dépose le paquet préparé par la page. Renvoie {ok, texte}. Ne lève jamais d'exception. */
    static JSONObject envoyer(Context c, String paquet) {
        JSONObject r = new JSONObject();
        String heure = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
        boolean ok = false;
        String texte;
        synchronized (VERROU) {
            try {
                SharedPreferences p = prefs(c);
                if (!p.getBoolean("actif", false)) throw new IOException("partage désactivé");
                String mdp = lireSecret(p, "mdp"), code = lireSecret(p, "code");
                if (mdp.isEmpty() || code.isEmpty()) throw new IOException("mot de passe ou code de partage à ressaisir");
                byte[] donnees = chiffrer(paquet.getBytes(StandardCharsets.UTF_8), code);
                deposer(racine(p.getString("adresse", "")), p.getString("dossier", DOSSIER_DEFAUT),
                        p.getString("identifiant", ""), mdp, donnees);
                ok = true;
                texte = "Planning partagé envoyé le " + heure + ".";
            } catch (Exception e) {
                texte = "Partage le " + heure + " : échec (" + raison(e) + ").";
            }
            prefs(c).edit().putString("etat", texte).apply();
        }
        try {
            r.put("ok", ok).put("texte", texte);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return r;
    }

    static String raison(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "pas d'Internet ou adresse du kDrive introuvable";
        if (e instanceof java.net.SocketTimeoutException) return "le kDrive ne répond pas";
        return e.getMessage() == null || e.getMessage().isEmpty() ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ------------------------------------------------------------------ réception (application « lecture »)

    static File fichierLocal(Context c) {
        return new File(c.getFilesDir(), "partage.json");
    }

    /** Dernier planning reçu (texte JSON du paquet), ou "". */
    static String donnees(Context c) {
        File f = fichierLocal(c);
        if (!f.exists()) return "";
        try (InputStream in = new FileInputStream(f)) {
            return new String(lireTout(in, 30 * 1024 * 1024), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Récupère le planning partagé ; s'il a changé : l'enregistre, met à jour les widgets et (tâche de fond)
     * prévient d'un nouveau « Contrairement ». Renvoie {ok, texte, nouveau}. Ne lève jamais d'exception.
     */
    static JSONObject recevoir(Context c, boolean fond) {
        JSONObject r = new JSONObject();
        boolean ok = false, nouveau = false;
        String texte;
        String heure = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
        synchronized (VERROU) {
            SharedPreferences p = prefs(c);
            try {
                if (!p.getBoolean("actif", false)) throw new IOException("réception désactivée");
                String mdp = lireSecret(p, "mdp"), code = lireSecret(p, "code");
                if (mdp.isEmpty() || code.isEmpty()) throw new IOException("mot de passe ou code de partage à ressaisir");
                String[] etag = {fichierLocal(c).exists() ? p.getString("etag", "") : ""};
                byte[] donnees = lire(racine(p.getString("adresse", "")), p.getString("dossier", DOSSIER_DEFAUT),
                        p.getString("identifiant", ""), mdp, etag);
                if (donnees != null) {
                    String json = new String(dechiffrer(donnees, code), StandardCharsets.UTF_8);
                    JSONObject paquet = new JSONObject(json);
                    try (OutputStream out = new FileOutputStream(fichierLocal(c))) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    String widget = paquet.optString("widget", "");
                    if (!widget.isEmpty()) {
                        c.getSharedPreferences(WidgetBase.PREFS, Context.MODE_PRIVATE).edit().putString(WidgetBase.CLE, widget)
                                .putLong(WidgetBase.CLE_MAJ, System.currentTimeMillis()).apply();
                        WidgetBase.majTous(c);
                    }
                    String envoye = paquet.optString("envoye", "");
                    p.edit().putString("etag", etag[0] == null ? "" : etag[0])
                            .putString("recu", envoye).apply();
                    avertirContrairements(c, paquet, fond);   // application ouverte : avis affiché dans la page, pas de notification
                    nouveau = true;
                }
                ok = true;
                String envoye = p.getString("recu", "");
                texte = (nouveau ? "Planning mis à jour le " : "Vérifié le ") + heure
                        + (envoye.length() >= 16 ? " (envoyé le " + envoye.substring(8, 10) + "/" + envoye.substring(5, 7)
                        + " à " + envoye.substring(11, 16) + ")." : ".");
            } catch (Exception e) {
                texte = "Réception le " + heure + " : échec (" + raison(e) + ").";
            }
            p.edit().putString("etat", texte).apply();
        }
        try {
            r.put("ok", ok).put("texte", texte).put("nouveau", nouveau);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return r;
    }

    /** Notification pour chaque avis « Contrairement » pas encore signalé sur ce téléphone (notifier = false : seulement noté). */
    static void avertirContrairements(Context c, JSONObject paquet, boolean notifier) {
        JSONObject pl = paquet.optJSONObject("planning");
        JSONArray alertes = pl == null ? null : pl.optJSONArray("alertes");
        if (alertes == null) return;
        SharedPreferences p = prefs(c);
        Set<String> vues = new HashSet<>(java.util.Arrays.asList(p.getString("alertes_signalees", "").split("\n")));
        int nouvelles = 0;
        String premiere = "";
        // avis de moins de 14 jours seulement (pas de rafale d'anciens avis à la première réception) ;
        // « validé » sur le téléphone principal ne compte pas ici : chacun valide de son côté
        String seuil = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).format(new Date(System.currentTimeMillis() - 14L * 86400000L));
        for (int i = 0; i < alertes.length(); i++) {
            JSONObject a = alertes.optJSONObject(i);
            if (a == null || a.optString("recu", "").compareTo(seuil) < 0) continue;
            String cle = a.optString("cle", "");
            if (cle.isEmpty() || vues.contains(cle)) continue;
            vues.add(cle);
            nouvelles++;
            if (premiere.isEmpty()) premiere = a.optString("debut", "");
        }
        if (nouvelles == 0) return;
        StringBuilder b = new StringBuilder();
        for (String v : vues) if (!v.isEmpty()) b.append(v).append('\n');
        p.edit().putString("alertes_signalees", b.toString()).apply();
        if (!notifier) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Changements du planning", NotificationManager.IMPORTANCE_DEFAULT);
            canal.setDescription("Un « Contrairement » change le planning partagé.");
            nm.createNotificationChannel(canal);
        }
        Intent ouvrir = new Intent(c, MainActivity.class);
        ouvrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 9, ouvrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        Notification.Builder n = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        String jour = premiere.length() == 10 ? " (à partir du " + premiere.substring(8) + "/" + premiere.substring(5, 7) + ")" : "";
        n.setSmallIcon(R.drawable.ic_notif_commande)
                .setContentTitle(nouvelles > 1 ? nouvelles + " contrairements reçus" : "Contrairement reçu")
                .setContentText("Le planning change" + jour + " : touchez pour voir les jours modifiés.")
                .setColor(0xFFC62828)
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            nm.notify(ID_NOTIF, n.build());
        } catch (SecurityException e) {
            // notifications refusées : l'avis jaune s'affichera à l'ouverture
        }
    }
}
