package app.neusstv;

import android.app.Application;

/**
 * Last line of defence: if any thread in this app dies with an exception, the process is told to come
 * back in a few seconds instead of just disappearing. The server also runs on its own thread with its
 * own catch, so a bad request cannot take the app down in the first place.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Watchdog.schedule(this);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                Watchdog.soon(this);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(t, e);
            else System.exit(2);
        });
    }
}
