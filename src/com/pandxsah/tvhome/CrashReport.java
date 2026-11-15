package com.pandxsah.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Saves the last crash to a file and shows it on screen at the next launch (no adb needed). */
final class CrashReport {
    private CrashReport() {
    }

    static void install(final Context c) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        final Context app = c.getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    FileOutputStream o = new FileOutputStream(new File(app.getFilesDir(), "last_crash.txt"));
                    o.write(("Thread: " + t.getName() + "\n" + sw).getBytes("UTF-8"));
                    o.close();
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    static void showIfAny(final Activity a) {
        try {
            File f = new File(a.getFilesDir(), "last_crash.txt");
            if (!f.exists()) return;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 6000)];
            int n = in.read(buf);
            in.close();
            f.delete();
            TextView tv = new TextView(a);
            tv.setText(new String(buf, 0, Math.max(n, 0), "UTF-8"));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            tv.setTextColor(0xFFFFFFFF);
            tv.setPadding(24, 16, 24, 16);
            ScrollView sv = new ScrollView(a);
            sv.addView(tv);
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("TV Home crashed last time")
                    .setView(sv)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }
}
