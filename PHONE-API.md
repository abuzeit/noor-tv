# Neuss TV — HTTP API

The phone page and its API now live in their own app, **Neuss TV** (`app.neusstv`, source in
[`neuss-tv/`](neuss-tv)). Plain HTTP on port **8080**, LAN only, **no auth**, no TLS.

Base URL: `http://192.168.1.189:8080`  ·  tabs on the page: ✍️ write · 🎨 draw · 🖼️ photo · 🌐 link

| Method | Path      | Body                          | Effect |
|--------|-----------|-------------------------------|--------|
| GET    | `/`       | –                             | The phone page (write / draw / photo) |
| GET    | `/status` | –                             | `{"ok":true,"app":"neuss-tv","port":8080,"showing":"text\|image\|drawing\|nothing","mode":"fullscreen\|over\|none",...}` |
| POST   | `/send`   | UTF-8 text, ≤200 chars        | Shows the text on the TV (`?say=1` reads it out loud) |
| POST   | `/image`  | raw image **or video** bytes  | Shows the photo, or plays the video, full screen |
| POST   | `/video`  | raw MP4 / WebM / MOV bytes    | Plays the video full screen |
| POST   | `/draw`   | stroke segments (see below)   | Draws; further segments add to the same picture |
| POST   | `/url`    | a link, e.g. `example.com`    | Opens the page full screen in kiosk mode |
| POST   | `/headers`| `Name: value` per line, or empty | Saves headers sent with every kiosk page (a token) |
| POST   | `/startup`| a link, or empty to switch off | Saves the page to open by itself on every start |
| POST   | `/clear`  | empty                         | Takes the screen down, back to the home screen |

Responses are `200 OK` with body `ok` (the GETs return the page / JSON). `Access-Control-Allow-Origin: *`
is set, so a browser page can post to it.

## Two ways to show it: `?over=`

| | URL | What you get |
|---|---|---|
| **Full screen** (default) | `/send`, `/image`, `/draw` | Solid black screen, content on top, nothing else visible |
| **Over what is playing** | `…?over=1` | Content floats on the film / channel / home screen, which keeps running underneath |

```bash
curl -X POST --data-binary 'الأكل جاهز' "http://192.168.1.189:8080/send?over=1"
```

The phone page has the same choice as two buttons at the top — **🖤 شاشة سودا** and **🎬 فوق اللي شغّال** —
and it applies to text, photos and drawings alike. The mode travels with the content: sending in the other
mode switches the screen over, and `/status` reports `"mode":"fullscreen"` or `"over"`.

One caveat for `over=1`: this firmware caps an app's overlay windows at 0.8 opacity, so content shown this
way is always slightly see-through. That is the TV's doing, not the app's, and it is the point of the mode —
what is playing stays visible. For a solid, fully opaque picture use the default.

## How it behaves on screen

- **Full screen, fully opaque** (default mode). The content is drawn on a solid black screen, not as a
  see-through overlay — nothing of the home screen, no URL, no app name shows behind it.
- **One thing at a time.** Each text, photo or drawing *replaces* whatever was there.
- **It stays.** Nothing disappears on its own: what was sent remains until the next send or a `/clear`.
  Pass `?seconds=N` (1–3600) on `/send`, `/image` or `/draw` if you do want it to come down by itself.
- **The address screen closes itself** as soon as content arrives: `http://…:8080` is for setting up,
  not for watching.
- Drawing is the exception to "replaces": consecutive `/draw` calls add strokes to the picture already
  up, which is how the live finger-drawing works. A `/send` or `/image` ends the drawing.

## Text

```bash
curl -X POST --data-binary 'العشاء جاهز 🍽️' http://192.168.1.189:8080/send
```

```bash
curl -X POST --data-binary 'back in 5' "http://192.168.1.189:8080/send?seconds=60"
```

## Reading a message out loud

```bash
curl -X POST --data-binary 'الأكل جاهز' "http://192.168.1.189:8080/send?say=1"
```

