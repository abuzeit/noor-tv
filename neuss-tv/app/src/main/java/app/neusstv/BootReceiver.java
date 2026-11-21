package app.neusstv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Brings the server back after a reboot, an update of this app, or a watchdog poke. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        MessageServer.start(c);
        Watchdog.schedule(c);   // alarms are dropped on reboot; set them again every time we run
    }
}
