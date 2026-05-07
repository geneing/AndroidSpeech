package com.crispasr.androidspeech.core;

import android.content.Context;
import android.content.SharedPreferences;

public final class TtsSettings {
    private static final String PREFS = "crisp_tts";
    private static final String MODEL = "model";
    private static final String VOICE = "voice";
    private static final String BACKEND = "backend";
    private static final String THREADS = "threads";

    private final SharedPreferences prefs;

    public TtsSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String modelPath() {
        return prefs.getString(MODEL, "");
    }

    public String voicePath() {
        return prefs.getString(VOICE, "");
    }

    public Backend backend() {
        return Backend.valueOf(prefs.getString(BACKEND, Backend.CPU.name()));
    }

    public int threads() {
        return normalizeThreads(prefs.getInt(THREADS, defaultThreads()));
    }

    public void save(String modelPath, String voicePath, Backend backend, int threads) {
        prefs.edit()
                .putString(MODEL, modelPath == null ? "" : modelPath)
                .putString(VOICE, voicePath == null ? "" : voicePath)
                .putString(BACKEND, backend.name())
                .putInt(THREADS, normalizeThreads(threads))
                .apply();
    }

    private static int defaultThreads() {
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(2, Math.min(6, cpus));
    }

    public static int normalizeThreads(int threads) {
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(cpus, threads));
    }
}
