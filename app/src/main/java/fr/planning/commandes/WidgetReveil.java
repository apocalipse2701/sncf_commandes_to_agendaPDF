package fr.planning.commandes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Redessine les widgets quand l'alarme de minuit a pu être perdue ou faussée :
 * redémarrage du téléphone (les alarmes sont effacées), changement d'heure ou de fuseau,
 * mise à jour de l'application. Reprogramme aussi l'alarme de minuit.
 */
public class WidgetReveil extends BroadcastReceiver {

    @Override
    public void onReceive(Context contexte, Intent intention) {
        WidgetBase.nouveauJour(contexte);
    }
}