The text is shown **and** spoken through the TV, using the box's own text-to-speech. Arabic text gets the
Arabic voice, anything else the device's language. Without `?say=1` nothing is spoken, and the phone page
has it as a switch (🔊 اقرأها بصوت عالي) in the write tab. Speech stops when the screen is cleared or
anything else is sent. If the box has no Arabic voice installed, the text still appears — only the sound
is missing (Settings → device preferences → text-to-speech).

## Image and video

Raw bytes — no multipart, no base64:

```bash
curl -X POST --data-binary @photo.jpg http://192.168.1.189:8080/image
```

```python
import requests
requests.post("http://192.168.1.189:8080/image", data=open("photo.jpg", "rb").read())
```

```bash
curl -X POST --data-binary @clip.mp4 http://192.168.1.189:8080/video
```

`/image` takes video too — the file is sniffed (MP4/MOV carry `ftyp`, WebM starts `1A 45 DF A3`), so the
phone page and a script can both just send whatever the person picked. Videos **loop** until something
else is sent; `?loop=0` plays once.

Uploads are streamed to the box's own cache rather than held in memory, so the limit is 400 MB and a
phone video is fine. Photos are downsampled to the screen before display. Both are centred and
letterboxed on black, aspect ratio kept, and each new upload sweeps the previous file away.

## Drawing

One segment per line (several lines may be sent in one body):

```
#RRGGBB x,y x,y x,y ...
```

- `x` and `y` are fractions of the screen, `0.0`–`1.0`, origin top-left.
- A segment starting with `+` continues the previous stroke instead of beginning a new one.
- Colour goes through `Color.parseColor`, so `#RRGGBB` and `#AARRGGBB` both work; anything unparsable
  falls back to white. Stroke width is fixed at 14 px, round cap.

```bash
curl -X POST --data-binary '#f5c542 0.2,0.35 0.5,0.8 0.8,0.35 0.2,0.35' http://192.168.1.189:8080/draw
```

```bash
curl -X POST --data '' http://192.168.1.189:8080/clear
```

## Staying up

The server is meant to be reachable at any moment, so it is held up in four ways:

1. **Foreground service** (`START_STICKY`) — Android does not kill it to free memory the way it kills
   background apps, and restarts it if it ever does. `am kill app.neusstv` (what low memory does) left
   it running, same pid.
2. **A watchdog alarm every 5 minutes** pokes `BootReceiver`, which starts the service again. Starting a
   service that is already running does nothing, so this is free while all is well. The alarm is
   *inexact* on purpose: it needs no exact-alarm permission — that is what crashed the Kahf app.
3. **Crash handling.** The HTTP loop catches per-request errors and rebinds the port if it is lost, and
   an app-wide uncaught-exception handler schedules a restart a few seconds out. Verified with
   `adb shell am crash app.neusstv`: the server was back, on a new pid, within 12 seconds.
4. **Boot and update.** `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` all start it,
   and each run re-arms the watchdog (alarms are dropped on reboot). Verified on a real reboot: the box
   was back on the network after ~15 s and the server was answering ~30 s later, started by
   `BootReceiver` with nothing launched by hand — `Start proc … for broadcast {app.neusstv/.BootReceiver}`,
   then `Background started FGS: Allowed … SYSTEM_ALERT_WINDOW_PERMISSION / SYSTEM_ALLOW_LISTED`, and the
   `app.neusstv.KEEPALIVE` alarm registered again.

It is also on the device's doze/background allowlist:

```bash
adb -s 192.168.1.189:5555 shell dumpsys deviceidle whitelist +app.neusstv
```

**The one case it cannot recover from by itself:** a *force-stop* — from Settings → Apps → Neuss TV →
Force stop, or a "cleaner"/task-killer doing the same. Android then cancels the app's alarms and blocks
its broadcasts until something starts it by hand; no app can work around that. Verified: after
`am force-stop`, the port stayed closed. Opening the **Neuss TV** tile once, or rebooting, brings it back.

## Interrupting what is playing

