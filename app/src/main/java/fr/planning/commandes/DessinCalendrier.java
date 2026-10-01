package fr.planning.commandes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * Dessin du widget « Calendrier » : le mois en grille, comme la page du PDF.
 * Port de dessiner() (mobile/src/rendu.js, lui-même port de dessiner_planning de planning_pdf.py) :
 * mêmes couleurs de thème, mêmes règles de texte (numéro, férié, service à côté du numéro, lignes,
 * repos en gros, GRÈVE en filigrane). Différences propres au widget : les proportions suivent la taille
 * du widget (et non la page A4), le titre laisse la place aux flèches ‹ ›, et le jour J est encadré.
 * Le contenu des cases est préparé par l'application (Rendu.contenuJour, envoyé dans « cal ») :
 * ici on ne fait que la mise en page et le dessin.
 * Case trop petite pour les lignes détaillées (AUTO / PS / FS…) : on écrit à la place les horaires courts
 * « 13:40–21:40 » (« hc »), comme la grille compacte de l'application. Chaque case est découpée à ses bords.
 */
final class DessinCalendrier {

    // Géométrie en millièmes : identique aux poids de res/layout/widget_calendrier.xml (cases touchables).
    static final float H_TITRE = 130f, H_ENTETE = 56f, H_LIGNE = 130f, H_BAS = 34f;   // 130 + 56 + 6 × 130 + 34 = 1000
    static final float MARGE = 25f, COL = 950f / 7f;                                    // 25 + 7 × COL + 25 = 1000
    static final int NB_LIGNES = 6;

