package fr.planning.commandes;

import android.content.Context;
import android.graphics.Color;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini-widget « Prochain service » (2 × 1) : le service en cours ou le prochain.
 *   1re ligne : « En cours · fin 22:35 », « Aujourd'hui · 13:28–22:35 », « Demain · 04:35–13:38 » ou « Lun 05/10 · … »
 *   2e ligne  : le service dans sa couleur (M-VAL, S-VAL…).
 * Un « service » est un jour qui a des horaires (les repos n'en ont pas), hors jours de grève.
 * Une nuit (fin avant le début) se termine le lendemain : elle reste « en cours » jusqu'à sa fin.
 * Toucher le widget ouvre ce jour dans l'application. Style réglé à la pose (WidgetReglages).
 * Mis à jour toutes les 30 minutes (updatePeriodMillis), à minuit et à chaque envoi de l'application.
 */
public class PlanningWidgetProchain extends WidgetBase {

    private static final Pattern PLAGE = Pattern.compile("(\\d{1,2}):(\\d{2})\\D+?(\\d{1,2}):(\\d{2})");
    private static final int JOURS_MAX = 62;

    @Override
    int disposition() {
        return R.layout.widget_prochain;
    }

    @Override
    int lignes() {
        return 1;
    }

    @Override
    boolean avecHeures() {
        return true;
    }

    /** {jour (0 = aujourd'hui, -1 = hier…), en cours ?, fin "hh:mm"} du prochain service, ou null. */
    static Object[] prochain(JSONObject jours, Calendar maintenant) {
        if (jours == null) return null;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        long t = maintenant.getTimeInMillis();
        for (int k = -1; k <= JOURS_MAX; k++) {
            Calendar d = (Calendar) maintenant.clone();
            d.set(Calendar.HOUR_OF_DAY, 0);
            d.set(Calendar.MINUTE, 0);
            d.set(Calendar.SECOND, 0);
            d.set(Calendar.MILLISECOND, 0);
            d.add(Calendar.DAY_OF_MONTH, k);
            JSONObject j = jours.optJSONObject(iso.format(d.getTime()));
            if (j == null || j.optInt("g", 0) == 1) continue;
            String h = j.optString("h", "");
            if (h.isEmpty()) continue;                                  // repos, congé… : pas d'horaires
            Matcher m = PLAGE.matcher(h);
            if (m.find()) {
                int debut = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
                int fin = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
                if (fin <= debut) fin += 24 * 60;                       // nuit : finit le lendemain
                long tDebut = d.getTimeInMillis() + debut * 60000L;
                long tFin = d.getTimeInMillis() + fin * 60000L;
                if (t < tFin) {
                    return new Object[]{k, t >= tDebut, m.group(3) + ":" + m.group(4), iso.format(d.getTime()), j};
                }
            } else if (k >= 0) {                                        // horaires illisibles : la journée
                return new Object[]{k, false, "", iso.format(d.getTime()), j};
            }
        }
        return null;
    }

    @Override
    RemoteViews construire(Context contexte, int idWidget) {
        boolean gos = prefs(contexte).getInt(WidgetReglages.STYLE + idWidget, WidgetReglages.STYLE_ORIGINE)
                == WidgetReglages.STYLE_GOS;
        RemoteViews vue = new RemoteViews(contexte.getPackageName(),
                gos ? R.layout.widget_prochain_gos : R.layout.widget_prochain);
        vue.setInt(R.id.w_racine, "setBackgroundResource", fond(gos, transparence(contexte, idWidget)));
        boolean sombre = sombre(contexte);
        int[] texte = couleurTexte(gos), doux = couleurDouce(gos);

        JSONObject donnees = donnees(contexte);
        JSONObject jours = donnees == null ? null : donnees.optJSONObject("jours");
        Calendar maintenant = Calendar.getInstance();
        Object[] p = prochain(jours, maintenant);
        vue.setOnClickPendingIntent(R.id.w_racine, ouvrirAppli(contexte));

        if (jours == null) {
            vue.setTextViewText(R.id.w_titre, "Prochain service");
            vue.setTextViewText(R.id.w_n0, "Ouvrez l'application");
            vue.setInt(R.id.w_n0, "setBackgroundColor", Color.TRANSPARENT);
            teinter(vue, R.id.w_n0, doux, sombre);
            return vue;
        }
        if (p == null) {
            vue.setTextViewText(R.id.w_titre, "Prochain service");
            vue.setTextViewText(R.id.w_n0, "Aucun service prévu");
            vue.setInt(R.id.w_n0, "setBackgroundColor", Color.TRANSPARENT);
            teinter(vue, R.id.w_n0, doux, sombre);
        } else {
            int k = (Integer) p[0];
            boolean enCours = (Boolean) p[1];
            String finTxt = (String) p[2], cle = (String) p[3];
            JSONObject j = (JSONObject) p[4];
            String h = j.optString("h", "");
            String quand;
            if (enCours) quand = "En cours" + (finTxt.isEmpty() ? "" : " · fin " + finTxt);
            else {
                String jourTxt;
                if (k == 0) jourTxt = "Aujourd'hui";
                else if (k == 1) jourTxt = "Demain";
                else {
                    Calendar d = (Calendar) maintenant.clone();
                    d.add(Calendar.DAY_OF_MONTH, k);
                    jourTxt = NOMS_JOURS[d.get(Calendar.DAY_OF_WEEK) - 1] + " "
                            + new SimpleDateFormat("dd/MM", Locale.FRANCE).format(d.getTime());
                }
                quand = jourTxt + (h.isEmpty() ? "" : " · " + h);
            }
            vue.setTextViewText(R.id.w_titre, quand);
            pastille(vue, R.id.w_n0, j, j.optString("n", "Service"), texte, sombre);
            vue.setOnClickPendingIntent(R.id.w_racine, ouvrirJour(contexte, cle));
        }
        String alerte = alerteAnciennete(contexte, donnees);
        if (alerte != null) vue.setTextViewText(R.id.w_titre, alerte);
        return vue;
    }
}
