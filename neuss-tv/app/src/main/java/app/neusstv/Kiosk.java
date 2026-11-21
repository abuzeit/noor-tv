package app.neusstv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * The kiosk browser: a full-screen WebView with no address bar, no tabs and no way to wander off, but
 * a complete browser underneath — JavaScript, WebSocket, fetch/XHR, DOM storage and IndexedDB, cookies,
 * HTML5 video and audio (including a page's own full-screen player), and service workers.
 */
final class Kiosk {

    private Kiosk() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    static WebView create(final Activity a) {
        WebView w = new WebView(a);
        w.setBackgroundColor(Color.BLACK);

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);                     // JS, and with it WebSocket, fetch and XHR
        s.setDomStorageEnabled(true);                     // localStorage / sessionStorage
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);     // a TV has no "tap to play"
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);               // popups open in this same view
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);                      // a page from the net has no business in the files
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            s.setAlgorithmicDarkeningAllowed(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);

        // Everything stays inside this view: no handing links to another app, no browser chrome.
        // Saved headers (a token, say) ride along with the first load and with every page the user
        // walks to from there, which is what gets past a login on a dashboard.
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (Content.headers.isEmpty() || !r.isForMainFrame()) return false;
                load(v, r.getUrl().toString(), Content.headers);
                return true;
            }
        });

        // A page asking for full screen (video players) gets the whole screen.
        w.setWebChromeClient(new WebChromeClient() {
            private View custom;
            private FrameLayout holder;

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (custom != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                custom = view;
                holder = new FrameLayout(a);
                holder.setBackgroundColor(Color.BLACK);
                holder.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                a.addContentView(holder, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                if (holder == null) return;
                ViewGroup parent = (ViewGroup) holder.getParent();
                if (parent != null) parent.removeView(holder);
                holder = null;
                custom = null;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();   // no camera or microphone from a page on the TV
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                return true;      // the page's console stays out of the log
            }
        });
        return w;
    }

    /**
     * Loads the page with the saved headers. A `Cookie` header is also handed to the CookieManager, so it
     * sticks to every later request on that site instead of only the first one.
     */
    static void load(WebView w, String url, java.util.Map<String, String> headers) {
        if (headers.isEmpty()) {
            w.loadUrl(url);
            return;
        }
        java.util.Map<String, String> extra = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
            if ("Cookie".equalsIgnoreCase(e.getKey())) {
                try {
                    CookieManager.getInstance().setCookie(url, e.getValue());
                } catch (Exception ignored) {
                }
            } else {
                extra.put(e.getKey(), e.getValue());
            }
        }
        CookieManager.getInstance().flush();
        w.loadUrl(url, extra);
    }
}
