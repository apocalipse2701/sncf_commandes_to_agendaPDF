package fr.planning.commandes;

/**
 * Widget « Planning sur 15 jours » : deux pages que l'on change avec le bouton
 * « Suite ▶ » / « ◀ Retour ». Page 1 = aujourd'hui + 6 jours, page 2 = les 7 jours
 * suivants (7 lignes par page, comme les autres widgets). Chaque nuit, le widget revient sur la page 1. Code commun : WidgetBase.
 */
public class PlanningWidgetQuinzaine extends WidgetBase {

    @Override
    int disposition() {
        return R.layout.widget_quinzaine;
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
    boolean avecPages() {
        return true;
    }
}
