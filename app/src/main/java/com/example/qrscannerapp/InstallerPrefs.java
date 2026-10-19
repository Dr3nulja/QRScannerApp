package com.example.qrscannerapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Имя монтажника: спрашивается один раз, уходит с каждым отчётом (поле installer)
 */
public final class InstallerPrefs {

    private static final String PREFS = "installer";
    private static final String KEY_NAME = "name";

    private InstallerPrefs() {
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static void setName(Context context, String name) {
        prefs(context).edit().putString(KEY_NAME, name.trim()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
