package com.crispasr.androidspeech;

import com.crispasr.androidspeech.core.TimingInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimingInfoTest {
    @Test
    public void parsesNativeTiming() {
        TimingInfo info = TimingInfo.parse("ok=true\nmessage=done\nsampleRate=24000\nsamples=48000\nloadMs=12\n");
        assertTrue(info.ok);
        assertEquals("done", info.message);
        assertEquals(24000, info.sampleRate);
        assertEquals(48000, info.samples);
        assertEquals(Long.valueOf(12), info.millis.get("loadMs"));
    }
}
