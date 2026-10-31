package fr.planning.commandes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Envoi d'un mail court (confirmation de réception d'une commande), sans bibliothèque :
 * SMTP avec authentification, toujours chiffré —
 *   port 465 : TLS dès la connexion ;
 *   autre port (587…) : connexion puis STARTTLS obligatoire (sinon rien n'est envoyé, le mot de passe ne part jamais en clair).
 * Même adresse et même mot de passe que le relevé (CoffreMail). Texte en UTF-8 (base64), réponse dans le fil du
 * message reçu (In-Reply-To / References) quand son Message-ID est connu.
 */
final class EnvoiMail {

    private static final int DELAI_MS = 30000;

    private final SocketFactory fabrique;          // connexion de départ (TLS direct pour le port 465)
    private final SSLSocketFactory fabriqueTls;     // passage en TLS après STARTTLS
    /** Demander un accusé de réception : de lecture (Disposition-Notification-To) et de remise (DSN, si le serveur le permet). */
    boolean accuse;
    /** Après l'envoi : vrai si l'avis de remise (DSN) a pu être demandé au serveur. */
    boolean remiseDemandee;
    /** Message-ID du dernier message envoyé (« <…@…> ») : sert à retrouver ses accusés de réception. */
    String messageId = "";
    private Socket socket;
    private BufferedReader in;
    private OutputStream out;

    EnvoiMail() {
        this(null, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    /** Pour les essais : fabriques de sockets imposées (null = choix normal selon le port). */
    EnvoiMail(SocketFactory fabrique, SSLSocketFactory fabriqueTls) {
        this.fabrique = fabrique;
        this.fabriqueTls = fabriqueTls;
    }

    /** Port 465 = TLS dès la connexion. */
    static boolean tlsDirect(int port) {
        return port == 465;
    }

    /**
     * Envoie le message. @param enReponseA Message-ID du mail reçu (« <…@…> ») ou "".
     * Lève IOException avec un message en français compréhensible.
     */
    void envoyer(String serveur, int port, String adresse, String motDePasse, String a, String sujet, String corps,
                 String enReponseA) throws IOException {
        envoyer(serveur, port, adresse, motDePasse, a, sujet, corps, enReponseA, null, null);
    }

    /** Avec une pièce jointe PDF (nomPiece, piece) : par exemple un formulaire rempli. */
    void envoyer(String serveur, int port, String adresse, String motDePasse, String a, String sujet, String corps,
                 String enReponseA, String nomPiece, byte[] piece) throws IOException {
        if (serveur == null || serveur.trim().isEmpty()) throw new IOException("serveur d'envoi (SMTP) non indiqué");
        if (!adresseValide(a)) throw new IOException("adresse du destinataire incorrecte");
        connecter(serveur.trim(), port);
        try {
            attendre(220);
            List<String> ehlo = ehlo();
            if (!(socket instanceof SSLSocket)) {
                boolean starttls = false;
                for (String l : ehlo) if (l.toUpperCase(Locale.ROOT).contains("STARTTLS")) starttls = true;
                if (!starttls) throw new IOException("le serveur n'accepte pas de connexion chiffrée sur ce port (essayez 465)");
                envoyerLigne("STARTTLS");
                attendre(220);
                SSLSocket s = (SSLSocket) fabriqueTls.createSocket(socket, serveur.trim(), port, true);
                s.startHandshake();
                ouvrirFlux(s);
                ehlo = ehlo();
            }
            authentifier(ehlo, adresse, motDePasse);
            boolean dsn = false;
            if (accuse) for (String l : ehlo) if (l.toUpperCase(Locale.ROOT).matches("^\\d{3}[ -]DSN\\b.*")) dsn = true;
            remiseDemandee = dsn;
            messageId = "<" + UUID.randomUUID() + "@" + (adresse.contains("@") ? adresse.substring(adresse.indexOf('@') + 1) : "planning.local") + ">";
            envoyerLigne("MAIL FROM:<" + adresse + ">" + (dsn ? " RET=HDRS ENVID=" + xtext(messageId) : ""));
            attendre(250);
            envoyerLigne("RCPT TO:<" + a.trim() + ">" + (dsn ? " NOTIFY=SUCCESS,FAILURE" : ""));
            int code = lireReponse(null);
            if (code != 250 && code != 251) throw new IOException("destinataire refusé par le serveur (" + code + ")");
            envoyerLigne("DATA");
            attendre(354);
            out.write(message(adresse, a.trim(), sujet, corps, enReponseA, nomPiece, piece, accuse, messageId).getBytes(StandardCharsets.UTF_8));
            envoyerLigne(".");
            attendre(250);
            try {
                envoyerLigne("QUIT");
            } catch (IOException e) {
                // déjà envoyé : la fermeture importe peu
            }
        } finally {
            fermer();
        }
    }

    static boolean adresseValide(String a) {
        return a != null && a.trim().matches("[^\\s@<>\"]+@[^\\s@<>\"]+\\.[^\\s@<>\"]+");
    }

    // ------------------------------------------------------------------ message

    static String message(String de, String a, String sujet, String corps, String enReponseA) {
        return message(de, a, sujet, corps, enReponseA, null, null);
    }

    static String message(String de, String a, String sujet, String corps, String enReponseA, String nomPiece, byte[] piece) {
        return message(de, a, sujet, corps, enReponseA, nomPiece, piece, false);
    }

    static String message(String de, String a, String sujet, String corps, String enReponseA, String nomPiece, byte[] piece,
                          boolean accuse) {
        return message(de, a, sujet, corps, enReponseA, nomPiece, piece, accuse, null);
    }

    /** xtext (RFC 3461) : caractères hors 33-126, « + » et « = » en +HH. */
    static String xtext(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= 33 && ch <= 126 && ch != '+' && ch != '=') b.append(ch);
            else b.append('+').append(String.format(Locale.ROOT, "%02X", (int) ch & 0xFF));
        }
        return b.toString();
    }

    static String message(String de, String a, String sujet, String corps, String enReponseA, String nomPiece, byte[] piece,
                          boolean accuse, String messageId) {
        StringBuilder m = new StringBuilder();
        String domaine = de.contains("@") ? de.substring(de.indexOf('@') + 1) : "planning.local";
        m.append("From: <").append(de).append(">\r\n");
        m.append("To: <").append(a).append(">\r\n");
        m.append("Subject: ").append(encoderEntete(sujet == null ? "" : sujet)).append("\r\n");
        m.append("Date: ").append(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(new Date())).append("\r\n");
        m.append("Message-ID: ").append(messageId != null && !messageId.isEmpty() ? messageId
                : "<" + UUID.randomUUID() + "@" + domaine + ">").append("\r\n");
        String ref = enReponseA == null ? "" : enReponseA.trim();
        if (ref.matches("<[^<>\\s]+>")) {
            m.append("In-Reply-To: ").append(ref).append("\r\n");
            m.append("References: ").append(ref).append("\r\n");
        }
        if (accuse) {                     // accusé de lecture : renvoyé à l'adresse d'envoi si le destinataire l'accepte
            m.append("Disposition-Notification-To: <").append(de).append(">\r\n");
            m.append("Return-Receipt-To: <").append(de).append(">\r\n");
        }
        m.append("MIME-Version: 1.0\r\n");
        String texte = b64(
                (corps == null ? "" : corps).replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        if (piece == null) {
            m.append("Content-Type: text/plain; charset=UTF-8\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n");
            m.append("\r\n");
            lignes76(m, texte);
            return m.toString();
        }
        // texte + pièce jointe PDF (multipart/mixed)
        String limite = "=_planning_" + UUID.randomUUID().toString().replace("-", "");
        String nom = (nomPiece == null || nomPiece.trim().isEmpty() ? "document.pdf" : nomPiece.trim()).replace("\"", "'");
        m.append("Content-Type: multipart/mixed; boundary=\"").append(limite).append("\"\r\n");
        m.append("\r\n");
        m.append("--").append(limite).append("\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        lignes76(m, texte);
        m.append("--").append(limite).append("\r\n");
        String enc = encoderEntete(nom).replace("\r\n ", " ");
        m.append("Content-Type: application/pdf; name=\"").append(enc).append("\"\r\n");
        m.append("Content-Disposition: attachment; filename=\"").append(enc).append("\"\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        lignes76(m, b64(piece));
        m.append("--").append(limite).append("--\r\n");
        return m.toString();
    }

    private static void lignes76(StringBuilder m, String b64) {
        for (int i = 0; i < b64.length(); i += 76) m.append(b64, i, Math.min(b64.length(), i + 76)).append("\r\n");
    }

    /** Sujet en ASCII tel quel, sinon « =?UTF-8?B?…?= » (morceaux de 45 octets au plus). */
    static String encoderEntete(String s) {
        s = s.replace("\r", " ").replace("\n", " ");
        boolean ascii = true;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 126 || s.charAt(i) < 32) ascii = false;
        if (ascii) return s;
        StringBuilder r = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int j = i, octets = 0;
            while (j < s.length()) {
                int cp = s.codePointAt(j);
                int n = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
                if (octets + n > 45) break;
                octets += n;
                j += Character.charCount(cp);
            }
            if (r.length() > 0) r.append("\r\n ");
            r.append("=?UTF-8?B?").append(b64(s.substring(i, j).getBytes(StandardCharsets.UTF_8))).append("?=");
            i = j;
        }
        return r.toString();
    }

    /** base64 sur une ligne (android.util.Base64 : java.util.Base64 n'existe qu'à partir d'Android 8). */
    static String b64(byte[] o) {
        return android.util.Base64.encodeToString(o, android.util.Base64.NO_WRAP);
    }

    // ------------------------------------------------------------------ SMTP

    private void connecter(String serveur, int port) throws IOException {
        SocketFactory f = fabrique != null ? fabrique : tlsDirect(port) ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        Socket s = f.createSocket();
        s.connect(new InetSocketAddress(serveur, port), DELAI_MS);
        s.setSoTimeout(DELAI_MS);
        if (s instanceof SSLSocket) ((SSLSocket) s).startHandshake();
        ouvrirFlux(s);
    }

    private void ouvrirFlux(Socket s) throws IOException {
        socket = s;
        socket.setSoTimeout(DELAI_MS);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();
    }

    private List<String> ehlo() throws IOException {
        envoyerLigne("EHLO [127.0.0.1]");
        List<String> l = new ArrayList<>();
        int code = lireReponse(l);
        if (code != 250) throw new IOException("le serveur refuse la connexion (" + code + ")");
        return l;
    }

    private void authentifier(List<String> ehlo, String adresse, String mdp) throws IOException {
        boolean plain = false, login = false;
        for (String l : ehlo) {
            String u = l.toUpperCase(Locale.ROOT);
            if (u.contains("AUTH")) {
                if (u.matches(".*\\bPLAIN\\b.*")) plain = true;
                if (u.matches(".*\\bLOGIN\\b.*")) login = true;
            }
        }
        int code;
        if (plain || !login) {
            envoyerLigne("AUTH PLAIN " + b64(("\0" + adresse + "\0" + mdp).getBytes(StandardCharsets.UTF_8)));
            code = lireReponse(null);
        } else {
            envoyerLigne("AUTH LOGIN");
            if (lireReponse(null) != 334) throw new IOException("authentification refusée par le serveur");
            envoyerLigne(b64(adresse.getBytes(StandardCharsets.UTF_8)));
            if (lireReponse(null) != 334) throw new IOException("authentification refusée par le serveur");
            envoyerLigne(b64(mdp.getBytes(StandardCharsets.UTF_8)));
            code = lireReponse(null);
        }
        if (code != 235) throw new IOException("envoi refusé : adresse ou mot de passe incorrect (ou mot de passe d'application nécessaire)");
    }

    private void envoyerLigne(String l) throws IOException {
        out.write((l + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void attendre(int attendu) throws IOException {
        int code = lireReponse(null);
        if (code != attendu) throw new IOException("réponse inattendue du serveur d'envoi (" + code + ")");
    }

    /** Lit une réponse (plusieurs lignes « 250-… » puis « 250 … ») ; renvoie le code. */
    private int lireReponse(List<String> lignes) throws IOException {
        while (true) {
            String l = in.readLine();
            if (l == null) throw new IOException("le serveur d'envoi a coupé la connexion");
            if (lignes != null) lignes.add(l);
            if (l.length() < 3) continue;
            int code;
            try {
                code = Integer.parseInt(l.substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IOException("réponse illisible du serveur d'envoi");
            }
            if (l.length() == 3 || l.charAt(3) != '-') return code;
        }
    }

    private void fermer() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // rien
        }
    }
}
