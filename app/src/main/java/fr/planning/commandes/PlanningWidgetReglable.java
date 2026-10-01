package fr.planning.commandes;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Widget « Planning » réglable : le seul proposé dans la liste des widgets (avec « Prochain service »).
 * Réglé à la pose (WidgetReglages) : style d'origine ou GrapheneOS, horaires (automatique / toujours /
 * jamais), 7 ou 14 jours. Il s'adapte à sa taille :
 *   - horaires « automatique » : affichés seulement si le widget fait au moins LARGEUR_HEURES dp de large ;
 *   - moins de HAUTEUR_7 dp de haut : 4 lignes par page (le bouton « Suite » parcourt les jours restants).
 */
public class PlanningWidgetReglable extends WidgetBase {

    static final int LARGEUR_HEURES = 250;
    static final int HAUTEUR_7 = 190;

    /** [gos][horaires][7 lignes] → mise en page. */
    private static final int[][][] DISPOSITIONS = {
            {{R.layout.widget_reglable_orig_sh_4, R.layout.widget_reglable_orig_sh_7},
                    {R.layout.widget_reglable_orig_h_4, R.layout.widget_reglable_orig_h_7}},
            {{R.layout.widget_reglable_gos_sh_4, R.layout.widget_reglable_gos_sh_7},
                    {R.layout.widget_reglable_gos_h_4, R.layout.widget_reglable_gos_h_7}}};

    @Override
    int disposition() {
        return R.layout.widget_reglable_orig_h_7;
    }

    @Override
    int lignes() {
        return 7;
    }

    @Override
    boolean avecHeures() {
        return true;
    }

    @Override
    Reglage reglage(Context contexte, int id) {
        SharedPreferences p = prefs(contexte);
        boolean gos = p.getInt(WidgetReglages.STYLE + id, WidgetReglages.STYLE_ORIGINE) == WidgetReglages.STYLE_GOS;
        int choixHeures = p.getInt(WidgetReglages.HEURES + id, WidgetReglages.HEURES_AUTO);
        int duree = p.getInt(WidgetReglages.DUREE + id, 7) == 14 ? 14 : 7;

        // taille du widget (en dp) : largeur la plus petite et hauteur la plus grande (téléphone en portrait)
        int largeur = 0, hauteur = 0;
        try {
            Bundle o = AppWidgetManager.getInstance(contexte).getAppWidgetOptions(id);
            if (o != null) {
                largeur = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                hauteur = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            }
        } catch (RuntimeException e) {
            // taille inconnue : on garde l'affichage complet
        }
        boolean heures = choixHeures == WidgetReglages.HEURES_OUI
                || (choixHeures == WidgetReglages.HEURES_AUTO && (largeur == 0 || largeur >= LARGEUR_HEURES));
        boolean sept = hauteur == 0 || hauteur >= HAUTEUR_7;
        int lignes = sept ? 7 : 4;
        return new Reglage(DISPOSITIONS[gos ? 1 : 0][heures ? 1 : 0][sept ? 1 : 0], gos, heures, lignes, duree, true);
    }
}
