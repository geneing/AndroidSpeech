package com.crispasr.androidspeech.core;

public final class PcmSynthesisResult {
    public final byte[] pcm16;
    public final String timing;

    public PcmSynthesisResult(byte[] pcm16, String timing) {
        this.pcm16 = pcm16 == null ? new byte[0] : pcm16;
        this.timing = timing == null ? "" : timing;
    }

    public TimingInfo timingInfo() {
        return TimingInfo.parse(timing);
    }
}
