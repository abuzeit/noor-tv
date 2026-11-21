package app.neusstv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The screen the content is shown on. A real full-screen activity rather than an overlay window:
 * this firmware caps an app's overlay windows at 0.8 opacity, which left photos see-through.
 */
public class ShowActivity extends Activity {

    private static WeakReference<ShowActivity> open = new WeakReference<>(null);
    private FrameLayout root;
    private DrawLayer layer;
    private android.webkit.WebView web;
    private android.widget.VideoView video;

    /** Shows whatever is in {@link Content} — starting the screen if it is not up yet. */
    static void present(Context c) {
        ShowActivity a = open.get();
        if (a != null && !a.isFinishing()) {
            a.runOnUiThread(a::render);
            return;
        }
        try {
            c.startActivity(new Intent(c, ShowActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ignored) {
        }
    }

    /** One more stroke on the drawing already on screen. */
    static void addStroke(Context c, String seg) {
        ShowActivity a = open.get();
        if (a != null && !a.isFinishing() && a.layer != null) {
            a.runOnUiThread(() -> a.layer.add(seg));
        } else {
            present(c);
        }
    }

    static void close() {
        ShowActivity a = open.get();
        if (a != null && !a.isFinishing()) a.runOnUiThread(a::finish);
        open = new WeakReference<>(null);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        open = new WeakReference<>(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        render();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        open = new WeakReference<>(this);
        render();
    }

    private void render() {
        dropWeb();
        dropVideo();
        root.removeAllViews();
        layer = null;
        switch (Content.kind) {
            case WEB: {
                web = Kiosk.create(this);
                add(web);
                Kiosk.load(web, Content.url, Content.headers);
                break;
            }
            case TEXT: {
                TextView v = new TextView(this);
                v.setText(Content.text);
                v.setTextColor(Color.WHITE);
                v.setTextSize(64);
                v.setTypeface(Typeface.DEFAULT_BOLD);
                v.setGravity(Gravity.CENTER);
                v.setPadding(120, 80, 120, 80);
                add(v);
                break;
            }
            case VIDEO: {
                video = new android.widget.VideoView(this);
                video.setBackgroundColor(Color.BLACK);
                add(video);
                video.setVideoPath(Content.video.getAbsolutePath());
                video.setOnPreparedListener(mp -> {
                    mp.setLooping(Content.loop);
                    video.start();
                });
                // A file that will not play should not leave a black screen with no explanation.
                video.setOnErrorListener((mp, what, extra) -> {
                    TextView t = new TextView(this);
                    t.setText("ما قدرت أشغّل هذا الفيديو");
                    t.setTextColor(0xFFF87171);
                    t.setTextSize(40);
                    t.setGravity(Gravity.CENTER);
                    root.removeAllViews();
                    add(t);
                    return true;
                });
                break;
            }
            case IMAGE: {
                ImageView v = new ImageView(this);
                v.setImageBitmap(Content.image);
                v.setScaleType(ImageView.ScaleType.FIT_CENTER);
                add(v);
                break;
            }
            case DRAW: {
                layer = new DrawLayer(this);
                add(layer);
                DrawLayer d = layer;
                d.post(() -> {
                    for (String s : Content.strokes) d.add(s);
                });
                break;
            }
            default:
                finish();
                break;
        }
    }

    private void add(View v) {
        root.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    /** Back steps through the page's own history; at the first page it stays put — this is a kiosk. */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        if (Content.kind == Content.Kind.WEB) return;   // leave only via /clear or the Home button
        super.onBackPressed();
    }

    /** Stops playback before anything else takes the screen, so no sound carries over. */
    private void dropVideo() {
        if (video == null) return;
        android.widget.VideoView v = video;
        video = null;
        try {
            v.stopPlayback();
            root.removeView(v);
        } catch (Exception ignored) {
        }
    }

    /** Stops the page — and any sound it is playing — before anything else takes the screen. */
    private void dropWeb() {
        if (web == null) return;
        android.webkit.WebView w = web;
        web = null;
        try {
            w.loadUrl("about:blank");
            w.stopLoading();
            w.setWebChromeClient(null);
            root.removeView(w);
            w.destroy();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        dropWeb();
        dropVideo();
        if (open.get() == this) open = new WeakReference<>(null);
        super.onDestroy();
    }
}
