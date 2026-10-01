package fr.planning.commandes;

/**
 * Widget « Planning de la semaine » : aujourd'hui et les 6 jours suivants,
 * avec les horaires (le widget d'origine). Le code commun est dans WidgetBase.
 */
public class PlanningWidget extends WidgetBase {

    @Override
    int disposition() {
        return R.layout.widget_semaine;
    }

    @Override
    int lignes() {
        return 7;
    }

    @Override
    boolean avecHeures() {
        return true;
    }
}
