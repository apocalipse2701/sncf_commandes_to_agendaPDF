package fr.planning.commandes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Widget « Calendrier » : le mois en grille, dans le style de la page du PDF, aux couleurs du thème
 * du planning choisi dans l'application (Réglages → Thème du planning).
 *   - Le mois est dessiné en image (DessinCalendrier), à la taille du widget.
 *   - Par-dessus, 42 cases transparentes (res/layout/widget_calendrier.xml, mêmes proportions que le dessin) :
 *     toucher un jour l'ouvre dans l'application ; toucher le titre ouvre l'application.
 *   - ‹ › : mois précédent / suivant (du mois précédent à deux mois plus loin, les jours envoyés par l'appli).
 *     Retour au mois en cours chaque nuit (WidgetBase.nouveauJour).
 *   - Le jour J est encadré.
 * Réglé à la pose (WidgetReglages, aussi par appui long → Réglages) :
 *   - style d'origine = thème du planning choisi dans l'application (comme le PDF), ou GrapheneOS (toujours sombre) ;
 *   - durée : le mois entier (6 lignes), ou « 15 jours » = cette semaine et la suivante (widget_calendrier_15.xml,
 *     2 lignes, cases 3 fois plus hautes, textes plus grands, sans les acheminements AUTO : seulement PS / FS ;
 *     ‹ › avancent de 2 semaines).
 */
public class PlanningWidgetCalendrier extends WidgetBase {

    static final String ACTION_MOIS = "fr.planning.commandes.WIDGET_MOIS";
    static final String EXTRA_SENS = "sens";
    static final int MOIS_MIN = -1, MOIS_MAX = 2;
    /** Taille maximale du dessin (pixels) : reste sous la limite de mémoire des widgets d'Android. */
    static final int PIXELS_MAX = 700_000;

    static final int[] CASES = {
            R.id.w_c0, R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4, R.id.w_c5, R.id.w_c6,
            R.id.w_c7, R.id.w_c8, R.id.w_c9, R.id.w_c10, R.id.w_c11, R.id.w_c12, R.id.w_c13,
            R.id.w_c14, R.id.w_c15, R.id.w_c16, R.id.w_c17, R.id.w_c18, R.id.w_c19, R.id.w_c20,
            R.id.w_c21, R.id.w_c22, R.id.w_c23, R.id.w_c24, R.id.w_c25, R.id.w_c26, R.id.w_c27,
            R.id.w_c28, R.id.w_c29, R.id.w_c30, R.id.w_c31, R.id.w_c32, R.id.w_c33, R.id.w_c34,
            R.id.w_c35, R.id.w_c36, R.id.w_c37, R.id.w_c38, R.id.w_c39, R.id.w_c40, R.id.w_c41};

    @Override
    int disposition() {
        return R.layout.widget_calendrier;
    }

    @Override
    int lignes() {
        return 0;
    }

    @Override
    boolean avecHeures() {
        return false;
    }

    /** Mois affiché, par rapport au mois en cours (0 = ce mois-ci). */
    static int decalage(Context contexte, int id) {
        int d = prefs(contexte).getInt("p_" + id, 0);
        return Math.max(MOIS_MIN, Math.min(MOIS_MAX, d));
    }

    @Override
    public void onReceive(Context contexte, Intent intention) {
        if (ACTION_MOIS.equals(intention.getAction())) {
            int id = intention.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int d = Math.max(MOIS_MIN, Math.min(MOIS_MAX, decalage(contexte, id) + intention.getIntExtra(EXTRA_SENS, 0)));
                prefs(contexte).edit().putInt("p_" + id, d).apply();
                AppWidgetManager.getInstance(contexte).updateAppWidget(id, construire(contexte, id));
            }
            return;
        }
        super.onReceive(contexte, intention);
    }

    private PendingIntent changerMois(Context contexte, int id, int sens) {
        Intent i = new Intent(contexte, PlanningWidgetCalendrier.class);
        i.setAction(ACTION_MOIS);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(EXTRA_SENS, sens);
        return PendingIntent.getBroadcast(contexte, id * 2 + (sens > 0 ? 1 : 0), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** {largeur, hauteur} du widget en dp (téléphone en portrait : largeur mini, hauteur maxi). */
    static int[] taille(Context contexte, int id) {
        int largeur = 0, hauteur = 0;
        try {
            Bundle o = AppWidgetManager.getInstance(contexte).getAppWidgetOptions(id);
            if (o != null) {
                largeur = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                hauteur = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            }
        } catch (RuntimeException e) {
            // taille inconnue
        }
        return new int[]{largeur > 0 ? largeur : 320, hauteur > 0 ? hauteur : 300};
    }

    /** Réglage « durée » : 15 jours (sinon le mois entier). */
    static boolean quinzaine(Context contexte, int id) {
        return prefs(contexte).getInt(WidgetReglages.DUREE + id, 0) == 15;
    }

    /** Période affichée : mois en cours + dec mois, ou 3 semaines à partir de la semaine en cours + 2 × dec semaines. */
    static DessinCalendrier.Periode periode(Calendar auj, int dec, boolean quinzaine) {
        if (quinzaine) return DessinCalendrier.quinzaine(DessinCalendrier.lundi(auj, 2 * dec));
        Calendar m = (Calendar) auj.clone();
        m.set(Calendar.DAY_OF_MONTH, 1);
        m.add(Calendar.MONTH, dec);
        return DessinCalendrier.mois(m.get(Calendar.YEAR), m.get(Calendar.MONTH) + 1);
    }

    @Override
    RemoteViews construire(Context contexte, int idWidget) {
        boolean q = quinzaine(contexte, idWidget);
        RemoteViews vue = new RemoteViews(contexte.getPackageName(), q ? R.layout.widget_calendrier_15 : R.layout.widget_calendrier);
        JSONObject donnees = donnees(contexte);
        JSONObject cal = donnees == null ? null : donnees.optJSONObject("cal");

        int dec = decalage(contexte, idWidget);
        Calendar auj = Calendar.getInstance();
        DessinCalendrier.Periode v = periode(auj, dec, q);

        String message;
        if (cal == null) message = "Ouvrez l'application";
        else message = alerteAnciennete(contexte, donnees);

        int[] t = taille(contexte, idWidget);
        float densite = contexte.getResources().getDisplayMetrics().density;
        float echelle = (float) Math.min(densite, Math.sqrt(PIXELS_MAX / ((double) t[0] * t[1])));
        echelle = Math.max(1f, echelle);
        int W = Math.round(t[0] * echelle), H = Math.round(t[1] * echelle);
        boolean gos = prefs(contexte).getInt(WidgetReglages.STYLE + idWidget, WidgetReglages.STYLE_ORIGINE)
                == WidgetReglages.STYLE_GOS;
        Bitmap image = DessinCalendrier.dessiner(W, H, echelle, v, cal, DessinCalendrier.iso(auj), message, gos);
        vue.setImageViewBitmap(R.id.w_image, image);

        String[] cases = v.cases;
        for (int k = 0; k < cases.length; k++) {
            if (cases[k] != null) vue.setOnClickPendingIntent(CASES[k], ouvrirJour(contexte, cases[k]));
        }
        vue.setOnClickPendingIntent(R.id.w_titre_zone, ouvrirAppli(contexte));

        int fleches = DessinCalendrier.couleurTitre(cal, gos);
        vue.setTextColor(R.id.w_prec, fleches);
        vue.setTextColor(R.id.w_suiv, fleches);
        vue.setViewVisibility(R.id.w_prec, dec > MOIS_MIN ? View.VISIBLE : View.INVISIBLE);
        vue.setViewVisibility(R.id.w_suiv, dec < MOIS_MAX ? View.VISIBLE : View.INVISIBLE);
        vue.setOnClickPendingIntent(R.id.w_prec, changerMois(contexte, idWidget, -1));
        vue.setOnClickPendingIntent(R.id.w_suiv, changerMois(contexte, idWidget, 1));
        vue.setContentDescription(R.id.w_image, v.description());
        return vue;
    }
}
