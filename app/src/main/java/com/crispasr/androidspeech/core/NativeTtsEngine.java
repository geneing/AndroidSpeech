package com.crispasr.androidspeech.core;

import android.content.Context;
import android.util.Log;

public final class NativeTtsEngine {
    private static final String TAG = "CrispNativeTts";

    static {
        System.loadLibrary("crisp_android_tts");
    }

    private NativeTtsEngine() {}

    public static PcmSynthesisResult synthesizePcm(Context context, String modelPath, String voicePath, Backend backend,
                                                   String text, int threads) {
        long start = System.nanoTime();
        PcmSynthesisResult result = nativeSynthesizePcm(modelPath, voicePath == null ? "" : voicePath, backend.name(),
                text == null ? "" : text, Math.max(1, threads));
        TimingInfo info = result.timingInfo();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        info.millis.put("javaTotalMs", elapsedMs);
        Log.i(TAG, "synthesize ok=" + info.ok + " pcmBytes=" + result.pcm16.length + " backend=" + backend
                + " model=" + modelPath + " voice=" + voicePath + " " + info.format().replace('\n', ' '));
        return new PcmSynthesisResult(result.pcm16, appendJavaTiming(result.timing, elapsedMs));
    }

    private static String appendJavaTiming(String timing, long javaTotalMs) {
        return (timing == null ? "" : timing) + "javaTotalMs=" + javaTotalMs + "\n";
    }

    public static native PcmSynthesisResult nativeSynthesizePcm(String modelPath, String voicePath, String backend,
                                                                String text, int threads);
}
