package app.neusstv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Neuss TV's phone page: a small HTTP server on the home network.
 *
 *   GET  /             the page itself
 *   GET  /status       {"ok":true,...} for scripts
 *   POST /send         UTF-8 text, shown in the middle of the TV
 *   POST /image        raw JPEG/PNG/WebP bytes, shown full screen
 *   POST /draw         "#RRGGBB x,y x,y ..." strokes, drawn live
 *   POST /clear        takes whatever is on screen off
 *
 * One thing at a time: every text, image or drawing replaces what was there, and the app's own
 * address screen is closed as soon as content arrives. What is shown stays until the next send
 * or a /clear — unless the call asks for a timeout with ?seconds=N (1-3600).
 */
public class MessageServer extends Service {
    static final int PORT = BuildConfig.PORT;
    static final int MAX_TEXT = 200;
    static final long MAX_BODY = 400L * 1024 * 1024;   // 400 MB: a phone photo or a short video

    private final Handler main = new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private volatile boolean stopped;
    private boolean startupTried;
    private final Runnable expire = this::clearAll;

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, MessageServer.class));
        } catch (Exception ignored) {
            // Android 14 refuses some background starts; the activity or the next boot starts it instead.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("neuss", "Neuss TV", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "neuss")
                .setContentTitle("Neuss TV")
                .setContentText("http://" + Net.ip(this) + ":" + PORT)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .build();
        startForeground(1, n);
        new Thread(this::serve, "neuss-server").start();
        openStartupPage();
    }

    // ------------------------------------------------------- the startup page

    private static final String PREFS = "neuss";
    private static final String KEY_STARTUP = "startup_url";
    private static final String KEY_HEADERS = "headers";
    private static final long STARTUP_DELAY = 15_000;   // let Wi-Fi come up after a reboot first

    String startupUrl() {
        String u = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STARTUP, "");
        return u == null || u.isEmpty() ? null : u;
    }

    private void setStartupUrl(String u) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STARTUP, u == null ? "" : u).apply();
    }

    // ------------------------------------------------- headers (token, cookie)

    /** Saved as written: one "Name: value" per line. */
    private String headerLines() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HEADERS, "");
    }

    private void setHeaderLines(String lines) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HEADERS, lines == null ? "" : lines).apply();
    }

    /** Fills {@link Content#headers} from what was saved, for the page about to open. */
    private void loadHeaders() {
        Content.headers.clear();
        for (String line : headerLines().split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) Content.headers.put(name, value);
        }
    }

    /** Header NAMES only — the values are tokens, and anyone on the Wi-Fi can read /status. */
    private String headerNamesJson() {
        loadHeaders();
        StringBuilder b = new StringBuilder("[");
        for (String name : Content.headers.keySet()) {
            if (b.length() > 1) b.append(",");
            b.append("\"").append(name.replace("\"", "")).append("\"");
        }
        return b.append("]").toString();
    }

    /**
     * On every start of the app — boot, update, or a restart after a crash — the saved page comes back up
     * by itself. It waits a few seconds for the network, and gives way to anything sent in the meantime.
     */
    private void openStartupPage() {
        if (startupTried) return;
        startupTried = true;
        final String u = startupUrl();
        if (u == null) return;
        // Twice: once the network should be up, and again in case the first try lost a race with the
        // system tidying up activities after a crash. Both give way to anything sent in the meantime.
        main.postDelayed(() -> {
            if (Content.kind == Content.Kind.NONE) showWeb(u, 0);
        }, STARTUP_DELAY);
        main.postDelayed(() -> {
            if (Content.kind == Content.Kind.NONE) showWeb(u, 0);
        }, STARTUP_DELAY * 3);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        openStartupPage();   // also covers a restart where onCreate raced with the system
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopped = true;
        Watchdog.soon(this);   // asked to stop: be back in a few seconds
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    /**
     * Never gives up: a bad request cannot kill the loop, and if the port cannot be taken yet (Wi-Fi
     * still coming up after a reboot, or the old socket not released) it waits and tries again.
     */
    private void serve() {
        while (!stopped) {
            try {
                server = new ServerSocket(PORT);
                while (!stopped) {
                    try (Socket s = server.accept()) {
                        s.setSoTimeout(15000);
                        handle(s);
                    } catch (Exception ignored) {
                        // one broken request; keep listening
                    }
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        String head = readHead(in);
        String first = head.split("\r\n", 2)[0];
        String[] parts = first.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String target = parts.length > 1 ? parts[1] : "/";
        String path = target.split("\\?", 2)[0];
        String query = target.contains("?") ? target.split("\\?", 2)[1] : "";

        if (!"POST".equals(method)) {
            if ("/status".equals(path)) {
                reply(s.getOutputStream(), "application/json",
                        "{\"ok\":true,\"app\":\"neuss-tv\",\"port\":" + PORT
                                + ",\"showing\":\"" + showing() + "\",\"mode\":\"" + Content.mode() + "\""
                                + ",\"url\":" + (Content.url == null ? "null" : "\"" + Content.url.replace("\"", "") + "\"")
                                + ",\"startup\":" + (startupUrl() == null ? "null" : "\"" + startupUrl().replace("\"", "") + "\"")
                                + ",\"headers\":" + headerNamesJson()
                                + ",\"endpoints\":[\"/send\",\"/image\",\"/video\",\"/draw\",\"/url\",\"/startup\",\"/headers\",\"/clear\"]}");
            } else {
                reply(s.getOutputStream(), "text/html", Page.HTML);
            }
            return;
        }

        // Media can be far bigger than memory, so it is streamed to a file in the app's own cache;
        // text, strokes and settings are small and stay in memory.
        boolean media = "/image".equals(path) || "/video".equals(path);
        java.io.File upload = media ? readBodyToFile(in, head, newUploadFile()) : null;
        byte[] body = media ? new byte[0] : readBody(in, head);
        // No timeout unless the caller asks for one: what is sent stays until the next send or /clear.
        long seconds = num(query, "seconds", 0);
        long ms = seconds > 0 ? Math.min(seconds, 3600) * 1000 : 0;
        // ?over=1 floats the content on top of whatever is playing; the default takes the screen.
        boolean over = num(query, "over", 0) == 1;
        switch (path) {
            case "/send": {
                String text = new String(body, StandardCharsets.UTF_8).trim();
                if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
                if (!text.isEmpty()) {
                    String t = text;
                    final boolean say = num(query, "say", 0) == 1;
                    main.post(() -> {
                        showText(t, ms, over);
                        if (say) Voice.say(this, t);
                    });
                }
                break;
            }
            case "/image":
            case "/video": {
                if (upload != null && upload.length() > 0) {
                    final java.io.File f = upload;
                    final boolean loop = num(query, "loop", 1) == 1;
                    if ("/video".equals(path) || isVideo(f)) {
                        main.post(() -> showVideo(f, ms, loop));
                    } else {
                        main.post(() -> showImage(f, ms, over));
                    }
                }
                break;
            }
            case "/draw": {
                String segs = new String(body, StandardCharsets.UTF_8);
                main.post(() -> draw(segs, ms, over));
                break;
            }
            case "/url":
            case "/web": {
                String url = new String(body, StandardCharsets.UTF_8).trim();
                if (!url.isEmpty()) {
                    final String u = withScheme(url);
                    main.post(() -> showWeb(u, ms));
                }
                break;
            }
            case "/startup": {
                // A link saves it as the page to open on every start; an empty body switches it off.
                String url = new String(body, StandardCharsets.UTF_8).trim();
                if (url.isEmpty()) {
                    setStartupUrl(null);
                } else {
                    setStartupUrl(withScheme(url));
                }
                break;
            }
            case "/headers": {
                // "Name: value" per line; an empty body wipes them.
                setHeaderLines(new String(body, StandardCharsets.UTF_8).trim());
                loadHeaders();
                break;
            }
            case "/clear":
                main.post(this::clearAll);
                break;
            default:
                break;
        }
        reply(s.getOutputStream(), "text/plain", "ok");
    }

    private String showing() {
        return Content.name();
    }

    /**
     * Adds the scheme when the link came without one. A bare IP address or a .local name is something on
     * the home network — a dashboard, a camera, a box — and those speak plain http far more often than
     * https, so guessing https there only produces a certificate error.
     */
    private static String withScheme(String url) {
        if (url.matches("(?i)^https?://.*")) return url;
        boolean local = url.matches("^(\\d{1,3}\\.){3}\\d{1,3}([:/].*)?$")
                || url.matches("(?i)^(localhost|[^/]*\\.local)([:/].*)?$");
        return (local ? "http://" : "https://") + url;
    }

    /** ?name=123 from the query string, or `fallback`. */
    private static long num(String query, String name, long fallback) {
        for (String p : query.split("&")) {
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).equals(name)) {
                try {
                    return Long.parseLong(p.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c, state = 0;
        while ((c = in.read()) != -1 && b.size() < 8192) {
            b.write(c);
            state = (c == '\r' && (state == 0 || state == 2)) || (c == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            if (state == 4) break;
        }
        return b.toString("UTF-8");
    }

    private static byte[] readBody(InputStream in, String head) throws Exception {
        int len = 0;
        for (String line : head.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    len = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        byte[] body = new byte[(int) Math.min(Math.max(len, 0), 1024 * 1024)];
        int got = 0;
        while (got < body.length) {
            int r = in.read(body, got, body.length - got);
            if (r < 0) break;
            got += r;
        }
        if (got == body.length) return body;
        byte[] cut = new byte[got];
        System.arraycopy(body, 0, cut, 0, got);
        return cut;
    }

    /** A fresh file in the cache; the previous ones are swept so videos do not pile up. */
    private java.io.File newUploadFile() {
        java.io.File dir = new java.io.File(getCacheDir(), "sent");
        if (!dir.exists()) dir.mkdirs();
        java.io.File[] old = dir.listFiles();
        if (old != null) {
            for (java.io.File f : old) {
                if (!f.equals(Content.video)) f.delete();
            }
        }
        return new java.io.File(dir, "m" + System.currentTimeMillis());
    }

    private static java.io.File readBodyToFile(InputStream in, String head, java.io.File out) throws Exception {
        long len = contentLength(head);
        if (len <= 0) return out;
        long left = Math.min(len, MAX_BODY);
        byte[] buf = new byte[64 * 1024];
        try (java.io.OutputStream f = new java.io.BufferedOutputStream(new java.io.FileOutputStream(out))) {
            while (left > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) break;
                f.write(buf, 0, r);
                left -= r;
            }
        }
        return out;
    }

    /** MP4/MOV carry "ftyp" at offset 4; WebM and Matroska start with 1A 45 DF A3. */
    private static boolean isVideo(java.io.File f) {
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[12];
            if (in.read(b) < 12) return false;
            if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return true;
            return (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
        } catch (Exception e) {
            return false;
        }
    }

    private static long contentLength(String head) {
        for (String line : head.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    return Long.parseLong(line.substring(15).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private static void reply(OutputStream out, String type, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    // ------------------------------------------------------------- showing

    /** Content is arriving: the address screen is for setting up, not for watching. */
    private void takeOver() {
        MainActivity.close();
        Voice.stop();
        main.removeCallbacks(expire);
    }

    private void arm(long ms) {
        if (ms > 0) main.postDelayed(expire, ms);
    }

    private void showText(String text, long ms, boolean over) {
        takeOver();
        Content.text(text, over);
        if (over) {
            ShowActivity.close();
            Overlay.text(this, text);
        } else {
            Overlay.clear(this);
            ShowActivity.present(this);
        }
        arm(ms);
    }

    /** A web page always takes the screen: a kiosk behind a half-transparent overlay is no use. */
    private void showWeb(String url, long ms) {
        takeOver();
        loadHeaders();
        Overlay.clear(this);
        Content.web(url);
        ShowActivity.present(this);
        arm(ms);
    }

    private void showImage(java.io.File file, long ms, boolean over) {
        Bitmap bm = decode(file);
        if (bm == null) return;
        takeOver();
        Content.image(bm, over);
        if (over) {
            ShowActivity.close();
            Overlay.image(this, bm);
        } else {
            Overlay.clear(this);
            ShowActivity.present(this);
        }
        arm(ms);
    }

    /** Decodes within the screen size, so a 12 MP phone photo does not blow up the heap. */
    private Bitmap decode(java.io.File file) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int want = Math.max(dm.widthPixels, 1920);
            String path = file.getAbsolutePath();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > want * 2 || o.outHeight / sample > want * 2) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, d);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Plays a video full screen; by default it loops until something else is sent. */
    private void showVideo(java.io.File file, long ms, boolean loop) {
        takeOver();
        Overlay.clear(this);
        Content.video(file, loop);
        ShowActivity.present(this);
        arm(ms);
    }

    /** Strokes of one picture keep adding to the same screen; a text or image before them is replaced. */
    private void draw(String segs, long ms, boolean over) {
        takeOver();
        for (String seg : segs.split("\n")) {
            if (seg.isEmpty()) continue;
            boolean fresh = Content.stroke(seg, over);
            if (over) {
                if (fresh) ShowActivity.close();
                Overlay.stroke(this, seg, fresh);
            } else {
                if (fresh) {
                    Overlay.clear(this);
                    ShowActivity.present(this);  // first stroke of a new drawing
                } else {
                    ShowActivity.addStroke(this, seg);
                }
            }
        }
        arm(ms);
    }

    private void clearAll() {
        main.removeCallbacks(expire);
        Voice.stop();
        Content.clear();
        ShowActivity.close();
        Overlay.clear(this);
    }
}
