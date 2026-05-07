package com.crispasr.androidspeech.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TimingInfo {
    public boolean ok;
    public String message = "";
    public int sampleRate = 24000;
    public int samples;
    public final Map<String, Long> millis = new LinkedHashMap<>();

    public static TimingInfo parse(String text) {
        TimingInfo info = new TimingInfo();
        if (text == null) {
            info.ok = false;
            info.message = "Native call returned no timing data";
            return info;
        }
        String[] lines = text.split("\\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if ("ok".equals(key)) {
                info.ok = Boolean.parseBoolean(value);
            } else if ("message".equals(key)) {
                info.message = value;
            } else if ("sampleRate".equals(key)) {
                info.sampleRate = parseInt(value, info.sampleRate);
            } else if ("samples".equals(key)) {
                info.samples = parseInt(value, 0);
            } else if (key.endsWith("Ms")) {
                info.millis.put(key, parseLong(value, 0L));
            }
        }
        return info;
    }

    public String format() {
        StringBuilder b = new StringBuilder();
        b.append(ok ? "OK" : "FAILED").append(": ").append(message).append('\n');
        b.append("sampleRate=").append(sampleRate).append(" samples=").append(samples).append('\n');
        for (Map.Entry<String, Long> e : millis.entrySet()) {
            b.append(e.getKey()).append('=').append(e.getValue()).append(" ms\n");
        }
        return b.toString();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
