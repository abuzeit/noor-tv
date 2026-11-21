package app.neusstv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The "over" mode: the content floats on top of whatever is playing instead of taking the screen.
 *
 * Note the firmware caps an app's overlay windows at 0.8 opacity (dumpsys window reports alpha=0.8 on a
 * window that asks for 1.0), so anything shown this way is slightly see-through by design of the TV, not
 * of this app. That is the point here — the film stays visible underneath. For a solid black screen with
 * nothing behind it, {@link ShowActivity} is used instead.
 */
final class Overlay {
    private static View text;
    private static ImageView image;
    private static DrawLayer drawing;

    private Overlay() {
    }

    static boolean showing() {
        return text != null || image != null || drawing != null;
    }

    private static WindowManager.LayoutParams params(int size) {
        return new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
    }

    static void text(Context c, String s) {
        if (!Settings.canDrawOverlays(c)) return;
        clear(c);
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(54);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(80, 50, 80, 50);
        v.setMaxWidth(1500);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2101418);
        bg.setCornerRadius(48);
        bg.setStroke(3, 0x66FFFFFF);
        v.setBackground(bg);
        WindowManager.LayoutParams lp = params(WindowManager.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        try {
            c.getSystemService(WindowManager.class).addView(v, lp);
            text = v;
        } catch (Exception ignored) {
        }
    }

    static void image(Context c, Bitmap bm) {
        if (!Settings.canDrawOverlays(c)) return;
        clear(c);
        ImageView v = new ImageView(c);
        v.setImageBitmap(bm);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            c.getSystemService(WindowManager.class).addView(v, params(WindowManager.LayoutParams.MATCH_PARENT));
            image = v;
        } catch (Exception ignored) {
        }
    }

    /** Adds one stroke, starting the drawing layer if this is the first. */
    static void stroke(Context c, String seg, boolean fresh) {
        if (!Settings.canDrawOverlays(c)) return;
        if (fresh) {
            removeText(c);
            removeImage(c);
        }
        if (drawing == null) {
            DrawLayer d = new DrawLayer(c);
            try {
                c.getSystemService(WindowManager.class).addView(d, params(WindowManager.LayoutParams.MATCH_PARENT));
            } catch (Exception e) {
                return;
            }
            drawing = d;
        }
        DrawLayer d = drawing;
        d.post(() -> d.add(seg));   // the view has no size until its first layout
    }

    static void clear(Context c) {
        removeText(c);
        removeImage(c);
        removeDrawing(c);
    }

    private static void remove(Context c, View v) {
        try {
            c.getSystemService(WindowManager.class).removeView(v);
        } catch (Exception ignored) {
        }
    }

    private static void removeText(Context c) {
        if (text == null) return;
        remove(c, text);
        text = null;
    }

    private static void removeImage(Context c) {
        if (image == null) return;
        remove(c, image);
        image = null;
    }

    private static void removeDrawing(Context c) {
        if (drawing == null) return;
        remove(c, drawing);
        drawing = null;
    }
}
