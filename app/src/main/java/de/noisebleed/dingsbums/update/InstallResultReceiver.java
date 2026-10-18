package de.noisebleed.dingsbums.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import de.noisebleed.dingsbums.R;

/** Ergebnis der PackageInstaller-Session aus {@link AppUpdater#install}. */
public class InstallResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // Systemdialog "Update installieren?" — die App ist in diesem Moment im
                // Vordergrund, der Start ist daher erlaubt.
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                // Bei einem Selbst-Update kommt das praktisch nie an: Android beendet den Prozess
                // vorher.
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                // Im Systemdialog abgebrochen — kein Fehler.
                break;
            default:
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(context, context.getString(R.string.update_install_failed,
                        message != null ? message : String.valueOf(status)), Toast.LENGTH_LONG).show();
        }
    }
}
