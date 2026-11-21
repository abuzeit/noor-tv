package app.neusstv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Opening the app starts the server and shows the address to type on a phone. Starting a foreground
 * service is always allowed from here, which is why the tile exists at all.
 */
public class MainActivity extends Activity {

    /** The open address screen, so the first message can take the screen from it. */
    private static java.lang.ref.WeakReference<MainActivity> open = new java.lang.ref.WeakReference<>(null);

    /** Called when content arrives: the address is for setting up, not for watching. */
    static void close() {
        MainActivity a = open.get();
        if (a != null && !a.isFinishing()) a.runOnUiThread(a::finish);
        open = new java.lang.ref.WeakReference<>(null);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        open = new java.lang.ref.WeakReference<>(this);
        MessageServer.start(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0D1117);
        root.setPadding(80, 80, 80, 80);

        root.addView(text("Neuss TV", 34, 0xFF8B949E));
        root.addView(text("http://" + Net.ip(this) + ":" + MessageServer.PORT, 58, Color.WHITE));
        root.addView(text("افتح هالرابط من جوالك وانت على نفس الواي فاي،\nواكتب أو ارسم أو ابعت صورة وبتطلع هون.",
                26, 0xFF8B949E));

        if (!Settings.canDrawOverlays(this)) {
            root.addView(text("ناقص إذن العرض فوق التطبيقات — بدون هالإذن ما بتطلع الرسائل.", 24, 0xFFF87171));
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        if (open.get() == this) open = new java.lang.ref.WeakReference<>(null);
        super.onDestroy();
    }

    private TextView text(String s, int size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, 18, 0, 18);
        if (size > 40) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }
}
