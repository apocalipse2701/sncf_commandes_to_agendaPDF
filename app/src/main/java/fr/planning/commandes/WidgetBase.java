package fr.planning.commandes;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Base commune des widgets du planning.
 *
 * Widget proposé dans la liste des widgets :
 *   PlanningWidgetReglable  « Planning » : un seul widget réglé à la pose (style, horaires, 7 ou 14 jours),
 *                           qui s'adapte à sa taille (horaires masqués s'il est étroit, 4 lignes s'il est bas)
 *   PlanningWidgetProchain  « Prochain service » (mini-widget 2 × 1)
 *   PlanningWidgetCalendrier « Calendrier du mois » : le mois en grille, style de la page du PDF (DessinCalendrier)
 * Anciens widgets, masqués de la liste (Android 12+) mais qui continuent de marcher là où ils sont posés :
 *   PlanningWidget, PlanningWidgetSimple, PlanningWidgetQuinzaine et leurs versions GrapheneOS (*Gos).
 *
 * Couleurs des textes : depuis Android 12, on donne au lanceur la couleur claire ET la couleur sombre
 * (setColorInt) : il change de lui-même avec le thème. Style GrapheneOS : toujours sombre.
 * Les jours sont envoyés par l'application (MainActivity.majWidget, avec la date d'envoi « du ») et
 * gardés dans les préférences « widget » ; les widgets avancent tout seuls chaque jour (alarme après
 * minuit, qui passe même en veille, + WidgetReveil au redémarrage / changement d'heure / mise à jour).
 */
public abstract class WidgetBase extends AppWidgetProvider {

    static final String PREFS = "widget";
    static final String CLE = "semaine";
    static final String CLE_MAJ = "maj_le";               // heure du dernier envoi par l'appli (secours)
    static final String ACTION_PAGE = "fr.planning.commandes.WIDGET_PAGE";
    static final String ACTION_MINUIT = "fr.planning.commandes.WIDGET_MINUIT";
    static final String ACTION_JOUR = "fr.planning.commandes.OUVRIR_JOUR";
    static final String EXTRA_JOUR = "jour";
    /** Au-delà, le titre prévient que les données sont anciennes. */
    static final int JOURS_AVANT_ALERTE = 21;

    static final int[] RANGS = {R.id.w_r0, R.id.w_r1, R.id.w_r2, R.id.w_r3, R.id.w_r4, R.id.w_r5, R.id.w_r6};
    static final int[] JOURS = {R.id.w_j0, R.id.w_j1, R.id.w_j2, R.id.w_j3, R.id.w_j4, R.id.w_j5, R.id.w_j6};
    static final int[] NOMS = {R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5, R.id.w_n6};
    static final int[] HEURES = {R.id.w_h0, R.id.w_h1, R.id.w_h2, R.id.w_h3, R.id.w_h4, R.id.w_h5, R.id.w_h6};
    static final String[] NOMS_JOURS = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};

    /** Ce qu'un widget affiche : mise en page, style, horaires, lignes par page, nombre de jours. */
    static final class Reglage {
        final int disposition;
        final boolean gos, heures, bouton;
        final int lignes, duree;

        Reglage(int disposition, boolean gos, boolean heures, int lignes, int duree, boolean bouton) {
            this.disposition = disposition;
            this.gos = gos;
            this.heures = heures;
            this.lignes = lignes;
            this.duree = duree;
            this.bouton = bouton;
        }
    }

    // ---- réglages des anciens widgets (fixes) ----

    /** Mise en page du widget. */
    abstract int disposition();

    /** Nombre de lignes de la mise en page. */
    abstract int lignes();

    /** Affiche-t-on les horaires ? */
    abstract boolean avecHeures();

    /** Widget à deux pages (14 jours) ? */
    boolean avecPages() {
        return false;
    }

    /** Couleurs GrapheneOS (anthracite / blanc cassé / bleu) au lieu des couleurs d'origine ? */
    boolean graphene() {
        return false;
    }

    /** Réglage d'un widget posé ; le widget réglable le lit dans ses préférences et selon sa taille. */
    Reglage reglage(Context contexte, int idWidget) {
        return new Reglage(disposition(), graphene(), avecHeures(), lignes(), avecPages() ? 2 * lignes() : lignes(), avecPages());
    }

    /** Tous les widgets, pour les mettre à jour ensemble. */
    static WidgetBase[] tous() {
        return new WidgetBase[]{new PlanningWidgetReglable(), new PlanningWidgetProchain(), new PlanningWidgetCalendrier(),
                new PlanningWidget(), new PlanningWidgetSimple(), new PlanningWidgetQuinzaine(),
                new PlanningWidgetGos(), new PlanningWidgetSimpleGos(), new PlanningWidgetQuinzaineGos()};
    }

    @Override
    public void onUpdate(Context contexte, AppWidgetManager gestionnaire, int[] ids) {
        for (int id : ids) gestionnaire.updateAppWidget(id, construire(contexte, id));
        programmerMinuit(contexte);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context contexte, AppWidgetManager gestionnaire, int id, Bundle options) {
        gestionnaire.updateAppWidget(id, construire(contexte, id));       // taille changée
    }

    @Override
    public void onReceive(Context contexte, Intent intention) {
        String action = intention.getAction();
        if (ACTION_PAGE.equals(action)) {
            int id = intention.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                prefs(contexte).edit().putInt("p_" + id, page(contexte, id) + 1).apply();   // ramené au nombre de pages
                AppWidgetManager.getInstance(contexte).updateAppWidget(id, construire(contexte, id));
            }
            return;
        }
        if (ACTION_MINUIT.equals(action)) {
            nouveauJour(contexte);
            return;
        }
        super.onReceive(contexte, intention);
    }

    @Override
    public void onDeleted(Context contexte, int[] ids) {
        SharedPreferences.Editor ed = prefs(contexte).edit();
        for (int id : ids) {
            ed.remove("p_" + id);
            ed.remove("page_" + id);
            for (String cle : WidgetReglages.CLES) ed.remove(cle + id);
        }
        ed.apply();
    }

    static SharedPreferences prefs(Context contexte) {
        return contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int[] ids(Context contexte, Class<?> classe) {
        int[] ids = AppWidgetManager.getInstance(contexte).getAppWidgetIds(new ComponentName(contexte, classe));
        return ids == null ? new int[0] : ids;
    }

    private static int page(Context contexte, int id) {
        return Math.max(0, prefs(contexte).getInt("p_" + id, 0));
    }

    /** Nouveau jour (alarme, redémarrage, changement d'heure) : retour en page 1 et nouvel affichage. */
    static void nouveauJour(Context contexte) {
        SharedPreferences.Editor ed = prefs(contexte).edit();
        for (WidgetBase w : tous()) for (int id : ids(contexte, w.getClass())) ed.remove("p_" + id);
        ed.apply();
        majTous(contexte);
    }

    /** Met à jour tous les widgets posés sur l'écran d'accueil. */
    static void majTous(Context contexte) {
        AppWidgetManager gestionnaire = AppWidgetManager.getInstance(contexte);
        for (WidgetBase w : tous()) {
            for (int id : ids(contexte, w.getClass())) gestionnaire.updateAppWidget(id, w.construire(contexte, id));
        }
        programmerMinuit(contexte);
    }

    /** Mise à jour juste après minuit, même si le téléphone est en veille (sans permission spéciale). */
    static void programmerMinuit(Context contexte) {
        AlarmManager alarmes = (AlarmManager) contexte.getSystemService(Context.ALARM_SERVICE);
        if (alarmes == null) return;
        Intent intention = new Intent(contexte, PlanningWidget.class);
        intention.setAction(ACTION_MINUIT);
        PendingIntent pi = PendingIntent.getBroadcast(contexte, 1, intention,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar minuit = Calendar.getInstance();
        minuit.add(Calendar.DAY_OF_MONTH, 1);
        minuit.set(Calendar.HOUR_OF_DAY, 0);
        minuit.set(Calendar.MINUTE, 1);
        minuit.set(Calendar.SECOND, 0);
        alarmes.setAndAllowWhileIdle(AlarmManager.RTC, minuit.getTimeInMillis(), pi);
    }

    // ---- outils partagés avec le mini-widget ----

    static boolean sombre(Context contexte) {
        return (contexte.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** {clair, sombre} — GrapheneOS : toujours sombre ; style d'origine : suit le thème du téléphone. */
    static int[] couleurTexte(boolean gos) {
        return gos ? new int[]{0xFFFAFAFA, 0xFFFAFAFA} : new int[]{0xFF18212D, 0xFFE8EDF3};
    }

    static int[] couleurDouce(boolean gos) {
        return gos ? new int[]{0xFFA8A8A8, 0xFFA8A8A8} : new int[]{0xFF5B6878, 0xFF9AA7B6};
    }

    static int[] couleurRouge(boolean gos) {
        return gos ? new int[]{0xFFF2B8B5, 0xFFF2B8B5} : new int[]{0xFFB0463F, 0xFFF0A0A0};
    }

    /** Couleur d'un texte {clair, sombre} : suit le thème en direct depuis Android 12, valeur du moment avant. */
    static void teinter(RemoteViews vue, int id, int[] couleurs, boolean sombre) {
        if (Build.VERSION.SDK_INT >= 31) vue.setColorInt(id, "setTextColor", couleurs[0], couleurs[1]);
        else vue.setTextColor(id, sombre ? couleurs[1] : couleurs[0]);
    }

    static int couleur(String hex, int defaut) {
        try {
            return hex == null || hex.isEmpty() ? defaut : Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return defaut;
        }
    }

    /** Pastille du service : fond de la couleur du code, texte lisible dessus. */
    static void pastille(RemoteViews vue, int id, JSONObject j, String nom, int[] texte, boolean sombre) {
        int fond = couleur(j.optString("f", ""), Color.TRANSPARENT);
        vue.setTextViewText(id, nom);
        vue.setInt(id, "setBackgroundColor", fond);
        if (fond == Color.TRANSPARENT) teinter(vue, id, texte, sombre);
        else vue.setTextColor(id, j.optInt("s", 0) == 1 ? Color.WHITE : 0xFF1D2530);
    }

    /** Données envoyées par l'application : {"du": "aaaa-mm-jj", "jours": {...}} (null si rien). */
    static JSONObject donnees(Context contexte) {
        try {
            String brut = prefs(contexte).getString(CLE, null);
            return brut == null ? null : new JSONObject(brut);
        } catch (Exception e) {
            return null;
        }
    }

    /** Date du dernier envoi par l'application (champ « du », sinon heure enregistrée), ou null. */
    static Date dateEnvoi(Context contexte, JSONObject donnees) {
        String du = donnees == null ? "" : donnees.optString("du", "");
        if (!du.isEmpty()) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(du);
            } catch (ParseException e) {
                // format inattendu : on prend l'heure enregistrée
            }
        }
        long t = prefs(contexte).getLong(CLE_MAJ, 0);
        return t > 0 ? new Date(t) : null;
    }

    /** Texte d'alerte si les données ont plus de JOURS_AVANT_ALERTE jours, sinon null. */
    static String alerteAnciennete(Context contexte, JSONObject donnees) {
        Date envoi = dateEnvoi(contexte, donnees);
        if (envoi == null) return null;
        long jours = (System.currentTimeMillis() - envoi.getTime()) / 86400000L;
        if (jours < JOURS_AVANT_ALERTE) return null;
        return "⚠ Données du " + new SimpleDateFormat("dd/MM", Locale.FRANCE).format(envoi) + " : ouvrez l'appli";
    }

    /** Toucher : ouvre l'application sur ce jour (détail du jour). */
    static PendingIntent ouvrirJour(Context contexte, String iso) {
        Intent i = new Intent(contexte, MainActivity.class);
        i.setAction(ACTION_JOUR);
        i.setData(Uri.parse("planning-commandes://jour/" + iso));     // un PendingIntent distinct par jour
        i.putExtra(EXTRA_JOUR, iso);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(contexte, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent ouvrirAppli(Context contexte) {
        Intent ouvrir = new Intent(contexte, MainActivity.class);
        ouvrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(contexte, 0, ouvrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---- dessin d'un widget « liste de jours » ----

    RemoteViews construire(Context contexte, int idWidget) {
        Reglage r = reglage(contexte, idWidget);
        RemoteViews vue = new RemoteViews(contexte.getPackageName(), r.disposition);
        boolean sombre = sombre(contexte);
        int[] texte = couleurTexte(r.gos), doux = couleurDouce(r.gos), rouge = couleurRouge(r.gos);
        int surlignage = r.gos ? R.drawable.widget_gos_aujourdhui : R.drawable.widget_aujourdhui;

        JSONObject donnees = donnees(contexte);
        JSONObject jours = donnees == null ? null : donnees.optJSONObject("jours");

        // pages de r.lignes jours, jusqu'à r.duree jours en tout (la dernière page peut être incomplète)
        int pages = Math.max(1, (r.duree + r.lignes - 1) / r.lignes);
        int page = page(contexte, idWidget) % pages;
        int decalage = page * r.lignes;
        int nb = Math.min(r.lignes, r.duree - decalage);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        SimpleDateFormat court = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        Calendar jour = Calendar.getInstance();
        jour.add(Calendar.DAY_OF_MONTH, decalage);
        String debut = court.format(jour.getTime());
        String fin = debut;
        boolean vide = true;

        for (int i = 0; i < r.lignes; i++) {
            if (i >= nb) {
                vue.setViewVisibility(RANGS[i], View.INVISIBLE);       // garde la hauteur des lignes
                continue;
            }
            vue.setViewVisibility(RANGS[i], View.VISIBLE);
            String cle = iso.format(jour.getTime());
            int js = jour.get(Calendar.DAY_OF_WEEK);
            JSONObject j = jours == null ? null : jours.optJSONObject(cle);
            boolean ferie = j != null && !j.optString("fe", "").isEmpty();
            boolean weekEnd = js == Calendar.SATURDAY || js == Calendar.SUNDAY;

            vue.setTextViewText(JOURS[i], NOMS_JOURS[js - 1] + " " + jour.get(Calendar.DAY_OF_MONTH));
            teinter(vue, JOURS[i], weekEnd || ferie ? rouge : texte, sombre);

            String nom = j == null ? "" : j.optString("n", "");
            if (j != null && j.optInt("g", 0) == 1) nom = nom.isEmpty() ? "GRÈVE" : nom + " · grève";
            if (nom.isEmpty() && ferie) nom = j.optString("fe", "");

            if (nom.isEmpty()) {
                vue.setTextViewText(NOMS[i], "—");
                teinter(vue, NOMS[i], doux, sombre);
                vue.setInt(NOMS[i], "setBackgroundColor", Color.TRANSPARENT);
            } else {
                vide = false;
                pastille(vue, NOMS[i], j, nom, texte, sombre);
            }
            if (r.heures) {
                vue.setTextViewText(HEURES[i], j == null ? "" : j.optString("h", ""));
                teinter(vue, HEURES[i], doux, sombre);
            }
            vue.setInt(RANGS[i], "setBackgroundResource", i == 0 && decalage == 0 ? surlignage : 0);
            vue.setOnClickPendingIntent(RANGS[i], ouvrirJour(contexte, cle));
            fin = court.format(jour.getTime());
            jour.add(Calendar.DAY_OF_MONTH, 1);
        }

        String alerte = alerteAnciennete(contexte, donnees);
        String titre;
        if (jours == null) titre = "Ouvrez l'application pour afficher le planning";
        else if (alerte != null) titre = alerte;
        else titre = (pages > 1 ? "Du " : "Planning du ") + debut + " au " + fin + (vide ? " (aucune commande)" : "");
        vue.setTextViewText(R.id.w_titre, titre);
        vue.setOnClickPendingIntent(R.id.w_racine, ouvrirAppli(contexte));

        if (r.bouton) {
            if (pages > 1) {
                vue.setViewVisibility(R.id.w_page, View.VISIBLE);
                vue.setTextViewText(R.id.w_page, page < pages - 1 ? "Suite ▶" : "◀ Retour");
                Intent tourner = new Intent(contexte, getClass());
                tourner.setAction(ACTION_PAGE);
                tourner.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, idWidget);
                PendingIntent pp = PendingIntent.getBroadcast(contexte, idWidget, tourner,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                vue.setOnClickPendingIntent(R.id.w_page, pp);
            } else {
                vue.setViewVisibility(R.id.w_page, View.GONE);
            }
        }
        return vue;
    }
}
