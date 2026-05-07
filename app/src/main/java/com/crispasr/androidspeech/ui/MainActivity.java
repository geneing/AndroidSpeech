package com.crispasr.androidspeech.ui;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.crispasr.androidspeech.core.Backend;
import com.crispasr.androidspeech.core.ModelCatalog;
import com.crispasr.androidspeech.core.ModelEntry;
import com.crispasr.androidspeech.core.NativeTtsEngine;
import com.crispasr.androidspeech.core.PcmSynthesisResult;
import com.crispasr.androidspeech.core.TimingInfo;
import com.crispasr.androidspeech.core.TtsSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String TAG = "CrispTtsUi";

    private final List<ModelEntry> models = new ArrayList<>();
    private final List<ModelEntry> voices = new ArrayList<>();
    private Spinner modelSpinner;
    private Spinner voiceSpinner;
    private RadioGroup backendGroup;
    private EditText textInput;
    private EditText threadsInput;
    private TextView output;
    private Button speakButton;
    private TtsSettings settings;
    private AudioTrack player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        settings = new TtsSettings(this);
        buildUi();
        refreshModels();
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(0xFFFAFAFA);

        modelSpinner = new Spinner(this);
        modelSpinner.setMinimumHeight(dp(40));
        voiceSpinner = new Spinner(this);
        voiceSpinner.setMinimumHeight(dp(40));
        backendGroup = new RadioGroup(this);
        backendGroup.setOrientation(RadioGroup.HORIZONTAL);
        backendGroup.addView(radio("CPU", 1));
        backendGroup.addView(radio("Vulkan", 2));
        backendGroup.check(settings.backend() == Backend.VULKAN ? 2 : 1);

        threadsInput = new EditText(this);
        threadsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        threadsInput.setText(String.valueOf(settings.threads()));
        threadsInput.setHint("Threads");
        threadsInput.setSingleLine(true);
        threadsInput.setMinHeight(dp(40));

        textInput = new EditText(this);
        textInput.setMinLines(2);
        textInput.setMaxLines(4);
        textInput.setGravity(Gravity.TOP);
        textInput.setText("Hello from Crisp Android speech.");

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> refreshModels());

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> saveSettings());

        speakButton = new Button(this);
        speakButton.setText("Speak");
        speakButton.setOnClickListener(v -> synthesize());

        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(12);
        output.setMaxLines(10);

        TextView title = new TextView(this);
        title.setText("Crisp GGUF TTS");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setPadding(dp(12), dp(8) + topInset(), dp(12), dp(8));
        title.setBackgroundColor(0xFFFFFFFF);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(8) + bottomInset());
        bottomBar.setBackgroundColor(0xFFFFFFFF);
        addTaskbarButton(bottomBar, refresh);
        addTaskbarButton(bottomBar, save);
        addTaskbarButton(bottomBar, speakButton);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(12));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        root.addView(label("Model"));
        root.addView(modelSpinner, matchWrap());
        root.addView(label("Voice"));
        root.addView(voiceSpinner, matchWrap());
        root.addView(label("Backend"));
        root.addView(backendGroup, matchWrap());
        root.addView(threadsInput, matchWrap());
        root.addView(textInput, matchWrap());
        root.addView(output, matchWrap());

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        scroller.addView(root, matchWrap());

        screen.addView(title, matchWrap());
        screen.addView(scroller, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        screen.addView(bottomBar, matchWrap());
        setContentView(screen);
    }

    private TextView radio(String text, int id) {
        android.widget.RadioButton button = new android.widget.RadioButton(this);
        button.setText(text);
        button.setId(id);
        button.setTextSize(13);
        button.setMinHeight(dp(36));
        return button;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(12);
        v.setPadding(0, dp(6), 0, 0);
        return v;
    }

    private LinearLayout row(View... views) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (View view : views) {
            if (view instanceof Button) {
                Button button = (Button) view;
                button.setTextSize(12);
                button.setMinHeight(0);
                button.setMinWidth(0);
                button.setPadding(dp(10), 0, dp(10), 0);
            }
            row.addView(view, new LinearLayout.LayoutParams(0, dp(36), 1f));
        }
        return row;
    }

    private void addTaskbarButton(LinearLayout taskbar, Button button) {
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        taskbar.addView(button, new LinearLayout.LayoutParams(dp(76), dp(36)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int topInset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            return insets == null ? 0 : insets.getStableInsetTop();
        }
        return 0;
    }

    private int bottomInset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            return insets == null ? 0 : insets.getStableInsetBottom();
        }
        return 0;
    }

    private void refreshModels() {
        List<ModelEntry> entries = ModelCatalog.scan(this);
        models.clear();
        voices.clear();
        for (ModelEntry entry : entries) {
            if (entry.isVoicePack()) voices.add(entry);
            else models.add(entry);
        }
        modelSpinner.setAdapter(adapter(models, true));
        voiceSpinner.setAdapter(adapter(voices, false));
        selectSaved(modelSpinner, models, settings.modelPath());
        selectSaved(voiceSpinner, voices, settings.voicePath());
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ModelEntry model = selectedModel();
                ModelEntry voice = model == null ? null : ModelCatalog.firstVoiceFor(model.type, voices);
                if (voice != null) selectSaved(voiceSpinner, voices, voice.file.getAbsolutePath());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        File dir = ModelCatalog.sharedModelDirectory(this);
        output.setText("Models: " + models.size() + " Voices: " + voices.size() + "\nPush GGUF files to:\n"
                + dir.getAbsolutePath());
    }

    private ArrayAdapter<String> adapter(List<ModelEntry> entries, boolean includeMissing) {
        List<String> names = new ArrayList<>();
        if (entries.isEmpty() && includeMissing) names.add("No GGUF models found");
        for (ModelEntry entry : entries) names.add(entry.displayName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void selectSaved(Spinner spinner, List<ModelEntry> entries, String path) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).file.getAbsolutePath().equals(path)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private ModelEntry selectedModel() {
        int pos = modelSpinner.getSelectedItemPosition();
        return pos >= 0 && pos < models.size() ? models.get(pos) : null;
    }

    private ModelEntry selectedVoice() {
        int pos = voiceSpinner.getSelectedItemPosition();
        return pos >= 0 && pos < voices.size() ? voices.get(pos) : null;
    }

    private Backend selectedBackend() {
        return backendGroup.getCheckedRadioButtonId() == 2 ? Backend.VULKAN : Backend.CPU;
    }

    private int selectedThreads() {
        try {
            return TtsSettings.normalizeThreads(Integer.parseInt(threadsInput.getText().toString()));
        } catch (NumberFormatException e) {
            return settings.threads();
        }
    }

    private void saveSettings() {
        ModelEntry model = selectedModel();
        ModelEntry voice = selectedVoice();
        settings.save(model == null ? "" : model.file.getAbsolutePath(), voice == null ? "" : voice.file.getAbsolutePath(),
                selectedBackend(), selectedThreads());
        threadsInput.setText(String.valueOf(settings.threads()));
        output.setText("Saved\n" + (model == null ? "" : model.file.getAbsolutePath()));
    }

    private void synthesize() {
        ModelEntry model = selectedModel();
        if (model == null) {
            output.setText("No model selected");
            return;
        }
        saveSettings();
        ModelEntry voice = selectedVoice();
        String modelPath = model.file.getAbsolutePath();
        String voicePath = voice == null ? "" : voice.file.getAbsolutePath();
        Backend backend = selectedBackend();
        String text = textInput.getText().toString();
        int threads = selectedThreads();
        speakButton.setEnabled(false);
        output.setText("Synthesizing...\n" + model.displayName());
        new Thread(() -> {
            long start = System.nanoTime();
            PcmSynthesisResult result = NativeTtsEngine.synthesizePcm(this, modelPath, voicePath, backend, text, threads);
            TimingInfo info = result.timingInfo();
            long uiMs = (System.nanoTime() - start) / 1_000_000L;
            info.millis.put("uiCallMs", uiMs);
            Log.i(TAG, "UI synth " + info.format());
            runOnUiThread(() -> {
                output.setText(info.format() + "\npcmBytes=" + result.pcm16.length);
                speakButton.setEnabled(true);
                if (info.ok) play(result.pcm16, info.sampleRate);
            });
        }, "CrispTtsSynth").start();
    }

    private void play(byte[] pcm16, int sampleRate) {
        try {
            if (player != null) player.release();
            player = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm16.length)
                    .build();
            player.write(pcm16, 0, pcm16.length);
            player.play();
        } catch (Exception e) {
            output.append("\nPlayback failed: " + e.getMessage());
        }
    }
}
