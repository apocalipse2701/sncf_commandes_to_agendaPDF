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
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Base commune des trois widgets du planning :
 *   PlanningWidget          7 jours avec horaires (le widget d'origine)
 *   PlanningWidgetSimple    7 jours sans horaires, texte plus gros
 *   PlanningWidgetQuinzaine 15 jours : deux pages de 7 jours (bouton « Suite » / « Retour »)
 * et les trois mêmes aux couleurs de GrapheneOS (classes *Gos, mises en page *_gos) :
 * toujours sombres (#212121 anthracite, #FAFAFA blanc cassé, bleu #0053A3 éclairci).
 *
 * Couleurs des textes : depuis Android 12, on donne au lanceur la couleur claire ET la
 * couleur sombre (setColorInt) : il change de lui-même avec le thème, en même temps que
 * le fond ; avant Android 12, on prend celle du thème au moment de la mise à jour.
 * Les jours sont envoyés par l'application (MainActivity.majWidget) et gardés
 * dans les préférences « widget » ; les widgets avancent tout seuls chaque jour.
 */
public abstract class WidgetBase extends AppWidgetProvider {

    static final String PREFS = "widget";
    static final String CLE = "semaine";
    static final String ACTION_PAGE = "fr.planning.commandes.WIDGET_PAGE";
    static final String ACTION_MINUIT = "fr.planning.commandes.WIDGET_MINUIT";

    private static final int[] RANGS = {R.id.w_r0, R.id.w_r1, R.id.w_r2, R.id.w_r3, R.id.w_r4, R.id.w_r5, R.id.w_r6};
    private static final int[] JOURS = {R.id.w_j0, R.id.w_j1, R.id.w_j2, R.id.w_j3, R.id.w_j4, R.id.w_j5, R.id.w_j6};
    private static final int[] NOMS = {R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5, R.id.w_n6};
    private static final int[] HEURES = {R.id.w_h0, R.id.w_h1, R.id.w_h2, R.id.w_h3, R.id.w_h4, R.id.w_h5, R.id.w_h6};
    private static final String[] NOMS_JOURS = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};

    /** Mise en page du widget. */
    abstract int disposition();

    /** Nombre de lignes de la mise en page. */
    abstract int lignes();

    /** Affiche-t-on les horaires ? */
    abstract boolean avecHeures();

    /** Widget à deux pages (15 jours) ? */
    boolean avecPages() {
        return false;
    }

    /** Couleurs GrapheneOS (anthracite / blanc cassé / bleu) au lieu des couleurs d'origine ? */
    boolean graphene() {
        return false;
    }

    /** Les six widgets, pour les mettre à jour ensemble. */
    private static WidgetBase[] tous() {
        return new WidgetBase[]{new PlanningWidget(), new PlanningWidgetSimple(), new PlanningWidgetQuinzaine(),
                new PlanningWidgetGos(), new PlanningWidgetSimpleGos(), new PlanningWidgetQuinzaineGos()};
    }

    @Override
    public void onUpdate(Context contexte, AppWidgetManager gestionnaire, int[] ids) {
        for (int id : ids) gestionnaire.updateAppWidget(id, construire(contexte, id));
        programmerMinuit(contexte);
    }

    @Override
    public void onReceive(Context contexte, Intent intention) {
        String action = intention.getAction();
        if (ACTION_PAGE.equals(action)) {
            int id = intention.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SharedPreferences prefs = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putInt("page_" + id, page(contexte, id) == 1 ? 2 : 1).apply();
                AppWidgetManager.getInstance(contexte).updateAppWidget(id, construire(contexte, id));
            }
            return;
        }
        if (ACTION_MINUIT.equals(action)) {
            // nouveau jour : les widgets à deux pages reviennent sur la première
            SharedPreferences.Editor ed = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            for (WidgetBase w : tous()) {
                if (w.avecPages()) for (int id : ids(contexte, w.getClass())) ed.remove("page_" + id);
            }
            ed.apply();
            majTous(contexte);
            return;
        }
        super.onReceive(contexte, intention);
    }

    @Override
    public void onDeleted(Context contexte, int[] ids) {
        SharedPreferences.Editor ed = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) ed.remove("page_" + id);
        ed.apply();
    }

    private static int[] ids(Context contexte, Class<?> classe) {
        int[] ids = AppWidgetManager.getInstance(contexte).getAppWidgetIds(new ComponentName(contexte, classe));
        return ids == null ? new int[0] : ids;
    }

    private static int page(Context contexte, int id) {
        return contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("page_" + id, 1);
    }

    /** Met à jour tous les widgets posés sur l'écran d'accueil (les six versions). */
    static void majTous(Context contexte) {
        AppWidgetManager gestionnaire = AppWidgetManager.getInstance(contexte);
        for (WidgetBase w : tous()) {
            for (int id : ids(contexte, w.getClass())) gestionnaire.updateAppWidget(id, w.construire(contexte, id));
        }
        programmerMinuit(contexte);
    }

    /** Demande une mise à jour juste après minuit (les widgets passent au jour suivant). */
    private static void programmerMinuit(Context contexte) {
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
        alarmes.set(AlarmManager.RTC, minuit.getTimeInMillis(), pi);
    }

    /** Couleur d'un texte {clair, sombre} : suit le thème en direct depuis Android 12, valeur du moment avant. */
    private static void teinter(RemoteViews vue, int id, int[] couleurs, boolean sombre) {
        if (Build.VERSION.SDK_INT >= 31) vue.setColorInt(id, "setTextColor", couleurs[0], couleurs[1]);
        else vue.setTextColor(id, sombre ? couleurs[1] : couleurs[0]);
    }

    private static int couleur(String hex, int defaut) {
        try {
            return hex == null || hex.isEmpty() ? defaut : Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return defaut;
        }
    }

    RemoteViews construire(Context contexte, int idWidget) {
        RemoteViews vue = new RemoteViews(contexte.getPackageName(), disposition());
        boolean sombre = (contexte.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean gos = graphene();
        // {clair, sombre} — GrapheneOS : toujours sombre ; style d'origine : suit le thème du téléphone
        int[] texte = gos ? new int[]{0xFFFAFAFA, 0xFFFAFAFA} : new int[]{0xFF18212D, 0xFFE8EDF3};
        int[] doux = gos ? new int[]{0xFFA8A8A8, 0xFFA8A8A8} : new int[]{0xFF5B6878, 0xFF9AA7B6};
        int[] rouge = gos ? new int[]{0xFFF2B8B5, 0xFFF2B8B5} : new int[]{0xFFB0463F, 0xFFF0A0A0};
        int fonceTexte = 0xFF1D2530;
        int surlignage = gos ? R.drawable.widget_gos_aujourdhui : R.drawable.widget_aujourdhui;

        JSONObject jours = null;
        try {
            String brut = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLE, null);
            if (brut != null) jours = new JSONObject(brut).optJSONObject("jours");
        } catch (Exception e) {
            jours = null;
        }

        // page 1 : aujourd'hui + 6 jours ; page 2 : les 7 jours suivants → deux semaines
        int page = avecPages() ? page(contexte, idWidget) : 1;
        int decalage = page == 2 ? lignes() : 0;

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        SimpleDateFormat court = new SimpleDateFormat("dd/MM", Locale.FRANCE);
        Calendar jour = Calendar.getInstance();
        jour.add(Calendar.DAY_OF_MONTH, decalage);
        String debut = court.format(jour.getTime());
        String fin = debut;
        boolean vide = true;

        for (int i = 0; i < lignes(); i++) {
            int js = jour.get(Calendar.DAY_OF_WEEK);
            JSONObject j = jours == null ? null : jours.optJSONObject(iso.format(jour.getTime()));
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
                int fond = couleur(j.optString("f", ""), Color.TRANSPARENT);
                vue.setTextViewText(NOMS[i], nom);
                vue.setInt(NOMS[i], "setBackgroundColor", fond);
                if (fond == Color.TRANSPARENT) teinter(vue, NOMS[i], texte, sombre);
                else vue.setTextColor(NOMS[i], j.optInt("s", 0) == 1 ? Color.WHITE : fonceTexte);
            }
            if (avecHeures()) {
                vue.setTextViewText(HEURES[i], j == null ? "" : j.optString("h", ""));
                teinter(vue, HEURES[i], doux, sombre);
            }
            vue.setInt(RANGS[i], "setBackgroundResource", i == 0 && decalage == 0 ? surlignage : 0);
            fin = court.format(jour.getTime());
            jour.add(Calendar.DAY_OF_MONTH, 1);
        }

        String titre;
        if (jours == null) titre = "Ouvrez l'application pour afficher le planning";
        else if (avecPages()) titre = "Du " + debut + " au " + fin + (vide ? " (aucune commande)" : "");
        else titre = "Planning du " + debut + " au " + fin + (vide ? " (aucune commande)" : "");
        vue.setTextViewText(R.id.w_titre, titre);

        Intent ouvrir = new Intent(contexte, MainActivity.class);
        ouvrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(contexte, 0, ouvrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        vue.setOnClickPendingIntent(R.id.w_racine, pi);

        if (avecPages()) {
            vue.setTextViewText(R.id.w_page, page == 1 ? "Suite ▶" : "◀ Retour");
            Intent tourner = new Intent(contexte, getClass());
            tourner.setAction(ACTION_PAGE);
            tourner.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, idWidget);
            PendingIntent pp = PendingIntent.getBroadcast(contexte, idWidget, tourner,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            vue.setOnClickPendingIntent(R.id.w_page, pp);
        }
        return vue;
    }
}
