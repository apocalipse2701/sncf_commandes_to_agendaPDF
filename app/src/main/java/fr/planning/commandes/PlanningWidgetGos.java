package fr.planning.commandes;

/**
 * Widget « Planning de la semaine (GrapheneOS) » : identique à PlanningWidget,
 * aux couleurs de GrapheneOS (anthracite #212121, blanc cassé #FAFAFA, bleu #0053A3).
 */
public class PlanningWidgetGos extends PlanningWidget {

    @Override
    int disposition() {
        return R.layout.widget_semaine_gos;
    }

    @Override
    boolean graphene() {
        return true;
    }
}
