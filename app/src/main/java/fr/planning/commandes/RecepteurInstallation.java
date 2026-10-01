package fr.planning.commandes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/**
 * Réponse d'Android à l'installation d'une mise à jour (MiseAJour.installer) :
 * il faut d'abord montrer l'écran de confirmation d'Android, puis on signale un éventuel échec.
 * Si l'installation réussit, l'application est remplacée et redémarrée par Android.
 */
public class RecepteurInstallation extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context contexte, Intent intention) {
        int etat = intention.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (etat == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmer = intention.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmer != null) {
                confirmer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    contexte.startActivity(confirmer);
                } catch (RuntimeException e) {
                    Toast.makeText(contexte, "Mise à jour : écran de confirmation impossible à ouvrir.", Toast.LENGTH_LONG).show();
                }
            }
        } else if (etat != PackageInstaller.STATUS_SUCCESS) {
            String m = intention.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String texte = etat == PackageInstaller.STATUS_FAILURE_ABORTED ? "Mise à jour annulée."
                    : etat == PackageInstaller.STATUS_FAILURE_CONFLICT || etat == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                    ? "Mise à jour refusée par Android (signature différente ?)."
                    : "Mise à jour impossible" + (m != null && !m.isEmpty() ? " : " + m : ".");
            Toast.makeText(contexte, texte, Toast.LENGTH_LONG).show();
        }
    }
}
