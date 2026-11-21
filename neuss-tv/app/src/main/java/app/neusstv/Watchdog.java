package app.neusstv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Keeps the server alive without anyone watching it.
 *
 * A repeating alarm pokes {@link BootReceiver} every few minutes; starting a service that is already
 * running is a no-op, so this costs nothing while all is well and brings the server back when it is not.
 * The alarm is inexact on purpose — it needs no exact-alarm permission, which is what crashed the Kahf
 * app, and a few minutes either way does not matter for a restart.
 */
final class Watchdog {
    static final String KEEPALIVE = "app.neusstv.KEEPALIVE";
    private static final long EVERY = 5 * 60 * 1000L;

    private Watchdog() {
    }

    static void schedule(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + EVERY, EVERY, pending(c));
        } catch (Exception ignored) {
        }
    }

    /** After a crash: come back in a few seconds rather than waiting for the next round. */
    static void soon(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 3000, pending(c));
        } catch (Exception ignored) {
        }
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, BootReceiver.class).setAction(KEEPALIVE);
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
