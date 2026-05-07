package com.crispasr.androidspeech.core;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ModelCatalog {
    private static final String TAG = "CrispModelCatalog";

    private ModelCatalog() {}

    public static File appModelDirectory(Context context) {
        File base = context.getExternalFilesDir(null);
        File dir = new File(base == null ? context.getFilesDir() : base, "CrispASR");
        if (dir.exists() && !dir.canRead()) {
            Log.w(TAG, "Model directory exists but is not readable; attempting to recreate: " + dir);
            deleteEmptyDirectory(dir);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create model directory: " + dir + " parentCanWrite=" + dir.getParentFile().canWrite());
        }
        return dir;
    }

    public static File sharedModelDirectory(Context context) {
        File mediaRoot = Environment.getExternalStoragePublicDirectory("Android/media");
        return new File(new File(mediaRoot, context.getPackageName()), "CrispASR");
    }

    public static List<File> searchRoots(Context context) {
        List<File> roots = new ArrayList<>();
        roots.add(sharedModelDirectory(context));
        roots.add(appModelDirectory(context));
        roots.add(new File(context.getFilesDir(), "CrispASR"));
        roots.add(new File(Environment.getExternalStorageDirectory(), "CrispASR"));
        return roots;
    }

    public static List<ModelEntry> scan(Context context) {
        long start = System.nanoTime();
        List<ModelEntry> entries = new ArrayList<>();
        for (File root : searchRoots(context)) {
            scanDirectory(root, entries);
        }
        entries.sort(ModelCatalog::compareEntries);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        Log.i(TAG, "scan roots=" + searchRoots(context) + " entries=" + entries.size() + " elapsedMs=" + elapsedMs);
        return entries;
    }

    public static ModelEntry firstVoiceFor(ModelType type, List<ModelEntry> entries) {
        for (ModelEntry entry : entries) {
            if (entry.isVoicePack() && entry.file.getName().toLowerCase().contains(type.name().toLowerCase().split("_")[0])) {
                return entry;
            }
        }
        for (ModelEntry entry : entries) {
            if (entry.isVoicePack()) return entry;
        }
        return null;
    }

    private static void scanDirectory(File dir, List<ModelEntry> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            Log.w(TAG, "Cannot list model directory: " + dir
                    + " exists=" + dir.exists()
                    + " canRead=" + dir.canRead()
                    + " canExecute=" + dir.canExecute());
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, out);
            } else if (file.getName().toLowerCase().endsWith(".gguf")) {
                out.add(new ModelEntry(file));
            }
        }
    }

    private static int compareEntries(ModelEntry a, ModelEntry b) {
        int type = a.type.label.compareTo(b.type.label);
        if (type != 0) return type;
        int quant = Integer.compare(quantRank(a.quantization), quantRank(b.quantization));
        if (quant != 0) return quant;
        return a.file.getName().compareTo(b.file.getName());
    }

    private static int quantRank(String quantization) {
        if ("Q8".equals(quantization)) return 0;
        if ("Q4".equals(quantization)) return 1;
        if ("FP16".equals(quantization)) return 2;
        return 3;
    }

    private static void deleteEmptyDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            Log.w(TAG, "Refusing to delete non-empty unreadable model directory: " + dir);
            return;
        }
        if (!dir.delete() && dir.exists()) {
            Log.w(TAG, "Failed to delete unreadable model directory: " + dir);
        }
    }
}
