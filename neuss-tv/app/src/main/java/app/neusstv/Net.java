package app.neusstv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.net.Inet4Address;

/** The TV's own address on the home network, for the page URL shown on screen. */
final class Net {
    private Net() {
    }

    static String ip(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp != null) {
                for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof Inet4Address && !la.getAddress().isLoopbackAddress()) {
                        return la.getAddress().getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "<tv-ip>";
    }
}
