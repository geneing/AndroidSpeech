package com.crispasr.androidspeech.tts;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.util.Log;

import com.crispasr.androidspeech.core.NativeTtsEngine;
import com.crispasr.androidspeech.core.PcmSynthesisResult;
import com.crispasr.androidspeech.core.TimingInfo;
import com.crispasr.androidspeech.core.TtsSettings;
import java.util.Locale;

public final class CrispTtsService extends TextToSpeechService {
    private static final String TAG = "CrispTtsService";
    private static final int SAMPLE_RATE = 24000;

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if (lang == null) return TextToSpeech.LANG_NOT_SUPPORTED;
        if (Locale.ENGLISH.getLanguage().equals(lang)) return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[] { Locale.ENGLISH.getLanguage(), Locale.US.getCountry(), "" };
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        long totalStart = System.nanoTime();
        TtsSettings settings = new TtsSettings(this);
        String modelPath = settings.modelPath();
        if (modelPath.isEmpty()) {
            Log.e(TAG, "No model configured");
            callback.error();
            return;
        }

        callback.start(SAMPLE_RATE, android.media.AudioFormat.ENCODING_PCM_16BIT, 1);
        PcmSynthesisResult result = NativeTtsEngine.synthesizePcm(this, modelPath, settings.voicePath(),
                settings.backend(), request.getCharSequenceText().toString(), settings.threads());
        TimingInfo timing = result.timingInfo();
        if (!timing.ok) {
            Log.e(TAG, "Synthesis failed: " + timing.format());
            callback.error();
            return;
        }
        int offset = 0;
        int max = callback.getMaxBufferSize();
        while (offset < result.pcm16.length) {
            int len = Math.min(max, result.pcm16.length - offset);
            callback.audioAvailable(result.pcm16, offset, len);
            offset += len;
        }
        long totalMs = (System.nanoTime() - totalStart) / 1_000_000L;
        Log.i(TAG, "TTS service delivered bytes=" + result.pcm16.length + " totalMs=" + totalMs + " " + timing.format());
        callback.done();
    }
}
