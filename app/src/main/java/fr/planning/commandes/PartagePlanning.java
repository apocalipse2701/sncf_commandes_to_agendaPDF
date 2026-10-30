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
 * Partage du planning avec un proche (conjoint…), par un dossier WebDAV — NAS Synology (paquet « WebDAV Server »,
 * https, port 5006 par défaut), Infomaniak kDrive (offres avec WebDAV) ou tout autre serveur WebDAV en https :
 *   - l'application principale (version manuelle ou « mail ») dépose, après chaque changement, le planning
 *     CHIFFRÉ dans <dossier>/planning-partage.bin (PUT) ;
 *   - l'application « lecture » (PlanningCommandes-lecture.apk) le relit à l'ouverture et toutes les heures
 *     (tâche Android), met à jour ses widgets et prévient d'un « Contrairement ».
 * Chiffrement : AES-256-GCM, clé tirée du CODE DE PARTAGE (PBKDF2, 150 000 tours, sel aléatoire) :
 * sans le code, le fichier est illisible, même pour qui a accès au serveur.
 * Contenu (avant chiffrement, gzip) : {"version":1, "envoye":"…", "planning":{…}, "codesTxt":"…", "theme":"…", "widget":"…"}.
 * Réglages dans les préférences « partage » ; mot de passe et code chiffrés sur le téléphone (CoffreMail).
 */
public class PartagePlanning extends JobService {

