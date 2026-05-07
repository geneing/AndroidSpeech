#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <map>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "kokoro.h"
#include "qwen3_tts.h"
#include "vibevoice.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CrispNativeTts", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "CrispNativeTts", __VA_ARGS__)

#ifndef CRISP_ANDROID_CMAKE_BUILD_TYPE
#define CRISP_ANDROID_CMAKE_BUILD_TYPE "unknown"
#endif

#ifndef CRISP_ANDROID_OPTIMIZED_DEBUG
#define CRISP_ANDROID_OPTIMIZED_DEBUG 0
#endif

namespace {
using Clock = std::chrono::steady_clock;

struct Timer {
    Clock::time_point start = Clock::now();
    long ms() const {
        return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - start).count();
    }
};

struct Result {
    bool ok = false;
    std::string message;
    int sample_rate = 24000;
    int samples = 0;
    long load_ms = 0;
    long voice_ms = 0;
    long synth_ms = 0;
    long write_ms = 0;
    long total_ms = 0;

    std::string serialize() const {
        std::ostringstream out;
        out << "ok=" << (ok ? "true" : "false") << "\n";
        out << "message=" << message << "\n";
        out << "sampleRate=" << sample_rate << "\n";
        out << "samples=" << samples << "\n";
        out << "loadMs=" << load_ms << "\n";
        out << "voiceMs=" << voice_ms << "\n";
        out << "synthMs=" << synth_ms << "\n";
        out << "pcmConvertMs=" << write_ms << "\n";
        out << "nativeTotalMs=" << total_ms << "\n";
        return out.str();
    }
};

struct NativeResult {
    Result timing;
    std::vector<int16_t> pcm16;
};

struct KokoroCache {
    std::mutex mu;
    kokoro_context* ctx = nullptr;
    std::string model;
    std::string voice;
    bool use_gpu = false;
    int threads = 0;

    ~KokoroCache() {
        if (ctx) {
            kokoro_free(ctx);
            ctx = nullptr;
        }
    }
};

KokoroCache g_kokoro_cache;

std::string jstring_to_string(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::string lowercase(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return s;
}

std::vector<int16_t> pcm_to_pcm16(const float* pcm, int n_samples) {
    std::vector<int16_t> pcm16(n_samples);
    for (int i = 0; i < n_samples; ++i) {
        float v = std::max(-1.0f, std::min(1.0f, pcm[i]));
        pcm16[i] = static_cast<int16_t>(std::lrintf(v * 32767.0f));
    }
    return pcm16;
}

std::string ascii_lower_alnum_space(const std::string& text) {
    std::string out;
    out.reserve(text.size());
    bool last_space = true;
    for (unsigned char ch : text) {
        if (ch >= 'A' && ch <= 'Z') {
            out.push_back(static_cast<char>(ch - 'A' + 'a'));
            last_space = false;
        } else if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
            out.push_back(static_cast<char>(ch));
            last_space = false;
        } else if (!last_space) {
            out.push_back(' ');
            last_space = true;
        }
    }
    while (!out.empty() && out.back() == ' ') out.pop_back();
    return out;
}

