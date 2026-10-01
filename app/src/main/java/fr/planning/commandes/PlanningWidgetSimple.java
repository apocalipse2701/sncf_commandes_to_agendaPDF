package fr.planning.commandes;

/**
 * Widget « Planning de la semaine (sans horaires) » : aujourd'hui et les 6 jours
 * suivants, seulement le jour et le service, en plus gros. Code commun : WidgetBase.
 */
public class PlanningWidgetSimple extends WidgetBase {

    @Override
    int disposition() {
        return R.layout.widget_semaine_simple;
    }

    @Override
    int lignes() {
        return 7;
    }

    @Override
    boolean avecHeures() {
        return false;
    }
}
