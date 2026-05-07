package com.crispasr.androidspeech.core;

public enum Backend {
    CPU("CPU"),
    VULKAN("Vulkan");

    public final String label;

    Backend(String label) {
        this.label = label;
    }
}
