package fr.planning.commandes;

/**
 * Widget « Planning de la semaine (sans horaires) (GrapheneOS) » : identique à PlanningWidgetSimple,
 * aux couleurs de GrapheneOS (anthracite #212121, blanc cassé #FAFAFA, bleu #0053A3).
 */
public class PlanningWidgetSimpleGos extends PlanningWidgetSimple {

    @Override
    int disposition() {
        return R.layout.widget_semaine_simple_gos;
    }

    @Override
    boolean graphene() {
        return true;
    }
}
