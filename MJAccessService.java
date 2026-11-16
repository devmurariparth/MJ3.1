package com.mj.agent;   // <-- apna project nu package name rakho

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lets MJ tap, type, scroll and read the screen of ANY app. Returns "OK ..." / "ERR ..." strings. */
public class MJAccessService extends AccessibilityService {
    private static volatile MJAccessService inst;
    public static MJAccessService get() { return inst; }

    @Override protected void onServiceConnected() { inst = this; }
    @Override public boolean onUnbind(Intent i) { inst = null; return super.onUnbind(i); }
    @Override public void onDestroy() { inst = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    private List<AccessibilityNodeInfo> allNodes() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        collect(getRootInActiveWindow(), out);
        return out;
    }
    private void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out);
    }
    private String label(AccessibilityNodeInfo n) {
        CharSequence t = n.getText(), d = n.getContentDescription();
        return ((t != null ? t.toString() : "") + (d != null ? " " + d : "")).trim();
    }

    /** names = alternatives separated by | (first one that is found and clickable wins) */
    public String clickText(String names) {
        List<AccessibilityNodeInfo> all = allNodes();
        if (all.isEmpty()) return "ERR screen vanchi shakyo nahi";
        for (String name : names.split("\\|")) {
            String q = name.toLowerCase(Locale.ROOT).trim();
            if (q.isEmpty()) continue;
            AccessibilityNodeInfo hit = null;
            for (AccessibilityNodeInfo n : all) {
                String l = label(n).toLowerCase(Locale.ROOT);
                if (l.equals(q)) { hit = n; break; }
                if (hit == null && !l.isEmpty() && l.contains(q)) hit = n;
            }
            for (AccessibilityNodeInfo c = hit; c != null; c = c.getParent()) {
                if (c.isClickable() && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "OK CLICKED: " + name.trim();
            }
        }
        return "ERR '" + names.split("\\|")[0].trim() + "' screen par mali nahi (ke click na thayu)";
    }

    public String typeText(String text) {
        List<AccessibilityNodeInfo> all = allNodes();
        if (all.isEmpty()) return "ERR screen vanchi shakyo nahi";
        AccessibilityNodeInfo f = null;
        for (AccessibilityNodeInfo n : all) if (n.isFocused() && n.isEditable()) { f = n; break; }
        if (f == null) for (AccessibilityNodeInfo n : all) if (n.isEditable()) { f = n; break; }
        if (f == null) return "ERR type karva mate koi text box nathi";
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b) ? "OK TYPED" : "ERR type na thayu";
    }

    public String globalAct(String name) {
        int a;
        switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "back": a = GLOBAL_ACTION_BACK; break;
            case "home": a = GLOBAL_ACTION_HOME; break;
            case "recents": a = GLOBAL_ACTION_RECENTS; break;
            case "notifications": a = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "quicksettings": a = GLOBAL_ACTION_QUICK_SETTINGS; break;
            case "power": a = GLOBAL_ACTION_POWER_DIALOG; break;
            case "lock":
                if (Build.VERSION.SDK_INT < 28) return "ERR lock screen Android 9+ ma j thay";
                a = GLOBAL_ACTION_LOCK_SCREEN; break;
            case "screenshot":
                if (Build.VERSION.SDK_INT < 28) return "ERR screenshot Android 9+ ma j thay";
                a = GLOBAL_ACTION_TAKE_SCREENSHOT; break;
            default: return "ERR ajaano action: " + name;
        }
        return performGlobalAction(a) ? "OK " + name.toUpperCase(Locale.ROOT) : "ERR action na thayo";
    }

    public String scroll(String dir) {
        boolean down = !"up".equalsIgnoreCase(dir);
        for (AccessibilityNodeInfo n : allNodes()) {
            if (n.isScrollable() && n.performAction(down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                                         : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
                return "OK SCROLL " + (down ? "DOWN" : "UP");
        }
        return "ERR scroll thay tevu kai nathi";
    }

    public String readScreen() {
        StringBuilder sb = new StringBuilder();
        for (AccessibilityNodeInfo n : allNodes()) {
            String l = label(n);
            if (!l.isEmpty() && sb.length() < 1500) sb.append(l).append(" | ");
        }
        return sb.length() == 0 ? "ERR screen par vanchay tevu kai nathi" : "OK " + sb;
    }
}