A message, photo or drawing in the default mode takes the screen from whatever app is in front — verified
against Kodi in the foreground: the content replaced it full-screen, and `/clear` handed the screen back.
This works because the app holds `SYSTEM_ALERT_WINDOW`, which is what allows it to start its screen from
the background; that permission is granted with:

```bash
adb -s 192.168.1.189:5555 shell appops set app.neusstv SYSTEM_ALERT_WINDOW allow
```

With `?over=1` nothing is interrupted at all — the content floats over the running app instead.

## Web pages (kiosk mode)

```bash
curl -X POST --data-binary 'https://www.aljazeera.net' http://192.168.1.189:8080/url
```

```bash
curl -X POST --data-binary '192.168.1.50:8123' http://192.168.1.189:8080/url
```

A bare host is fine: a local address (`192.168.1.50:8123`, `box.local`, `localhost`) gets `http://`,
anything else gets `https://` — guessing https for a home dashboard only produces a certificate error. `/web` is an alias of `/url`, and
`?seconds=N` works here too. The phone page has it as the **🌐 رابط** tab.

It is a real browser, not a preview: the system WebView (Chromium) with

- **JavaScript**, and with it **WebSocket**, `fetch` and XHR — verified on the TV against a local test
  page: `JavaScript: running`, `WebSocket: live`, twelve round-trips echoed back;
- **localStorage / sessionStorage, IndexedDB, cookies** (third-party cookies included) and service workers;
- **HTML5 video and audio**, with autoplay allowed (a TV has nobody to tap "play") and a page's own
  full-screen player handled;
- **plain `http://`** as well as https, so local dashboards, cameras and Home Assistant work. (Android
  blocks cleartext by default; `usesCleartextTraffic` is on for this app.)

Clearing the screen from the phone page also resets that page's own fields — the link box and the chosen
photo with its preview — so the next send starts clean. The saved startup link stays in the box while the
toggle is on.

Kiosk means what it says: no address bar, no tabs, no menu, and links stay inside the page — nothing can
send the TV off to another app. **Back** steps through the page's own history and stops at the first page
instead of dropping out. To leave: send anything else, call `/clear`, or press **Home** on the remote.

Two things worth knowing:

- A web page always takes the whole screen; `?over=1` is ignored for `/url`, since a half-transparent
  kiosk is no use to anyone.
- Navigating a site is done with the remote's **D-pad** — the TV has no mouse pointer, so pages that
  need hovering or precise clicking are awkward. Sites built for TVs, dashboards and kiosk pages are fine.
- Camera and microphone requests from a page are denied outright.

## A page that opens by itself (startup link)

```bash
curl -X POST --data-binary 'https://dashboard.example.com' http://192.168.1.189:8080/startup
```

```bash
curl -X POST --data '' http://192.168.1.189:8080/startup
```

The link is saved on the box and opens on **every start of the app** — after a reboot, after an update,
and after a crash restart — so a dashboard or a clock page is back on the TV with nobody touching it.
The second command switches it off. `/status` reports it as `"startup"`. On the phone page the link tab
has it as a **toggle** (⭐ يفتح لحاله عند تشغيل التلفزيون): the page reads the saved link from `/status`
when it opens, so the switch shows the real state and the field is filled in with it. Turning it on with
an empty field does nothing but say so, and sending a new link while it is on updates the saved one too.

How it behaves:

- It waits ~15 s after the app starts, so Wi-Fi is up before the page loads, and tries once more at 45 s
  in case the first attempt lost a race with the system tidying up after a crash.
- It **never overrides you**: if anything was sent in the meantime, the startup page stays out of the way.
- Measured: on a clean start the page was up at **t+15 s**; after `am crash` it was back at **t+20 s**.
- Nothing is set by default — the box ships with no startup link.

## Pages behind a login (token header)

```bash
curl -X POST --data-binary 'Authorization: Bearer eyJhbGciOi...' http://192.168.1.189:8080/headers
```

```bash
curl -X POST --data '' http://192.168.1.189:8080/headers
```

Saved on the box and sent with **every page the kiosk opens** — the first load and every page walked to
from there — so a dashboard that only wants a token shows up without a login screen on a TV that has no
keyboard. Several headers go one per line:

