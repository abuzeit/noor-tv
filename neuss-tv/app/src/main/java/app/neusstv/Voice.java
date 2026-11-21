package app.neusstv;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Reads a message out loud when it is sent with ?say=1 — for when nobody is looking at the screen.
 * The engine is started on first use and kept, since starting it takes a second or two.
 */
final class Voice {
    private static TextToSpeech tts;
    private static boolean ready;
    private static String pending;

    private Voice() {
    }

    /** Arabic text gets the Arabic voice, anything else the device's own language. */
    private static Locale localeFor(String text) {
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return new Locale("ar");
        }
        return Locale.getDefault();
    }

    static void say(Context c, final String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (ready && tts != null) {
            speak(text);
            return;
        }
        pending = text;
        if (tts != null) return;   // still starting up; it will say the last thing asked for
        try {
            tts = new TextToSpeech(c.getApplicationContext(), status -> {
                ready = status == TextToSpeech.SUCCESS;
                if (ready && pending != null) {
                    speak(pending);
                    pending = null;
                }
            });
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) {
                }

                public void onDone(String id) {
                }

                public void onError(String id) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void speak(String text) {
        try {
            Locale want = localeFor(text);
            int res = tts.isLanguageAvailable(want);
            if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE
                    || res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                tts.setLanguage(want);
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "neuss");
        } catch (Exception ignored) {
        }
    }

    /** Stops mid-sentence — used when the screen is cleared or something else is sent. */
    static void stop() {
        try {
            if (tts != null) tts.stop();
        } catch (Exception ignored) {
        }
    }
}