std::string kokoro_android_fallback_phonemes(const std::string& text) {
    static const std::map<std::string, std::string> dict = {
            {"a", "ə"},          {"able", "ˈeɪbəl"},     {"about", "əˈbaʊt"},
            {"android", "ˈændɹɔɪd"}, {"and", "ænd"},       {"are", "ɑɹ"},
            {"asr", "ˌeɪ ˌɛs ˈɑɹ"}, {"audio", "ˈɔdiˌoʊ"}, {"can", "kæn"},
            {"crisp", "kɹɪsp"}, {"device", "dɪˈvaɪs"}, {"engine", "ˈɛndʒən"},
            {"gguf", "ˌdʒi ˌdʒi ˌju ˈɛf"}, {"hello", "həˈloʊ"}, {"is", "ɪz"},
            {"local", "ˈloʊkəl"}, {"model", "ˈmɑdəl"}, {"models", "ˈmɑdəlz"},
            {"on", "ɑn"},       {"running", "ˈɹʌnɪŋ"}, {"speech", "spiːtʃ"},
            {"speak", "spiːk"}, {"test", "tɛst"},     {"text", "tɛkst"},
            {"the", "ðə"},      {"this", "ðɪs"},      {"to", "tə"},
            {"tts", "ˌti ˌti ˈɛs"}, {"using", "ˈjuzɪŋ"}, {"voice", "vɔɪs"},
            {"with", "wɪð"},    {"world", "wɜɹld"},
            {"0", "ˈzɪɹoʊ"},    {"1", "wʌn"},        {"2", "tu"},
            {"3", "θɹi"},       {"4", "fɔɹ"},        {"5", "faɪv"},
            {"6", "sɪks"},      {"7", "ˈsɛvən"},     {"8", "eɪt"},
            {"9", "naɪn"},
    };
    static const std::map<char, std::string> letters = {
            {'a', "æ"}, {'b', "b"}, {'c', "k"}, {'d', "d"}, {'e', "ɛ"}, {'f', "f"},
            {'g', "ɡ"}, {'h', "h"}, {'i', "ɪ"}, {'j', "dʒ"}, {'k', "k"}, {'l', "l"},
            {'m', "m"}, {'n', "n"}, {'o', "ɑ"}, {'p', "p"}, {'q', "k"}, {'r', "ɹ"},
            {'s', "s"}, {'t', "t"}, {'u', "ʌ"}, {'v', "v"}, {'w', "w"}, {'x', "ks"},
            {'y', "j"}, {'z', "z"},
    };

    std::string clean = ascii_lower_alnum_space(text);
    std::istringstream words(clean);
    std::string word;
    std::string out;
    while (words >> word) {
        auto hit = dict.find(word);
        std::string phon;
        if (hit != dict.end()) {
            phon = hit->second;
        } else {
            for (size_t i = 0; i < word.size(); ++i) {
                char c = word[i];
                auto lit = letters.find(c);
                if (lit == letters.end()) continue;
                if (!phon.empty()) phon += " ";
                phon += lit->second;
            }
        }
        if (!phon.empty()) {
            if (!out.empty()) out += " ";
            out += phon;
        }
    }
    return out;
}

NativeResult synthesize_kokoro(const std::string& model, const std::string& voice, bool use_gpu,
                               const std::string& text, int threads) {
    NativeResult nr;
    Result& r = nr.timing;
    Timer total;

    std::unique_lock<std::mutex> cache_lock(g_kokoro_cache.mu);
    const bool cache_hit = g_kokoro_cache.ctx &&
                           g_kokoro_cache.model == model &&
                           g_kokoro_cache.voice == voice &&
                           g_kokoro_cache.use_gpu == use_gpu &&
                           g_kokoro_cache.threads == threads;
    kokoro_context* ctx = g_kokoro_cache.ctx;
    if (!cache_hit) {
        if (g_kokoro_cache.ctx) {
            kokoro_free(g_kokoro_cache.ctx);
            g_kokoro_cache.ctx = nullptr;
        }

        kokoro_context_params params = kokoro_context_default_params();
        params.n_threads = threads;
        params.verbosity = 2;
        params.use_gpu = use_gpu;
        std::snprintf(params.espeak_lang, sizeof(params.espeak_lang), "%s", "en-us");

        Timer load;
        ctx = kokoro_init_from_file(model.c_str(), params);
        r.load_ms = load.ms();
        if (!ctx) {
            r.message = "kokoro_init_from_file failed";
            r.total_ms = total.ms();
            return nr;
        }

        if (!voice.empty()) {
            Timer voice_timer;
            int voice_rc = kokoro_load_voice_pack(ctx, voice.c_str());
            r.voice_ms = voice_timer.ms();
            if (voice_rc != 0) {
                kokoro_free(ctx);
                r.message = "kokoro_load_voice_pack failed rc=" + std::to_string(voice_rc);
                r.total_ms = total.ms();
                return nr;
            }
        }

        g_kokoro_cache.ctx = ctx;
        g_kokoro_cache.model = model;
        g_kokoro_cache.voice = voice;
        g_kokoro_cache.use_gpu = use_gpu;
        g_kokoro_cache.threads = threads;
        LOGI("kokoro cache miss loaded model=%s voice=%s backend=%s", model.c_str(), voice.c_str(),
             use_gpu ? "VULKAN" : "CPU");
    } else {
        r.load_ms = 0;
        r.voice_ms = 0;
        LOGI("kokoro cache hit model=%s voice=%s backend=%s", model.c_str(), voice.c_str(),
             use_gpu ? "VULKAN" : "CPU");
    }

    int n = 0;
    Timer synth;
    float* pcm = nullptr;
    std::string fallback = kokoro_android_fallback_phonemes(text);
    if (!fallback.empty()) {
        int n_ids = 0;
        int32_t* ids = kokoro_phonemes_to_ids(ctx, fallback.c_str(), &n_ids);
        std::free(ids);
        LOGI("kokoro Android phoneme fallback chars=%zu tokenIds=%d phonemes='%s'",
             fallback.size(), n_ids, fallback.c_str());
        if (n_ids > 0) {
            pcm = kokoro_synthesize_phonemes(ctx, fallback.c_str(), &n);
        }
    }
    if (!pcm || n <= 0) {
        LOGI("kokoro Android phoneme fallback failed; trying native text phonemizer");
        pcm = kokoro_synthesize(ctx, text.c_str(), &n);
    }
    r.synth_ms = synth.ms();
    if (!pcm || n <= 0) {
        r.message = "kokoro_synthesize failed; Android phoneme fallback and native phonemizer produced no audio";
        r.total_ms = total.ms();
        return nr;
    }

    Timer write;
    nr.pcm16 = pcm_to_pcm16(pcm, n);
    r.write_ms = write.ms();
    kokoro_pcm_free(pcm);
    r.ok = !nr.pcm16.empty();
    r.samples = n;
    r.message = r.ok ? "kokoro synthesized" : "failed to convert PCM";
    r.total_ms = total.ms();
    return nr;
}

