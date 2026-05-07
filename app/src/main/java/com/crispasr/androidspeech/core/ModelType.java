package com.crispasr.androidspeech.core;

public enum ModelType {
    KOKORO("Kokoro"),
    VIBEVOICE("VibeVoice"),
    QWEN3_TTS("Qwen3-TTS"),
    UNKNOWN("Unknown");

    public final String label;

    ModelType(String label) {
        this.label = label;
    }

    public static ModelType fromFileName(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("kokoro") && !lower.contains("voice")) return KOKORO;
        if (lower.contains("vibevoice") && !lower.contains("vibevoice-voice")) return VIBEVOICE;
        if (lower.contains("qwen3") && lower.contains("tts")) return QWEN3_TTS;
        return UNKNOWN;
    }
}
