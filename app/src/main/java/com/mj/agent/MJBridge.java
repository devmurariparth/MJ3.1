package com.mj.agent;   // <-- apna project nu package name rakho (MainActivity ma upar je hoy te)

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every method returns "OK ..." or "ERR ..." so index.html can tell the TRUTH about what happened. */
public class MJBridge {
    static final int REQ_VOICE = 7701;
    private final Activity act;
    private final WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech = null, pendingLang = "gu-IN";
    private boolean torchOn = false;

    public MJBridge(Activity a, WebView w) {
        act = a; web = w;
        tts = new TextToSpeech(a.getApplicationContext(), st -> {
            ttsReady = (st == TextToSpeech.SUCCESS);
            if (ttsReady && pendingSpeech != null) { doSpeak(pendingSpeech, pendingLang); pendingSpeech = null; }
        });
    }

    public static void requestPermissions(Activity a) {
        a.runOnUiThread(() -> {
            ArrayList<String> missing = new ArrayList<>();
            if (a.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.CALL_PHONE);
            }
            if (a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_CONTACTS);
            }
            if (!missing.isEmpty()) {
                a.requestPermissions(missing.toArray(new String[0]), 77);
            }
        });
    }

    public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); } }

    private String start(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); act.startActivity(i); return "OK"; }
        catch (Exception e) { return "ERR " + e.getMessage(); }
    }

    private boolean granted(String p) { return act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    /** number as-is, or look a contact name up (needs READ_CONTACTS) */
    private String resolveNumber(String who) {
        if (who == null) return null;
        String digits = who.replaceAll("[^\\d+]", "");
        if (digits.length() >= 3 && digits.length() >= who.replaceAll("\\s", "").length() - 2) return digits;
        if (!granted(Manifest.permission.READ_CONTACTS)) return null;
        try (Cursor c = act.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ ContactsContract.CommonDataKinds.Phone.NUMBER },
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", new String[]{ "%" + who.trim() + "%" }, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0).replaceAll("[^\\d+]", "");
        } catch (Exception ignored) {}
        return null;
    }

    @JavascriptInterface
    public String openApp(String name, String pkg) {
        PackageManager pm = act.getPackageManager();
        try {
            if (pkg != null && !pkg.isEmpty()) {
                Intent li = pm.getLaunchIntentForPackage(pkg);
                if (li != null) return start(li).equals("OK") ? "OK " + name : "ERR open failed";
            }
            String q = (name == null ? "" : name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u0900-\\u0dff]", "");
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
            ResolveInfo best = null;
            for (ResolveInfo ri : apps) {
                String label = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u0900-\\u0dff]", "");
                if (label.equals(q)) { best = ri; break; }
                if (best == null && !q.isEmpty() && (label.contains(q) || q.contains(label)) && !label.isEmpty()) best = ri;
            }
            if (best == null) return "ERR '" + name + "' નામની app ફોનમાં મળી નથી";
            Intent li = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
            return li != null && start(li).equals("OK") ? "OK " + best.loadLabel(pm) : "ERR app ખુલી નહીં";
        } catch (Exception e) { return "ERR " + e.getMessage(); }
    }

    @JavascriptInterface
    public String toggleTorch(boolean on) {
        if (act.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            act.runOnUiThread(() ->
                    act.requestPermissions(new String[]{ Manifest.permission.CAMERA }, 78));
            return "ERR Camera permission જરૂરી છે. MJ ને permission આપ્યા પછી ફરી પ્રયાસ કરો.";
        }
        try {
            CameraManager cm = (CameraManager) act.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { cm.setTorchMode(id, on); torchOn = on; return "OK"; }
            }
            return "ERR ફોનમાં ફ્લેશ નથી";
        } catch (Exception e) { return "ERR " + e.getMessage(); }
    }

    @JavascriptInterface
    public String openSetting(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT), a;
        if (n.contains("appinfo")) return start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.getPackageName())));
        if (n.contains("wifi") || n.contains("wi-fi")) a = Settings.ACTION_WIFI_SETTINGS;
        else if (n.contains("bluetooth")) a = Settings.ACTION_BLUETOOTH_SETTINGS;
        else if (n.contains("sound") || n.contains("volume")) a = Settings.ACTION_SOUND_SETTINGS;
        else if (n.contains("display") || n.contains("bright")) a = Settings.ACTION_DISPLAY_SETTINGS;
        else if (n.contains("access")) a = Settings.ACTION_ACCESSIBILITY_SETTINGS;
        else if (n.contains("develop")) a = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS;
        else if (n.contains("location")) a = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        else if (n.contains("battery")) a = Settings.ACTION_BATTERY_SAVER_SETTINGS;
        else if (n.contains("airplane")) a = Settings.ACTION_AIRPLANE_MODE_SETTINGS;
        else a = Settings.ACTION_SETTINGS;
        String r = start(new Intent(a));
        return r.equals("OK") && a.equals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) ? "OK Developer Options" : r;
    }

    @JavascriptInterface
    public String makeCall(String who) {
        String num = resolveNumber(who);
        if (num == null) {
            if (!granted(Manifest.permission.READ_CONTACTS)) requestPermissions(act);
            return "ERR '" + who + "' નો નંબર મળ્યો નહીં (Contacts permission આપો અથવા નંબર બોલો)";
        }
        Uri uri = Uri.fromParts("tel", num, null);
        if (granted(Manifest.permission.CALL_PHONE)) {
            String r = start(new Intent(Intent.ACTION_CALL, uri));
            return r.equals("OK") ? "OK CALLING " + num : r;
        }
        requestPermissions(act);   // ask now; meanwhile open dialer with the number filled
        String r = start(new Intent(Intent.ACTION_DIAL, uri));
        return r.equals("OK") ? "OK DIALER " + num : r;
    }

    @JavascriptInterface
    public String openWhatsApp(String who, String msg) {
        String num = resolveNumber(who);
        if (num == null) {
            if (!granted(Manifest.permission.READ_CONTACTS)) requestPermissions(act);
            return "ERR '" + who + "' નો નંબર મળ્યો નહીં";
        }
        num = num.replace("+", "");
        if (num.length() == 10) num = "91" + num;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(msg == null ? "" : msg)));
        i.setPackage("com.whatsapp");
        String r = start(i);
        if (!r.equals("OK")) r = start(new Intent(Intent.ACTION_VIEW, i.getData()));
        return r.equals("OK") ? "OK WHATSAPP (મેસેજ તૈયાર, Send દબાવો)" : r;
    }

    @JavascriptInterface
    public String sendSms(String who, String msg) {
        String num = resolveNumber(who);
        if (num == null) return "ERR '" + who + "' નો નંબર મળ્યો નહીં";
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + num));
        i.putExtra("sms_body", msg == null ? "" : msg);
        String r = start(i);
        return r.equals("OK") ? "OK SMS (મેસેજ તૈયાર, Send દબાવો)" : r;
    }

    @JavascriptInterface
    public String playMusic(String q) {
        Intent i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
        i.putExtra(SearchManager.QUERY, q);
        if (start(i).equals("OK")) return "OK PLAY: " + q;
        return openYouTubeSearch(q);
    }

    @JavascriptInterface
    public String openYouTubeSearch(String q) {
        Intent i = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube");
        i.putExtra("query", q);
        if (start(i).equals("OK")) return "OK YOUTUBE: " + q;
        return start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))));
    }

    @JavascriptInterface
    public String openUrl(String url) {
        return start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @JavascriptInterface
    public String setAlarm(int h, int m, String label) {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        return start(i);
    }

    @JavascriptInterface
    public String setVolume(String mode) {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            int s = AudioManager.STREAM_MUSIC, f = AudioManager.FLAG_SHOW_UI;
            switch (mode == null ? "up" : mode) {
                case "down": am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, f); break;
                case "mute": am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, f); break;
                case "max": am.setStreamVolume(s, am.getStreamMaxVolume(s), f); break;
                default: am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, f);
            }
            return "OK VOLUME " + mode;
        } catch (Exception e) { return "ERR " + e.getMessage(); }
    }

    private void doSpeak(String text, String lang) {
        Locale loc = Locale.forLanguageTag(lang);
        int r = tts.setLanguage(loc);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(new Locale("hi", "IN"));
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mj");
    }

    @JavascriptInterface
    public String speak(String text, String lang) {
        if (!ttsReady) { pendingSpeech = text; pendingLang = lang; return "OK"; }
        doSpeak(text, lang);
        return "OK";
    }

    @JavascriptInterface
    public String startVoice(String lang) {
        act.runOnUiThread(() -> {
            try {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                act.startActivityForResult(i, REQ_VOICE);
            } catch (Exception e) { sendToJs(""); }
        });
        return "OK";
    }

    // ---------- Accessibility based control (needs MJAccessService turned ON) ----------
    private String a11yOff() {
        return "ERR Accessibility chalu nathi. Settings > Accessibility > Installed apps > MJ > ON karo. Option grey hoy to: MJ App info > upar jamne ⋮ > 'Allow restricted settings'";
    }
    @JavascriptInterface public String isA11yOn() { return MJAccessService.get() != null ? "OK" : a11yOff(); }
    @JavascriptInterface public String clickText(String t) { MJAccessService x = MJAccessService.get(); return x == null ? a11yOff() : x.clickText(t); }
    @JavascriptInterface public String typeText(String t) { MJAccessService x = MJAccessService.get(); return x == null ? a11yOff() : x.typeText(t); }
    @JavascriptInterface public String globalAction(String n) { MJAccessService x = MJAccessService.get(); return x == null ? a11yOff() : x.globalAct(n); }
    @JavascriptInterface public String scroll(String d) { MJAccessService x = MJAccessService.get(); return x == null ? a11yOff() : x.scroll(d); }
    @JavascriptInterface public String readScreen() { MJAccessService x = MJAccessService.get(); return x == null ? a11yOff() : x.readScreen(); }

    /** call this from MainActivity.onActivityResult */
    public void handleResult(int req, int res, Intent data) {
        if (req != REQ_VOICE) return;
        String text = "";
        if (res == Activity.RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) text = r.get(0);
        }
        sendToJs(text);
    }

    private void sendToJs(String text) {
        act.runOnUiThread(() -> web.evaluateJavascript("window.onNativeVoiceResult && window.onNativeVoiceResult(" + JSONObject.quote(text) + ")", null));
    }
}
