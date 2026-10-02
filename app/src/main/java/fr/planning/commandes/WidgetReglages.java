package fr.planning.commandes;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Écran de réglage d'un widget, ouvert à la pose (et par appui long → « Réglages » sur Android 12+) :
 *   - Style : d'origine (suit le thème du téléphone ; pour le calendrier, le thème du planning) ou GrapheneOS (toujours sombre) ;
 *   - Horaires : automatique (selon la largeur), toujours, jamais    } widget « Planning »
 *   - Durée : 7 jours ou 14 jours (2 pages)                           } seulement
 *   Tous : transparence du fond (0, 25, 50, 75 %, ou fond invisible).
 *   Calendrier : style + durée (le mois entier ou « 15 jours » = 2 semaines, sans les acheminements).
 * Les choix sont gardés dans les préférences « widget » (clés + numéro du widget).
 */
public class WidgetReglages extends Activity {

    static final String STYLE = "cfg_style_", HEURES = "cfg_heures_", DUREE = "cfg_duree_", TRANSPARENCE = "cfg_transp_";
    static final String[] CLES = {STYLE, HEURES, DUREE, TRANSPARENCE};
    /** Transparence du fond, en % (0 = opaque, 100 = fond invisible). */
    static final int[] TRANSPARENCES = {0, 25, 50, 75, 100};
    static final int STYLE_ORIGINE = 0, STYLE_GOS = 1;
    static final int HEURES_AUTO = 0, HEURES_OUI = 1, HEURES_NON = 2;

    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        setResult(RESULT_CANCELED);                       // retour sans valider : le widget n'est pas posé
        Bundle extras = getIntent() == null ? null : getIntent().getExtras();
        if (extras != null) id = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        String classe = info == null || info.provider == null ? "" : info.provider.getClassName();
        final boolean calendrier = classe.endsWith("PlanningWidgetCalendrier");
        final boolean planning = !calendrier && !classe.endsWith("PlanningWidgetProchain");
        SharedPreferences p = WidgetBase.prefs(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int m = dp(20);
        col.setPadding(m, m, m, m);
        titre(col, calendrier ? "Widget « Calendrier du mois »" : planning ? "Widget « Planning »" : "Widget « Prochain service »", 20);

        final RadioGroup style = groupe(col, "Style",
                calendrier ? new String[]{"D'origine : comme le PDF, au thème du planning choisi dans l'application",
                        "GrapheneOS (toujours sombre)"}
                        : new String[]{"D'origine (suit le thème clair ou sombre du téléphone)", "GrapheneOS (toujours sombre)"},
                p.getInt(STYLE + id, STYLE_ORIGINE));
        final RadioGroup heures = planning ? groupe(col, "Horaires",
                new String[]{"Automatique : affichés si le widget est assez large", "Toujours", "Jamais"},
                p.getInt(HEURES + id, HEURES_AUTO)) : null;
        final RadioGroup duree = planning ? groupe(col, "Durée",
                new String[]{"7 jours", "14 jours (2 pages, bouton « Suite »)"},
                p.getInt(DUREE + id, 7) == 14 ? 1 : 0)
                : calendrier ? groupe(col, "Durée",
                new String[]{"Le mois entier", "15 jours : cette semaine et la suivante (2 lignes, sans les acheminements)"},
                p.getInt(DUREE + id, 0) == 15 ? 1 : 0) : null;
        int tActuelle = p.getInt(TRANSPARENCE + id, 0), iT = 0;
        for (int i = 0; i < TRANSPARENCES.length; i++) if (TRANSPARENCES[i] == tActuelle) iT = i;
        final RadioGroup transparence = groupe(col, "Transparence du fond",
                new String[]{"Aucune (fond plein)", "25 %", "50 %", "75 %", "Totale (fond invisible ; sur le Calendrier, seuls les textes restent)"},
                iT);
        if (planning) {
            TextView aide = new TextView(this);
            aide.setText("Si le widget est peu haut, il affiche 4 jours par page. Toucher un jour l'ouvre dans l'application.");
            aide.setAlpha(0.7f);
            aide.setPadding(0, dp(12), 0, 0);
            col.addView(aide);
        }
        if (calendrier) {
            TextView aide = new TextView(this);
            aide.setText("‹ › changent de mois (de 2 semaines en « 15 jours »). Toucher un jour l'ouvre dans l'application.\n"
                    + "En « 15 jours », le widget peut être réduit à 2 cases de haut (appui long, puis tirer la poignée).");
            aide.setAlpha(0.7f);
            aide.setPadding(0, dp(12), 0, 0);
            col.addView(aide);
        }

        Button ok = new Button(this);
        ok.setText("Enregistrer");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        col.addView(ok, lp);
        ok.setOnClickListener(v -> {
            SharedPreferences.Editor ed = WidgetBase.prefs(this).edit();
            ed.putInt(STYLE + id, indice(style));
            if (heures != null) ed.putInt(HEURES + id, indice(heures));
            ed.putInt(TRANSPARENCE + id, TRANSPARENCES[indice(transparence)]);
            if (duree != null) ed.putInt(DUREE + id, calendrier ? (indice(duree) == 1 ? 15 : 0) : indice(duree) == 1 ? 14 : 7);
            ed.remove("p_" + id);
            ed.apply();
            WidgetBase.majTous(this);
            Intent resultat = new Intent();
            resultat.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            setResult(RESULT_OK, resultat);
            finish();
        });

        ScrollView defile = new ScrollView(this);
        defile.addView(col);
        setContentView(defile);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void titre(LinearLayout col, String texte, int taille) {
        TextView t = new TextView(this);
        t.setText(texte);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, taille);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(taille >= 20 ? 0 : 16), 0, dp(6));
        col.addView(t);
    }

    private RadioGroup groupe(LinearLayout col, String nom, String[] choix, int actuel) {
        titre(col, nom, 16);
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < choix.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(choix[i]);
            b.setTag(i);
            g.addView(b);
            if (i == actuel) b.setChecked(true);
        }
        col.addView(g);
        return g;
    }

    private static int indice(RadioGroup g) {
        View b = g.findViewById(g.getCheckedRadioButtonId());
        return b != null && b.getTag() instanceof Integer ? (Integer) b.getTag() : 0;
    }
}