    static final String[] MOIS = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août",
            "Septembre", "Octobre", "Novembre", "Décembre"};
    static final String[] JOURS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};
    static final String[] JOURS_COURTS = {"lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."};

    /** Thème « Classique » (regles.json, themes.base) : quand l'application n'a encore rien envoyé. */
    private static final String[][] BASE = {{"fond", "#ffffff"}, {"titre", "#5a5a5a"}, {"annee", "#3c3c3c"},
            {"trait", "#555555"}, {"grille", "#bebebe"}, {"weekend", "#fce2e2"}, {"weekend_entete", "#f8d0d0"},
            {"entete_txt", "#555555"}, {"entete_txt_we", "#af4b4b"}, {"chiffre", "#505050"}, {"chiffre_we", "#af4b4b"},
            {"txt", "#232323"}, {"txt_doux", "#5f5f5f"}, {"txt_repos", "#969696"}, {"gros", "#8c5019"}};

    private final Canvas cv;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float minPx;
    private final Typeface serif = Typeface.SERIF;
    private final Typeface sans = Typeface.SANS_SERIF;
    private final Typeface sansGras = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    private DessinCalendrier(Canvas cv, float dp) {
        this.cv = cv;
        this.minPx = 5f * dp;                        // en dessous, illisible sur un écran de téléphone
    }

    // ---------------------------------------------------------------- outils

    static float lum(int c) {
        return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c);
    }

    /** Couleur d'une palette envoyée par l'application ({"fond": "#rrggbb", …}), ou null. */
    static Integer couleur(JSONObject pal, String cle) {
        String h = pal == null ? "" : pal.optString(cle, "");
        if (h.isEmpty()) return null;
        try {
            return Color.parseColor(h);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static JSONObject paletteDefaut() {
        JSONObject o = new JSONObject();
        try {
            for (String[] kv : BASE) o.put(kv[0], kv[1]);
        } catch (org.json.JSONException e) {
            // impossible
        }
        return o;
    }

    private void police(Typeface tf, float px) {
        p.setTypeface(tf);
        p.setTextSize(Math.max(minPx, px));
    }

    private float larg(String t, Typeface tf, float px) {
        police(tf, px);
        return p.measureText(t);
    }

    /** Hauteur au-dessus de la ligne de base (police entière). */
    private float asc(Typeface tf, float px) {
        police(tf, px);
        return -p.getFontMetrics().ascent;
    }

    /** Taille réelle de la police (après le minimum lisible). */
    private float reel(float px) {
        return Math.max(minPx, px);
    }

    /** ancre : ls = gauche/ligne de base, rs = droite/ligne de base, mt = centré/haut, mm = centré/milieu. */
    private void texte(String t, float x, float y, Typeface tf, float px, int c, String ancre) {
        police(tf, px);
        p.setColor(c);
        p.setStyle(Paint.Style.FILL);
        char h = ancre.charAt(0);
        p.setTextAlign(h == 'l' ? Paint.Align.LEFT : h == 'r' ? Paint.Align.RIGHT : Paint.Align.CENTER);
        if (ancre.equals("mm")) {
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(t, x, y - (fm.ascent + fm.descent) / 2, p);
        } else if (ancre.charAt(1) == 't') {
            cv.drawText(t, x, y - p.getFontMetrics().ascent, p);
        } else {
            cv.drawText(t, x, y, p);
        }
    }

    private void rect(float x0, float y0, float x1, float y1, int c) {
        p.setColor(c);
        p.setStyle(Paint.Style.FILL);
        cv.drawRect(x0, y0, x1, y1, p);
    }

    private void ligne(float xa, float ya, float xb, float yb, int c, float w) {
        p.setColor(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        cv.drawLine(xa, ya, xb, yb, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Coupe un texte en lignes qui tiennent dans « largeur » (mots trop longs coupés avec un tiret). */
    private List<String> couper(String t, Typeface tf, float px, float largeur) {
        List<String> lignes = new ArrayList<>();
        String cour = "";
        for (String m : t.trim().split("\\s+")) {
            if (m.isEmpty()) continue;
            String essai = (cour + " " + m).trim();
            if (larg(essai, tf, px) <= largeur) {
                cour = essai;
                continue;
            }
            if (!cour.isEmpty()) lignes.add(cour);
            while (larg(m, tf, px) > largeur && m.length() > 1) {
                int k = m.length();
                while (k > 1 && larg(m.substring(0, k) + "-", tf, px) > largeur) k--;
                lignes.add(m.substring(0, k) + "-");
                m = m.substring(k);
            }
            cour = m;
        }
        if (!cour.isEmpty()) lignes.add(cour);
        return lignes;
    }

    static String iso(Calendar d) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1,
                d.get(Calendar.DAY_OF_MONTH));
    }

    /** Lundi de la semaine du 1er du mois (première case de la grille). */
    static Calendar premiereCase(int annee, int mois) {
        Calendar d = new GregorianCalendar(annee, mois - 1, 1);
        int decalage = (d.get(Calendar.DAY_OF_WEEK) + 5) % 7;          // lundi = 0
        d.add(Calendar.DAY_OF_MONTH, -decalage);
        return d;
    }

    /** Les 42 cases de la grille (6 semaines) : date ISO, ou null hors du mois. */
    static String[] cases(int annee, int mois) {
        String[] r = new String[7 * NB_LIGNES];
        Calendar d = premiereCase(annee, mois);
        for (int k = 0; k < r.length; k++) {
            r[k] = d.get(Calendar.MONTH) == mois - 1 ? iso(d) : null;
            d.add(Calendar.DAY_OF_MONTH, 1);
        }
        return r;
    }

    // ---------------------------------------------------------------- dessin

    /**
     * @param W, H       taille du dessin en pixels
     * @param dp         pixels par dp (pour les épaisseurs et la taille minimale du texte)
     * @param mois       1 à 12
     * @param cal        {"t": palette du thème, "c": palette Classique, "j": {date: case}} ou null
     * @param aujourdhui date ISO du jour (encadrée)
     * @param message    texte d'alerte en rouge à côté du titre, ou null
     */
    static Bitmap dessiner(int W, int H, float dp, int annee, int mois, JSONObject cal, String aujourdhui, String message) {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        new DessinCalendrier(new Canvas(bmp), dp).page(W, H, dp, annee, mois, cal, aujourdhui, message);
        return bmp;
    }

    private void page(int W, int H, float dp, int annee, int mois, JSONObject cal, String aujourdhui, String message) {
        JSONObject P = cal == null ? null : cal.optJSONObject("t");
        if (P == null) P = paletteDefaut();
        JSONObject cl = cal == null ? null : cal.optJSONObject("c");
        if (cl == null) cl = paletteDefaut();
        JSONObject jours = cal == null ? null : cal.optJSONObject("j");
        int noir = Color.rgb(35, 35, 35);
        int fond = val(P, "fond", Color.WHITE), titre = val(P, "titre", noir), annee_c = val(P, "annee", noir);
        int trait = val(P, "trait", noir), grille = val(P, "grille", Color.rgb(190, 190, 190));
        int weekend = val(P, "weekend", fond), weekendEntete = val(P, "weekend_entete", weekend);
        Integer entete = couleur(P, "entete"), horsMois = couleur(P, "hors_mois");

        // fond arrondi (les lanceurs récents arrondissent aussi les widgets)
        p.setColor(fond);
        p.setStyle(Paint.Style.FILL);
        cv.drawRoundRect(new RectF(0, 0, W, H), 16 * dp, 16 * dp, p);

        float x0 = W * MARGE / 1000f, x1 = W - x0, col = W * COL / 1000f;
        float yTrait = H * H_TITRE / 1000f, yGrille = yTrait + H * H_ENTETE / 1000f;
        float lig = H * H_LIGNE / 1000f, yFin = yGrille + NB_LIGNES * lig;
        float hEntete = yGrille - yTrait;
        // unité de taille des textes : celle de la page A4 qui aurait des cases de cette taille
        float u = Math.min(col / 0.13429f, lig / 0.0965f);
        float finT = Math.max(1f, 0.5f * dp), ep = Math.max(1.5f, 1.1f * dp);

        String[] cases = cases(annee, mois);

        if (entete != null) rect(x0, yTrait, x1, yGrille, entete);
        for (int c = 5; c <= 6; c++) {
            rect(x0 + c * col, yTrait, x0 + (c + 1) * col, yGrille, weekendEntete);
            rect(x0 + c * col, yGrille, x0 + (c + 1) * col, yFin, weekend);
        }
        for (int k = 0; k < cases.length; k++) {
            int r = k / 7, c = k % 7;
            float bx0 = x0 + c * col, by0 = yGrille + r * lig, bx1 = bx0 + col, by1 = by0 + lig;
            if (cases[k] == null) {
                if (horsMois != null) rect(bx0, by0, bx1, by1, horsMois);
                continue;
            }
            JSONObject j = jours == null ? null : jours.optJSONObject(cases[k]);
            Integer f = couleur(j, "f");
            if (f == null && c < 5 && j != null && !j.optString("fe", "").isEmpty()) f = weekend;  // férié
            if (f != null) rect(bx0, by0, bx1, by1, f);
        }
        for (int r = 1; r < NB_LIGNES; r++) ligne(x0, yGrille + r * lig, x1, yGrille + r * lig, grille, finT);
        for (int c = 1; c < 7; c++) ligne(x0 + c * col, yGrille, x0 + c * col, yFin, grille, finT);
        for (float y : new float[]{yTrait, yGrille, yFin}) ligne(x0, y, x1, y, trait, ep);

        // noms des jours (en entier s'ils tiennent)
        float tJour = Math.min(hEntete * 0.5f, 14 * dp);
        boolean courts = false;
        for (String n : JOURS) if (larg(n, serif, tJour) > col * 0.92f) courts = true;
        String[] noms = courts ? JOURS_COURTS : JOURS;
        if (courts) for (String n : noms) while (larg(n, serif, tJour) > col * 0.92f && tJour > minPx) tJour *= 0.94f;
        for (int c = 0; c < 7; c++) {
            texte(noms[c], x0 + c * col + col / 2, yTrait + hEntete / 2, serif, tJour,
                    c >= 5 ? val(P, "entete_txt_we", titre) : val(P, "entete_txt", titre), "mm");
        }

        // titre : mois + année (la droite reste libre pour les flèches ‹ ›), message d'alerte éventuel
        float limite = W * 0.76f;
        float yTitre = yTrait - yTrait * 0.2f;
        if (message != null && !message.isEmpty()) {
            float tm = Math.min(yTrait * 0.24f, 11 * dp);
            String m = message;
            if (larg(m, sansGras, tm) > limite * 0.5f) m = "⚠ Ouvrez l'appli";
            while (larg(m, sansGras, tm) > limite * 0.5f && reel(tm * 0.95f) < reel(tm)) tm *= 0.95f;
            texte(m, limite, yTitre, sansGras, tm, Color.rgb(176, 40, 40), "rs");
            limite -= larg(m, sansGras, tm) + 8 * dp;
        }
        String nomMois = MOIS[mois - 1];
        float tTitre = Math.min(yTrait * 0.58f, 30 * dp);
        while (larg(nomMois + " " + annee, serif, tTitre) > limite - x0 && tTitre > minPx) tTitre *= 0.95f;
        texte(nomMois, x0, yTitre, serif, tTitre, titre, "ls");
        texte(String.valueOf(annee), x0 + larg(nomMois + " ", serif, tTitre), yTitre, serif, tTitre, annee_c, "ls");

        // contenu des cases
        float tNum = u * 0.02f, ascNum = asc(serif, tNum), pad = u * 0.007f;
        boolean fondSombre = lum(fond) < 140;
        for (int k = 0; k < cases.length; k++) {
            if (cases[k] == null) continue;
            int r = k / 7, c = k % 7;
            float cx0 = x0 + c * col, cy0 = yGrille + r * lig, cy1 = cy0 + lig;
            JSONObject j = jours == null ? null : jours.optJSONObject(cases[k]);
            Integer fondJ = couleur(j, "f");
            String ferie = j == null ? "" : j.optString("fe", "");
            boolean we = c >= 5 || !ferie.isEmpty();
            int fondCase = fondJ != null ? fondJ : we ? weekend : fond;
            boolean sombre = lum(fondCase) < 140;
            int TXT, DOUX, REPOS, cGros, cNum;
            if (sombre == fondSombre && fondJ == null) {
                TXT = val(P, "txt", noir);
                DOUX = val(P, "txt_doux", noir);
                REPOS = val(P, "txt_repos", noir);
                cGros = val(P, "gros", noir);
                cNum = we ? val(P, "chiffre_we", noir) : val(P, "chiffre", noir);
            } else if (sombre) {
                TXT = cGros = cNum = Color.WHITE;
                DOUX = Color.rgb(225, 225, 225);
                REPOS = Color.rgb(210, 210, 210);
            } else {
                JSONObject q = fondSombre ? cl : P;
                TXT = val(q, "txt", noir);
                DOUX = val(q, "txt_doux", noir);
                REPOS = val(q, "txt_repos", noir);
                cGros = val(q, "gros", noir);
                cNum = we ? val(q, "chiffre_we", noir) : val(q, "chiffre", noir);
            }
            float base = cy0 + pad + ascNum;
            String numTxt = String.valueOf(Integer.parseInt(cases[k].substring(8)));
            texte(numTxt, cx0 + pad, base, serif, tNum, cNum, "ls");
            float largFerie = 0;
            if (!ferie.isEmpty()) {                                    // nom du férié, en haut à droite
                float tFer = u * 0.0095f;
                // widget : la place à droite du numéro est petite → plus petit, puis raccourci « … »
                float dispo = col - 2 * pad - larg(numTxt, serif, tNum) - u * 0.006f;
                String fe = ferie;
                while (larg(fe, serif, tFer) > dispo && reel(tFer * 0.95f) < reel(tFer)) tFer *= 0.95f;
                while (larg(fe, serif, tFer) > dispo && fe.length() > 2) fe = fe.substring(0, fe.length() - 2).trim() + "…";
                texte(fe, cx0 + col - pad, base, serif, tFer, fondJ != null ? DOUX : cNum, "rs");
                largFerie = larg(fe, serif, tFer) + u * 0.006f;
            }
            if (j == null) continue;
            cv.save();
            cv.clipRect(cx0, cy0, cx0 + col, cy1);           // rien ne déborde sur la case voisine
            dessinerCase(j, cx0, cy0, cy1, col, u, base, numTxt, tNum, largFerie, TXT, DOUX, REPOS, cGros);
            cv.restore();
        }
        bordures(W, H, dp, x0, col, yGrille, lig, cases, jours, aujourdhui, titre);
    }

    private void dessinerCase(JSONObject j, float cx0, float cy0, float cy1, float col, float u, float base, String numTxt,
                              float tNum, float largFerie, int TXT, int DOUX, int REPOS, int cGros) {
        float pad = u * 0.007f, ascNum = asc(serif, tNum);
        {
            // service « spécial » : à côté du numéro, sur une ou deux lignes
            float decalage = 0;
            String special = j.optString("sp", "");
            if (!special.isEmpty()) {
                float xc = cx0 + pad + larg(numTxt, serif, tNum) + u * 0.009f;
                float largNom = cx0 + col - pad - xc - largFerie;
                boolean parDate = j.optInt("pd", 0) == 1;
                float tMin1 = u * (parDate ? 0.0085f : 0.0125f);
                float t = u * (parDate ? 0.0105f : 0.0165f);
                while (larg(special, sansGras, t) > largNom && t > tMin1) t *= 0.95f;
                String[] lignesNom = {special};
                if (larg(special, sansGras, t) > largNom && special.contains(" ")) {
                    String[] mots = special.split(" ");
                    String[] meilleur = null;
                    for (int m = 1; m < mots.length; m++) {
                        String a = join(mots, 0, m), b = join(mots, m, mots.length);
                        if (meilleur == null || Math.max(a.length(), b.length())
                                < Math.max(meilleur[0].length(), meilleur[1].length())) meilleur = new String[]{a, b};
                    }
                    lignesNom = meilleur;
                    t = u * (parDate ? 0.0105f : 0.0145f);
                }
                while (plusLarge(lignesNom, sansGras, t) > largNom && t > u * 0.007f) t *= 0.93f;
                float hNom = asc(sansGras, t) + 0.21f * reel(t) + reel(t) * 0.15f;
                float y1 = base;
                if (plusLarge(lignesNom, sansGras, t) > largNom) {
                    // widget : même au plus petit, le nom ne tient pas à côté du numéro → sous le numéro, toute la largeur
                    xc = cx0 + pad;
                    List<String> l2 = new ArrayList<>();
                    for (String s : lignesNom) l2.addAll(couper(s, sansGras, t, col - 2 * pad));
                    lignesNom = l2.toArray(new String[0]);
                    y1 = base + hNom;
                }
                for (int m = 0; m < lignesNom.length; m++) texte(lignesNom[m], xc, y1 + m * hNom, sansGras, t, TXT, "ls");
                decalage = y1 - base + (lignesNom.length - 1) * hNom;
            }

            JSONArray l = j.optJSONArray("l");
            String gros = j.optString("gr", "");
            if ((l == null || l.length() == 0) && gros.isEmpty() && special.isEmpty()) return;
            float largeur = col - 2 * pad;
            float y0 = cy0 + pad + ascNum + u * 0.010f + decalage;
            float hautDispo = cy1 - pad - y0;

            // lignes (codes, horaires, libellés) : on réduit jusqu'à ce que tout tienne ;
            // si les lignes détaillées ne tiennent pas, horaires courts à la place (« hc »)
            float taille = u * 0.0145f;
            List<Object[]> lignes;
            float hL;
            boolean compact = false;
            while (true) {
                lignes = composer(l, compact ? j.optString("hc", "") : null, taille, largeur, TXT, DOUX, REPOS);
                hL = 0.93f * reel(taille) + reel(taille) * 0.32f;
                boolean tient = lignes.size() * hL <= hautDispo;
                for (Object[] x : lignes) if (larg((String) x[0], (Typeface) x[1], (Float) x[2]) > largeur) tient = false;
                if (tient) break;
                if (taille <= u * 0.006f || reel(taille) > taille) {
                    if (compact) break;
                    compact = true;                                   // on recommence en version courte
                    taille = u * 0.0145f;
                    continue;
                }
                taille *= 0.96f;
            }
            float y = y0;
            for (Object[] x : lignes) {
                if (y + hL > cy1 - pad * 0.3f) break;
                float px = (Float) x[2];
                texte((String) x[0], cx0 + pad, y + 0.72f * reel(px), (Typeface) x[1], px, (Integer) x[3], "ls");
                y += hL;
            }

            // repos, congés… : en gros au centre de la place restante
            if (!gros.isEmpty()) {
                float hReste = cy1 - pad - y;
                float t = u * 0.05f;
                List<String> lg;
                float hLg;
                while (true) {
                    lg = new ArrayList<>();
                    for (String par : gros.split("\n")) lg.addAll(couper(par, sansGras, t, largeur));
                    hLg = asc(sansGras, t) + 0.21f * reel(t) + reel(t) * 0.2f;
                    boolean tient = lg.size() * hLg <= hReste && plusLarge(lg.toArray(new String[0]), sansGras, t) <= largeur;
                    if (tient || t <= u * 0.008f || reel(t) > t) break;
                    t *= 0.92f;
                }
                float yy = y + (hReste - lg.size() * hLg) / 2;
                for (String s : lg) {
                    if (yy + hLg > cy1 + 1) break;
                    texte(s, cx0 + col / 2, yy, sansGras, t, cGros, "mt");
                    yy += hLg;
                }
            }
        }
    }

    /** Lignes d'une case : [texte, police, taille, couleur]. horairesCourts != null : version courte. */
    private List<Object[]> composer(JSONArray l, String horairesCourts, float taille, float largeur, int TXT, int DOUX, int REPOS) {
        List<Object[]> lignes = new ArrayList<>();
        boolean hcMis = false;
        if (l != null) {
            for (int m = 0; m < l.length(); m++) {
                JSONArray x = l.optJSONArray(m);
                if (x == null) continue;
                String t = x.optString(0, ""), style = x.optString(1, "g"), role = x.optString(2, "t");
                int coul = role.equals("r") ? REPOS : role.equals("d") ? DOUX : TXT;
                if (style.equals("h")) {
                    if (horairesCourts == null) lignes.add(new Object[]{t, sansGras, taille, coul});
                    else if (!hcMis && !horairesCourts.isEmpty()) {
                        lignes.add(new Object[]{horairesCourts, sansGras, taille, coul});
                        hcMis = true;
                    }
                } else if (style.equals("n")) {
                    for (String s : couper(t, sans, taille * 0.9f, largeur)) lignes.add(new Object[]{s, sans, taille * 0.9f, coul});
                } else {
                    for (String s : couper(t, sansGras, taille, largeur)) lignes.add(new Object[]{s, sansGras, taille, coul});
                }
            }
        }
        return lignes;
    }

    private void bordures(int W, int H, float dp, float x0, float col, float yGrille, float lig, String[] cases,
                          JSONObject jours, String aujourdhui, int titre) {
        // grèves en filigrane, puis le jour J encadré
        for (int k = 0; k < cases.length; k++) {
            if (cases[k] == null || jours == null) continue;
            JSONObject j = jours.optJSONObject(cases[k]);
            if (j == null || j.optInt("g", 0) != 1) continue;
            int r = k / 7, c = k % 7;
            filigrane(x0 + c * col, yGrille + r * lig, x0 + (c + 1) * col, yGrille + (r + 1) * lig);
        }
        for (int k = 0; k < cases.length; k++) {
            if (cases[k] == null || !cases[k].equals(aujourdhui)) continue;
            int r = k / 7, c = k % 7;
            float e = 1.6f * dp;
            p.setColor(titre);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(e);
            cv.drawRect(x0 + c * col + e / 2, yGrille + r * lig + e / 2, x0 + (c + 1) * col - e / 2,
                    yGrille + (r + 1) * lig - e / 2, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void filigrane(float a, float b, float c, float d) {
        float x0 = a + 2, y0 = b + 2, w = c - a - 4, h = d - b - 4;
        if (w < 10 || h < 10) return;
        double angle = Math.atan2(h, w), diag = Math.hypot(w, h);
        float t = h * 0.5f;
        while (t > 6) {
            if (larg("GRÈVE", sansGras, t) <= diag * 0.8) break;
            t *= 0.94f;
        }
        cv.save();
        cv.translate(x0 + w / 2, y0 + h / 2);
        cv.rotate((float) -Math.toDegrees(angle));
        texte("GRÈVE", 0, 0, sansGras, t, Color.argb(90, 200, 0, 0), "mm");
        cv.restore();
    }

    private static int val(JSONObject pal, String cle, int defaut) {
        Integer c = couleur(pal, cle);
        return c == null ? defaut : c;
    }

    private static String join(String[] mots, int de, int a) {
        StringBuilder b = new StringBuilder();
        for (int k = de; k < a; k++) {
            if (k > de) b.append(' ');
            b.append(mots[k]);
        }
        return b.toString();
    }

    private float plusLarge(String[] lignes, Typeface tf, float px) {
        float m = 0;
        for (String s : lignes) m = Math.max(m, larg(s, tf, px));
        return m;
    }
}
