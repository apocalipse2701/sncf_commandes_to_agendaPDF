package fr.planning.commandes;

/**
 * Widget « Planning sur 15 jours (GrapheneOS) » : identique à PlanningWidgetQuinzaine,
 * aux couleurs de GrapheneOS (anthracite #212121, blanc cassé #FAFAFA, bleu #0053A3).
 */
public class PlanningWidgetQuinzaineGos extends PlanningWidgetQuinzaine {

    @Override
    int disposition() {
        return R.layout.widget_quinzaine_gos;
    }

    @Override
    boolean graphene() {
        return true;
    }
}
