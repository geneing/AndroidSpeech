package com.crispasr.androidspeech.core;

import java.io.File;
import java.util.Locale;

public final class ModelEntry {
    public final File file;
    public final ModelType type;
    public final String quantization;
    public final long bytes;

    public ModelEntry(File file) {
        this.file = file;
        this.type = ModelType.fromFileName(file.getName());
        this.quantization = detectQuantization(file.getName());
        this.bytes = file.length();
    }

    public boolean isVoicePack() {
        String lower = file.getName().toLowerCase(Locale.US);
        return lower.contains("kokoro-voice")
                || lower.contains("vibevoice-voice")
                || lower.contains("voice-pack")
                || lower.contains("voice_pack")
                || lower.contains("speaker");
    }

    public String displayName() {
        return type.label + " / " + quantization + " / " + file.getName();
    }

    private static String detectQuantization(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.contains("q8")) return "Q8";
        if (lower.contains("q4")) return "Q4";
        if (lower.contains("f16") || lower.contains("fp16")) return "FP16";
        return "GGUF";
    }
}