    static final String PREFS = "partage";
    static final String FICHIER = "planning-partage.bin";
    static final String DOSSIER_DEFAUT = "Planning";   // Synology : dossier partagé « Planning » (kDrive : « Private/Planning partagé »)
    static final int ID_TACHE = 52, ID_NOTIF = 53;
    static final String CANAL = "partage";
    private static final byte[] MAGIQUE = {'P', 'C', 'P', '1'};
    private static final int TOURS = 150000;
    private static final int DELAI_MS = 30000;
    static final Object VERROU = new Object();

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
            o.put("service", p.getString("service", ""));          // vide : réglages d'avant 2.9.4 (la page devine)
            o.put("adresse", p.getString("adresse", ""));
            o.put("identifiant", p.getString("identifiant", ""));
            o.put("dossier", p.getString("dossier", DOSSIER_DEFAUT));
            o.put("motDePasse", !p.getString("mdp", "").isEmpty());
            o.put("code", lireSecret(p, "code"));
            o.put("etat", p.getString("etat", ""));
            o.put("recu", p.getString("recu", ""));
            o.put("certificat", p.getString("certificat", ""));
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
            if (adresse.isEmpty()) throw new IOException("indiquez l'adresse du serveur (NAS Synology, numéro du kDrive ou adresse WebDAV)");
            if (o.optString("identifiant", "").trim().isEmpty()) throw new IOException("indiquez l'identifiant (adresse mail du compte)");
            if (normaliser(code).length() < 8) throw new IOException("le code de partage doit avoir au moins 8 lettres ou chiffres");
        }
        ed.putBoolean("actif", o.optBoolean("actif", false));
        if (!adresse.equals(prefs(c).getString("adresse", ""))) ed.remove("certificat");   // autre serveur : certificat à accepter de nouveau
        ed.putString("adresse", adresse);
        String service = o.optString("service", "synology");
        ed.putString("service", service.matches("synology|kdrive|autre") ? service : "synology");
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

    static String lireSecret(SharedPreferences p, String cle) {
        String s = p.getString(cle, "");
        if (s.isEmpty()) return "";
        try {
            return CoffreMail.dechiffrer(s);
        } catch (Exception e) {
            return "";
        }
    }

    /** L'utilisateur fait confiance au certificat présenté par le serveur (empreinte SHA-256 en hexadécimal). */
    static void faireConfiance(Context c, String empreinte) throws IOException {
        String e = empreinte == null ? "" : empreinte.trim().toLowerCase(Locale.ROOT).replace(":", "");
        if (e.isEmpty()) {                                         // « Ne plus faire confiance »
            prefs(c).edit().remove("certificat").apply();
            return;
        }
        if (!e.matches("[0-9a-f]{64}")) throw new IOException("empreinte de certificat incorrecte");
        prefs(c).edit().putString("certificat", e).apply();
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

    // ------------------------------------------------------------------ WebDAV (NAS Synology, kDrive…)

    /** Port WebDAV en https d'un NAS Synology (paquet « WebDAV Server »), pris quand l'adresse n'en indique pas. */
    static final int PORT_SYNOLOGY = 5006;

    /**
     * Adresse du serveur WebDAV :
     *   « 123456 » (numéro du kDrive) → https://123456.connect.kdrive.infomaniak.com/ ;
     *   « monnas.synology.me » ou « monnas.synology.me:5006 » (NAS Synology) → https://monnas.synology.me:5006/ ;
     *   adresse complète en https:// telle quelle. http:// refusé (mot de passe et planning ne circulent qu'en https).
     */
    static String racine(String adresse) throws IOException {
        String a = adresse.trim();
        if (a.matches("\\d{3,12}")) return "https://" + a + ".connect.kdrive.infomaniak.com/";
        if (a.toLowerCase(Locale.ROOT).startsWith("http://"))
            throw new IOException("adresse en http:// refusée : utilisez https:// (sur le Synology : WebDAV Server › HTTPS, port 5006)");
        if (a.matches("(?i)[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:\\d{1,5})?/?")) {      // nom (ou IP) du NAS, sans https://
            a = a.replaceAll("/$", "");
            return "https://" + a + (a.contains(":") ? "" : ":" + PORT_SYNOLOGY) + "/";
        }
        if (!a.toLowerCase(Locale.ROOT).startsWith("https://")) throw new IOException("adresse incorrecte : nom du NAS (ex. monnas.synology.me), numéro du kDrive ou adresse en https://");
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

    // ------------------------------------------------------------------ certificat du serveur
    // Normal : certificat reconnu par Android (Let's Encrypt…) et au nom de l'adresse. Sinon (NAS joint par son
    // adresse IP, certificat d'origine du Synology), l'utilisateur peut « faire confiance » à CE certificat :
    // son empreinte SHA-256 est gardée (prefs « certificat ») et seul ce certificat-là est ensuite accepté.
    // Rien n'est envoyé (ni identifiant ni planning) à un serveur dont le certificat n'est pas accepté.

    /** Essais seulement : autorités de confiance imposées (null = celles du téléphone). */
    static java.security.KeyStore confianceEssai = null;
    /** Empreinte (hex) du certificat accepté par l'utilisateur, "" si aucun. Réglée sous VERROU avant chaque échange. */
    static volatile String epingle = "";
    /** Empreinte du dernier certificat présenté par le serveur (pour la proposer quand il est refusé). */
    static volatile String vue = "";
    /** Le nom du serveur ne correspond pas à son certificat (adresse IP…) : l'échange a été refusé avant tout envoi. */
    static volatile boolean nomRefuse = false;

    static String empreinte(java.security.cert.Certificate c) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format(Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean epingleOk(javax.net.ssl.SSLSession s) {
        try {
            java.security.cert.Certificate[] cc = s.getPeerCertificates();
            return cc.length > 0 && !epingle.isEmpty() && epingle.equals(empreinte(cc[0]));
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            return false;
        }
    }

    private static boolean surAndroid() {
        return "Dalvik".equals(System.getProperty("java.vm.name"));
    }

    /** Certificat accepté par l'utilisateur, ou vérification normale du nom (Android ; sur ordinateur, déjà faite avant). */
    static final javax.net.ssl.HostnameVerifier VERIFICATEUR = (hote, session) -> {
        if (epingleOk(session)) return true;
        boolean bon = surAndroid() && javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hote, session);
        if (!bon) {
            nomRefuse = true;
            try {
                java.security.cert.Certificate[] cc = session.getPeerCertificates();
                if (cc.length > 0) vue = empreinte(cc[0]);
            } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
                // pas de certificat
            }
        }
        return bon;
    };

    /** Vérification normale des certificats, ou certificat accepté par l'utilisateur (empreinte identique). */
    static final class Confiance implements javax.net.ssl.X509TrustManager {
        private final javax.net.ssl.X509TrustManager normal;

        Confiance() throws java.security.GeneralSecurityException {
            javax.net.ssl.TrustManagerFactory f = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            f.init(confianceEssai);
            javax.net.ssl.X509TrustManager t = null;
            for (javax.net.ssl.TrustManager m : f.getTrustManagers()) if (m instanceof javax.net.ssl.X509TrustManager) t = (javax.net.ssl.X509TrustManager) m;
            if (t == null) throw new java.security.GeneralSecurityException("aucun gestionnaire de certificats");
            normal = t;
        }

        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String type) throws java.security.cert.CertificateException {
            normal.checkClientTrusted(c, type);
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String type) throws java.security.cert.CertificateException {
            if (c == null || c.length == 0) throw new java.security.cert.CertificateException("aucun certificat");
            vue = empreinte(c[0]);
            if (!epingle.isEmpty() && epingle.equals(vue)) return;          // le certificat accepté par l'utilisateur
            normal.checkServerTrusted(c, type);
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return normal.getAcceptedIssuers();
        }
    }

    static javax.net.ssl.SSLSocketFactory fabrique() throws IOException {
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{new Confiance()}, null);
            return ctx.getSocketFactory();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("connexion sécurisée impossible (" + e.getMessage() + ")");
        }
    }

    /** Empreinte proposée à l'utilisateur quand l'échange a échoué à cause du certificat, sinon "". */
    static String certificatARefuse(Throwable e) {
        if (nomRefuse) return vue.isEmpty() || vue.equals(epingle) ? "" : vue;
        for (Throwable t = e; t != null; t = t.getCause())
            if (t instanceof javax.net.ssl.SSLException || t instanceof java.security.cert.CertificateException)
                return vue.isEmpty() || vue.equals(epingle) ? "" : vue;
        return "";
    }

    private static String auth(String identifiant, String mdp) {
        return "Basic " + android.util.Base64.encodeToString(
                (identifiant + ":" + mdp).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }

    private static HttpURLConnection connexion(String url, String methode, String identifiant, String mdp) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("adresse refusée (https obligatoire)");
        HttpURLConnection h = (HttpURLConnection) u.openConnection();
        if (h instanceof javax.net.ssl.HttpsURLConnection) {
            ((javax.net.ssl.HttpsURLConnection) h).setSSLSocketFactory(fabrique());
            ((javax.net.ssl.HttpsURLConnection) h).setHostnameVerifier(VERIFICATEUR);
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

    /** Réponse d'une requête WebDAV écrite à la main. */
    static final class Reponse {
        final int code;
        final String corps;
        Reponse(int code, String corps) { this.code = code; this.corps = corps; }
    }

    /**
     * MKCOL / PROPFIND : HttpURLConnection ne connaît pas ces méthodes WebDAV, d'où une requête HTTP/1.1
     * écrite à la main sur une connexion TLS (nom du serveur vérifié comme pour le reste).
     */
    static Reponse requete(String methode, String url, String identifiant, String mdp, String corps, String entetes) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("adresse refusée (https obligatoire)");
        String hote = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 443;
        javax.net.ssl.SSLSocketFactory f = fabrique();
        java.net.Socket brut = new java.net.Socket();
        brut.connect(new java.net.InetSocketAddress(hote, port), DELAI_MS);
        try (javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket) f.createSocket(brut, hote, port, true)) {
            s.setSoTimeout(DELAI_MS);
            // nom du serveur vérifié pendant la poignée de main (comme HttpsURLConnection), sauf certificat accepté par l'utilisateur…
            if (epingle.isEmpty()) {
                javax.net.ssl.SSLParameters prm = s.getSSLParameters();
                prm.setEndpointIdentificationAlgorithm("HTTPS");
                s.setSSLParameters(prm);
            }
            s.startHandshake();
            // … et après : certificat accepté, ou vérificateur d'Android (sur un ordinateur : déjà fait ci-dessus)
            if (!epingleOk(s.getSession()) && (!epingle.isEmpty() || surAndroid()) && !VERIFICATEUR.verify(hote, s.getSession()))
                throw new javax.net.ssl.SSLPeerUnverifiedException("certificat du serveur WebDAV non reconnu");
            String chemin = u.getPath().isEmpty() ? "/" : u.getPath();
            byte[] c = (corps == null ? "" : corps).getBytes(StandardCharsets.UTF_8);
            String req = methode + " " + chemin + " HTTP/1.1\r\n"
                    + "Host: " + hote + (u.getPort() > 0 ? ":" + port : "") + "\r\n"
                    + "User-Agent: PlanningCommandes\r\n"
                    + "Authorization: " + auth(identifiant, mdp) + "\r\n"
                    + (entetes == null ? "" : entetes)
                    + "Content-Length: " + c.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = s.getOutputStream();
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.write(c);
            out.flush();
            InputStream in = new java.io.BufferedInputStream(s.getInputStream());
            String l = ligne(in);
            if (l == null || !l.startsWith("HTTP/")) throw new IOException("réponse illisible du serveur WebDAV");
            int code;
            try {
                code = Integer.parseInt(l.split(" ")[1]);
            } catch (RuntimeException e) {
                throw new IOException("réponse illisible du serveur WebDAV");
            }
            boolean morceaux = false;
            int longueur = -1;
            String h;
            while ((h = ligne(in)) != null && !h.isEmpty()) {
                String b = h.toLowerCase(Locale.ROOT);
                if (b.startsWith("transfer-encoding:") && b.contains("chunked")) morceaux = true;
                if (b.startsWith("content-length:")) {
                    try { longueur = Integer.parseInt(b.substring(15).trim()); } catch (NumberFormatException e) { longueur = -1; }
                }
            }
            if (methode.equals("MKCOL")) return new Reponse(code, "");
            ByteArrayOutputStream r = new ByteArrayOutputStream();
            if (morceaux) {
                String t;
                while ((t = ligne(in)) != null) {
                    int n;
                    try { n = Integer.parseInt(t.split(";")[0].trim(), 16); } catch (NumberFormatException e) { break; }
                    if (n == 0) break;
                    byte[] bloc = new byte[n];
                    int lu = 0;
                    while (lu < n) { int k = in.read(bloc, lu, n - lu); if (k < 0) break; lu += k; }
                    r.write(bloc, 0, lu);
                    ligne(in);
                    if (r.size() > 2 * 1024 * 1024) break;
                }
            } else {
                byte[] bloc = new byte[8192];
                int k;
                while ((longueur < 0 || r.size() < longueur) && (k = in.read(bloc)) > 0) {
                    r.write(bloc, 0, k);
                    if (r.size() > 2 * 1024 * 1024) break;
                }
            }
            return new Reponse(code, new String(r.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            try { brut.close(); } catch (IOException e) { /* rien */ }
        }
    }

    private static String ligne(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') return b.toString();
            if (c != '\r') b.append((char) c);
            if (b.length() > 16384) break;
        }
        return c < 0 && b.length() == 0 ? null : b.toString();
    }

    static int mkcol(String url, String identifiant, String mdp) throws IOException {
        return requete("MKCOL", url, identifiant, mdp, null, null).code;
    }

    /**
     * Dossiers à la racine du serveur (PROPFIND, profondeur 1), pour un message utile quand le dépôt est refusé.
     * null si la racine elle-même est refusée (offre sans WebDAV, compte sans accès…).
     */
    static java.util.List<String> dossiersRacine(String racine, String identifiant, String mdp) throws IOException {
        Reponse r = requete("PROPFIND", racine, identifiant, mdp,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>",
                "Depth: 1\r\nContent-Type: application/xml; charset=utf-8\r\n");
        if (r.code == 401) throw new IOException(message(401, ""));
        if (r.code != 207) return null;
        String base = new URL(racine).getPath();
        java.util.List<String> noms = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(?:[A-Za-z0-9]+:)?href>([^<]*)</(?:[A-Za-z0-9]+:)?href>").matcher(r.corps);
        while (m.find()) {
            String href = m.group(1).trim();
            try {
                href = java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8");
            } catch (RuntimeException e) {
                // gardé tel quel
            }
            if (href.startsWith("http")) {
                try { href = new URL(href).getPath(); } catch (IOException e) { continue; }
            }
            if (href.startsWith(base)) href = href.substring(base.length());
            href = href.replaceAll("^/+|/+$", "");
            if (!href.isEmpty() && !href.contains("/") && !noms.contains(href)) noms.add(href);
        }
        return noms;
    }

    /** Message quand le dépôt est refusé : dit ce qu'il y a vraiment à la racine du serveur. */
    static String diagnostic(String racine, String dossier, String identifiant, String mdp, int code) {
        try {
            java.util.List<String> noms = dossiersRacine(racine, identifiant, mdp);
            if (noms == null) return "le serveur refuse l'accès WebDAV (réponse " + code + "). Synology : paquet WebDAV Server installé, et"
                    + " l'utilisateur autorisé à utiliser WebDAV (Panneau de configuration › Privilèges d'application). kDrive : offre avec WebDAV"
                    + " (pas kSuite Free/Standard, my kSuite, my kSuite+), identifiant = adresse mail du compte";
            String premier = dossier.replace("\\", "/").split("/")[0].trim();
            if (noms.isEmpty()) return "dépôt refusé (réponse " + code + ") et aucun dossier visible pour cet utilisateur sur le serveur :"
                    + " sur le Synology, donnez-lui l'accès en lecture/écriture au dossier partagé (Panneau de configuration › Utilisateur › Permissions)";
            StringBuilder lb = new StringBuilder();          // (String.join : Android 8 et plus seulement)
            for (String n : noms) lb.append(lb.length() > 0 ? ", " : "").append(n);
            String liste = lb.toString();
            if (!premier.isEmpty() && !noms.contains(premier))
                return "le dossier « " + premier + " » n'existe pas sur le serveur, ou cet utilisateur n'y a pas accès. Dossiers trouvés : " + liste
                        + ". Dans « Dossier », indiquez l'un d'eux, par exemple « " + noms.get(0) + " » (le même sur les deux téléphones)."
                        + " Synology : un dossier partagé se crée dans Panneau de configuration › Dossier partagé, pas depuis l'application";
            return "dépôt refusé dans « " + dossier + " » (réponse " + code + ") : cet utilisateur n'a sans doute que la lecture."
                    + " Dossiers visibles : " + liste + ". Synology : Panneau de configuration › Utilisateur › Permissions → Lecture/Écriture";
        } catch (IOException e) {
            return message(code, "dépôt du planning") + " ; " + raison(e);
        }
    }

    private static String message(int code, String quoi) {
        if (code == 401) return "identifiant ou mot de passe refusé par le serveur (Synology : utilisateur du NAS ; kDrive : mot de passe d'application)";
        if (code == 403) return "accès refusé par le serveur pour " + quoi + " (dossier en lecture seule ?)";
        if (code == 404) return "dossier introuvable sur le serveur";
        if (code == 507) return "serveur plein";
        return "réponse " + code + " du serveur (" + quoi + ")";
    }

    static void deposer(String racine, String dossier, String identifiant, String mdp, byte[] donnees) throws IOException {
        deposer(racine, dossier, FICHIER, identifiant, mdp, donnees);
    }

    /** Dépose « nom » dans « dossier » (créé niveau par niveau s'il manque). Sert aussi à la sauvegarde des bulletins. */
    static void deposer(String racine, String dossier, String nom, String identifiant, String mdp, byte[] donnees) throws IOException {
        String base = racine + segments(dossier);
        String fichier = URLEncoder.encode(nom, "UTF-8").replace("+", "%20");
        int code = put(base + fichier, identifiant, mdp, donnees);
        if (code == 401) throw new IOException(message(401, ""));
        // dossier absent : 404/409 selon les serveurs, 403 pour kDrive et Synology. On le crée, niveau par niveau, puis on réessaie.
        if (code == 403 || code == 404 || code == 409) {
            String chemin = racine;
            for (String s : segments(dossier).split("/")) {
                if (s.isEmpty()) continue;
                chemin += s + "/";
                int c = mkcol(chemin, identifiant, mdp);
                if (c == 401) throw new IOException(message(401, ""));
                if (c != 201 && c != 405 && c != 200 && c != 301) throw new IOException(diagnostic(racine, dossier, identifiant, mdp, c));
            }
            code = put(base + fichier, identifiant, mdp, donnees);
        }
        if (code == 403 || code == 404 || code == 409) throw new IOException(diagnostic(racine, dossier, identifiant, mdp, code));
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
            if (c == 403) throw new IOException(diagnostic(racine, dossier, identifiant, mdp, c));
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
        String texte, certificat = "";
        synchronized (VERROU) {
            try {
                SharedPreferences p = prefs(c);
                epingle = p.getString("certificat", "");
                vue = "";
                nomRefuse = false;
                if (!p.getBoolean("actif", false)) throw new IOException("partage désactivé");
                String mdp = lireSecret(p, "mdp"), code = lireSecret(p, "code");
                if (mdp.isEmpty() || code.isEmpty()) throw new IOException("mot de passe ou code de partage à ressaisir");
                byte[] donnees = chiffrer(paquet.getBytes(StandardCharsets.UTF_8), code);
                deposer(racine(p.getString("adresse", "")), p.getString("dossier", DOSSIER_DEFAUT),
                        p.getString("identifiant", ""), mdp, donnees);
                ok = true;
                texte = "Planning partagé envoyé le " + heure + ".";
            } catch (Exception e) {
                certificat = certificatARefuse(e);
                texte = "Partage le " + heure + " : échec (" + raison(e) + ").";
            }
            prefs(c).edit().putString("etat", texte).apply();
        }
        try {
            r.put("ok", ok).put("texte", texte).put("certificat", certificat);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return r;
    }

    static String raison(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "pas d'Internet ou adresse du serveur introuvable";
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException)
            return "le serveur ne répond pas (NAS éteint, port 5006 non redirigé sur la box, ou WebDAV HTTPS désactivé)";
        if (!certificatARefuse(e).isEmpty() || e instanceof javax.net.ssl.SSLException)
            return "certificat du serveur non reconnu (adresse IP, ou certificat d'origine du NAS) : touchez « Faire confiance à ce"
                    + " certificat » quand l'application le propose (Envoyer, Recevoir ou Sauvegarder maintenant), ou installez un"
                    + " certificat Let's Encrypt sur le NAS";
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
        String texte, certificat = "";
        String heure = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
        synchronized (VERROU) {
            SharedPreferences p = prefs(c);
            epingle = p.getString("certificat", "");
            vue = "";
            nomRefuse = false;
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
                certificat = certificatARefuse(e);
                texte = "Réception le " + heure + " : échec (" + raison(e) + ").";
            }
            p.edit().putString("etat", texte).apply();
        }
        try {
            r.put("ok", ok).put("texte", texte).put("nouveau", nouveau).put("certificat", certificat);
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