NativeResult synthesize_vibevoice(const std::string& model, const std::string& voice, bool use_gpu,
                                  const std::string& text, int threads) {
    NativeResult nr;
    Result& r = nr.timing;
    Timer total;
    vibevoice_context_params params = vibevoice_context_default_params();
    params.n_threads = threads;
    params.verbosity = 2;
    params.use_gpu = use_gpu;
    params.tts_steps = 12;

    Timer load;
    vibevoice_context* ctx = vibevoice_init_from_file(model.c_str(), params);
    r.load_ms = load.ms();
    if (!ctx) {
        r.message = "vibevoice_init_from_file failed";
        r.total_ms = total.ms();
        return nr;
    }

    if (!voice.empty()) {
        Timer voice_timer;
        int voice_rc = vibevoice_load_voice(ctx, voice.c_str());
        r.voice_ms = voice_timer.ms();
        if (voice_rc != 0) {
            vibevoice_free(ctx);
            r.message = "vibevoice_load_voice failed rc=" + std::to_string(voice_rc);
            r.total_ms = total.ms();
            return nr;
        }
    }

    int n = 0;
    Timer synth;
    float* pcm = vibevoice_synthesize(ctx, text.c_str(), &n);
    r.synth_ms = synth.ms();
    if (!pcm || n <= 0) {
        vibevoice_free(ctx);
        r.message = "vibevoice_synthesize failed; model may lack decoder tensors";
        r.total_ms = total.ms();
        return nr;
    }

    Timer write;
    nr.pcm16 = pcm_to_pcm16(pcm, n);
    r.write_ms = write.ms();
    std::free(pcm);
    vibevoice_free(ctx);
    r.ok = !nr.pcm16.empty();
    r.samples = n;
    r.message = r.ok ? "vibevoice synthesized" : "failed to convert PCM";
    r.total_ms = total.ms();
    return nr;
}

