package app.neusstv;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the TV is showing right now. One thing at a time: a text, an image or a drawing, each
 * replacing the one before it, and staying until the next send or a /clear.
 *
 * {@link #over} is how it is shown: false takes the whole screen on black ({@link ShowActivity}),
 * true floats it on top of whatever is playing ({@link Overlay}).
 */
final class Content {
    enum Kind { NONE, TEXT, IMAGE, DRAW, WEB, VIDEO }

    static Kind kind = Kind.NONE;
    static boolean over;
    static String text;
    static String url;
    /** Extra request headers for the kiosk — an API token, a session cookie — set when the page is opened. */
    static final Map<String, String> headers = new LinkedHashMap<>();
    static Bitmap image;
    static java.io.File video;
    static boolean loop = true;
    static final List<String> strokes = new ArrayList<>();

    private Content() {
    }

    static void text(String t, boolean onTop) {
        kind = Kind.TEXT;
        over = onTop;
        video = null;
        text = t;
        url = null;
        image = null;
        strokes.clear();
    }

    /** A web page, shown full screen with no browser chrome. Always takes the screen. */
    /** A video file in the app's own cache, played full screen. */
    static void video(java.io.File f, boolean looping) {
        kind = Kind.VIDEO;
        over = false;
        video = f;
        loop = looping;
        text = null;
        url = null;
        image = null;
        strokes.clear();
    }

    static void web(String u) {
        kind = Kind.WEB;
        over = false;
        video = null;
        url = u;
        text = null;
        image = null;
        strokes.clear();
    }

    static void image(Bitmap b, boolean onTop) {
        kind = Kind.IMAGE;
        over = onTop;
        video = null;
        image = b;
        text = null;
        url = null;
        strokes.clear();
    }

    /** True when this stroke starts a new drawing — a different kind before it, or a different mode. */
    static boolean stroke(String s, boolean onTop) {
        boolean fresh = kind != Kind.DRAW || over != onTop;
        if (fresh) {
            kind = Kind.DRAW;
            over = onTop;
            video = null;
            text = null;
            url = null;
            image = null;
            strokes.clear();
        }
        strokes.add(s);
        return fresh;
    }

    static void clear() {
        kind = Kind.NONE;
        over = false;
        video = null;
        text = null;
        url = null;
        image = null;
        strokes.clear();
    }

    static String name() {
        switch (kind) {
            case TEXT: return "text";
            case IMAGE: return "image";
            case DRAW: return "drawing";
            case WEB: return "web";
            case VIDEO: return "video";
            default: return "nothing";
        }
    }

    /** "fullscreen" (black) or "over" (on top of what is playing). */
    static String mode() {
        if (kind == Kind.NONE) return "none";
        return over ? "over" : "fullscreen";
    }
}
