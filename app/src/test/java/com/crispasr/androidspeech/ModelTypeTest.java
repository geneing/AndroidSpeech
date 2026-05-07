package com.crispasr.androidspeech;

import com.crispasr.androidspeech.core.ModelType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModelTypeTest {
    @Test
    public void detectsSupportedTtsModels() {
        assertEquals(ModelType.KOKORO, ModelType.fromFileName("kokoro-82m-q8_0.gguf"));
        assertEquals(ModelType.VIBEVOICE, ModelType.fromFileName("vibevoice-realtime-0.5b-q4.gguf"));
        assertEquals(ModelType.QWEN3_TTS, ModelType.fromFileName("Qwen3-TTS-0.6B-Q8.gguf"));
    }
}