NativeResult synthesize_qwen3(const std::string& model, const std::string& voice, bool use_gpu,
                              const std::string& text, int threads) {
    NativeResult nr;
    Result& r = nr.timing;
    Timer total;
    qwen3_tts_context_params params = qwen3_tts_context_default_params();
    params.n_threads = threads;
    params.verbosity = 2;
    params.use_gpu = use_gpu;
    params.temperature = 0.0f;

    Timer load;
    qwen3_tts_context* ctx = qwen3_tts_init_from_file(model.c_str(), params);
    r.load_ms = load.ms();
    if (!ctx) {
        r.message = "qwen3_tts_init_from_file failed";
        r.total_ms = total.ms();
        return nr;
    }

    if (!voice.empty()) {
        Timer voice_timer;
        int voice_rc = qwen3_tts_load_voice_pack(ctx, voice.c_str());
        r.voice_ms = voice_timer.ms();
        if (voice_rc != 0) {
            LOGI("qwen3_tts_load_voice_pack rc=%d; continuing for fixed-speaker/VoiceDesign models", voice_rc);
        }
    }

    int n = 0;
    Timer synth;
    float* pcm = qwen3_tts_synthesize(ctx, text.c_str(), &n);
    r.synth_ms = synth.ms();
    if (!pcm || n <= 0) {
        qwen3_tts_free(ctx);
        r.message = "qwen3_tts_synthesize returned no PCM; codec decoder/model assets may be incomplete";
        r.total_ms = total.ms();
        return nr;
    }

    Timer write;
    nr.pcm16 = pcm_to_pcm16(pcm, n);
    r.write_ms = write.ms();
    qwen3_tts_pcm_free(pcm);
    qwen3_tts_free(ctx);
    r.ok = !nr.pcm16.empty();
    r.samples = n;
    r.message = r.ok ? "qwen3_tts synthesized" : "failed to convert PCM";
    r.total_ms = total.ms();
    return nr;
}

}

extern "C" JNIEXPORT jobject JNICALL
Java_com_crispasr_androidspeech_core_NativeTtsEngine_nativeSynthesizePcm(
        JNIEnv* env, jclass, jstring j_model, jstring j_voice, jstring j_backend,
        jstring j_text, jint j_threads) {
    std::string model = jstring_to_string(env, j_model);
    std::string voice = jstring_to_string(env, j_voice);
    std::string backend = jstring_to_string(env, j_backend);
    std::string text = jstring_to_string(env, j_text);

    bool use_gpu = lowercase(backend).find("vulkan") != std::string::npos;
    int threads = std::max(1, static_cast<int>(j_threads));
    std::string lower_model = lowercase(model);
    LOGI("native synth start model=%s voice=%s backend=%s use_gpu=%d threads=%d chars=%zu cmakeBuildType=%s optimizedDebug=%d",
         model.c_str(), voice.c_str(), backend.c_str(), use_gpu ? 1 : 0, threads, text.size(),
         CRISP_ANDROID_CMAKE_BUILD_TYPE, CRISP_ANDROID_OPTIMIZED_DEBUG);

    NativeResult native_result;
    Result& result = native_result.timing;
    if (model.empty()) {
        result.message = "model path is empty";
    } else if (lower_model.find("kokoro") != std::string::npos) {
        native_result = synthesize_kokoro(model, voice, use_gpu, text, threads);
    } else if (lower_model.find("vibevoice") != std::string::npos) {
        native_result = synthesize_vibevoice(model, voice, use_gpu, text, threads);
    } else if (lower_model.find("qwen3") != std::string::npos && lower_model.find("tts") != std::string::npos) {
        native_result = synthesize_qwen3(model, voice, use_gpu, text, threads);
    } else {
        result.message = "unsupported GGUF filename; expected kokoro, vibevoice, or qwen3-tts";
    }

    std::string serialized = native_result.timing.serialize();
    if (native_result.timing.ok) {
        LOGI("native synth done %s", serialized.c_str());
    } else {
        LOGE("native synth failed %s", serialized.c_str());
    }

    jbyteArray pcm = env->NewByteArray(static_cast<jsize>(native_result.pcm16.size() * sizeof(int16_t)));
    if (pcm && !native_result.pcm16.empty()) {
        env->SetByteArrayRegion(pcm, 0, static_cast<jsize>(native_result.pcm16.size() * sizeof(int16_t)),
                                reinterpret_cast<const jbyte*>(native_result.pcm16.data()));
    }
    jstring timing = env->NewStringUTF(serialized.c_str());
    jclass cls = env->FindClass("com/crispasr/androidspeech/core/PcmSynthesisResult");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    return env->NewObject(cls, ctor, pcm, timing);
}