```
Authorization: Bearer eyJhbGciOi...
X-Api-Key: 8f3a91
```

A `Cookie:` line is handled specially: besides being sent, it is handed to the browser's cookie store for
that site, so it sticks to every later request instead of only the first.

On the phone page it is under **🔑 توكن / هيدر** in the link tab, which shows which headers are saved
without ever showing their values.

Verified against a page that returns `401 LOGIN REQUIRED` without the header: with no token the TV showed
the 401 page; after saving `Authorization: Bearer …` the same URL came up as `TOKEN ACCEPTED`, and the
server log confirmed the header arrived from the TV.

**Treat it as a secret in a plain place.** The token sits in the app's private storage on the box, and
`/headers` takes no credentials — anyone on your Wi-Fi can set or replace it. `/status` therefore reports
only the header *names*, never the values. Use a token scoped to what the TV should see, not an admin one.

## Checking it is alive

```bash
curl -s http://192.168.1.189:8080/status
```

The server runs as a foreground service and comes back after a reboot or an app update. If it ever
stops answering, opening the **Neuss TV** tile on the home screen starts it again, or:

```bash
adb -s 192.168.1.189:5555 shell monkey -p app.neusstv -c android.intent.category.LEANBACK_LAUNCHER 1
```

## Building and installing

One command, from the Mac on the same Wi-Fi as the TV:

```bash
~/noor-tv/neuss-tv/deploy.sh
```

It builds, finds the box on whatever address it has today (the last one first, then a scan of the
network for the debugging port), installs over the old version, re-applies the two things a fresh
install drops — the overlay permission and the power-saving exemption — starts it, and prints the
answer from `/status` so you can see it came back up. The screen, the startup link and the saved token
are untouched by an update.

Give it an address to skip the search, or skip the build to install what is already compiled:

```bash
~/noor-tv/neuss-tv/deploy.sh 192.168.1.189
```

```bash
SKIP_BUILD=1 ~/noor-tv/neuss-tv/deploy.sh
```

If it says the TV is not authorised, the box is showing the "allow debugging?" prompt — accept it on the
TV and run it again.

## The logo

`icon.png` (the launcher icon) and `banner.png` (the 320x180 tile on the TV home row) are generated from
one file:

```bash
~/noor-tv/neuss-tv/make-logo.sh ~/noor-tv/neuss-tv/logo.webp
```

The source is kept at `neuss-tv/logo.webp`. The artwork is on white, so the tile is on white too —
on a dark tile the logo's own white square showed as a box. Drop in a bigger file (SVG or 512 px) and
run the same command for a sharper tile: the current source is only 72x72, which is soft when a TV row
blows it up.

The port is set in `neuss-tv/app/build.gradle` (`buildConfigField 'int', 'PORT'`).

## Notes from building this

- **Overlay windows are capped at 0.8 opacity on this firmware.** The first version drew the content in
  a `TYPE_APPLICATION_OVERLAY` window and the home screen showed through every photo; `dumpsys window`
  reported `alpha=0.8` on a window that asked for 1.0. The default mode is a normal full-screen activity
  (`ShowActivity`) instead, which composites fully opaque; the overlay path stayed on as `?over=1`
  (`Overlay.java`), where seeing through to the film is what you want.
- **The old phone page in the Kahf app is switched off** (`PHONE_PAGE=false` in
  `noor-app/app/build.gradle`) — two apps cannot both hold port 8080, and whichever bound first would
  have won at random.
- **Kahf used to crash** on Android 14: it scheduled the adhan with `setExactAndAllowWhileIdle` without
  `USE_EXACT_ALARM`, threw `SecurityException`, and took the old phone page down with it. The permission
  is now in `noor-app/app/src/main/AndroidManifest.xml`.

## Security

Port 8080 is open to every device on the LAN and takes no credentials: anyone on the Wi-Fi can put text,
a drawing or a photo on the TV. The same holds for adb on port 5555, which is still enabled on this box.
