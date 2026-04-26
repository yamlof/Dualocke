// libretro_bridge.cpp
// JNI bridge for loading and running libretro cores from Kotlin.
// Cross-platform: macOS, Linux, Windows.

#include <jni.h>    // for talking to kotlin
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cstdint>
#include <cstdarg>


#include "libretro.h" // libretro api definitions

// ============================================================
// Platform-specific dynamic loading
// ============================================================

#ifdef _WIN32
#include <windows.h>
    typedef HMODULE CoreHandle;
    #define LOAD_CORE(path)        LoadLibraryA(path)
    #define LOAD_SYMBOL(h, name)   ((void*)GetProcAddress(h, name))
    #define UNLOAD_CORE(h)         FreeLibrary(h)
#else
#include <dlfcn.h>
typedef void* CoreHandle;
#define LOAD_CORE(path)        dlopen(path, RTLD_LAZY | RTLD_LOCAL)
#define LOAD_SYMBOL(h, name)   dlsym(h, name)
#define UNLOAD_CORE(h)         dlclose(h)
#endif

// ============================================================
// Function pointers loaded from the core
// ============================================================

static CoreHandle g_coreHandle = nullptr;

static void     (*p_retro_init)(void) = nullptr;
static void     (*p_retro_deinit)(void) = nullptr;
static unsigned (*p_retro_api_version)(void) = nullptr;
static void     (*p_retro_get_system_info)(struct retro_system_info*) = nullptr;
static void     (*p_retro_get_system_av_info)(struct retro_system_av_info*) = nullptr;
static void     (*p_retro_set_environment)(retro_environment_t) = nullptr;
static void     (*p_retro_set_video_refresh)(retro_video_refresh_t) = nullptr;
static void     (*p_retro_set_audio_sample)(retro_audio_sample_t) = nullptr;
static void     (*p_retro_set_audio_sample_batch)(retro_audio_sample_batch_t) = nullptr;
static void     (*p_retro_set_input_poll)(retro_input_poll_t) = nullptr;
static void     (*p_retro_set_input_state)(retro_input_state_t) = nullptr;
static void     (*p_retro_reset)(void) = nullptr;
static void     (*p_retro_run)(void) = nullptr;
static bool     (*p_retro_load_game)(const struct retro_game_info*) = nullptr;
static void     (*p_retro_unload_game)(void) = nullptr;
static void*    (*p_retro_get_memory_data)(unsigned) = nullptr;
static size_t   (*p_retro_get_memory_size)(unsigned) = nullptr;

// ============================================================
// Frontend state
// ============================================================

// Frame buffer — sized for any reasonable screen (max 1024x1024 RGBA)
static uint32_t g_frameBuffer[1024 * 1024];
static int      g_frameWidth = 0;
static int      g_frameHeight = 0;

// Input state — bitmask of pressed RetroPad buttons (id matches RETRO_DEVICE_ID_JOYPAD_*)
static uint16_t g_inputState = 0;

// ROM data — must stay alive while the game is loaded
static void*  g_romData = nullptr;
static size_t g_romSize = 0;

// Pixel format requested by the core
static enum retro_pixel_format g_pixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;

// System / save directories (libretro cores ask for these)
static char g_systemDir[1024] = "/tmp/retro_system";
static char g_saveDir[1024]   = "/tmp/retro_saves";

// ============================================================
// Color conversion helpers
// ============================================================

static inline uint32_t convert_0RGB1555(uint16_t c) {
    uint8_t r = ((c >> 10) & 0x1F) << 3;
    uint8_t g = ((c >>  5) & 0x1F) << 3;
    uint8_t b = ((c >>  0) & 0x1F) << 3;
    return 0xFF000000u | (r << 16) | (g << 8) | b;
}

static inline uint32_t convert_RGB565(uint16_t c) {
    uint8_t r = ((c >> 11) & 0x1F) << 3;
    uint8_t g = ((c >>  5) & 0x3F) << 2;
    uint8_t b = ((c >>  0) & 0x1F) << 3;
    return 0xFF000000u | (r << 16) | (g << 8) | b;
}

// ============================================================
// Libretro callbacks (called by the core)
// ============================================================

static void cb_video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0) return;
    if (width > 1024 || height > 1024) return;

    g_frameWidth  = (int)width;
    g_frameHeight = (int)height;

    // Convert to ARGB8888 for Java IntArray (each int = one pixel)
    if (g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        // Already correct format, just copy with pitch handling
        for (unsigned y = 0; y < height; y++) {
            const uint32_t* src = (const uint32_t*)((const uint8_t*)data + y * pitch);
            uint32_t* dst = &g_frameBuffer[y * width];
            for (unsigned x = 0; x < width; x++) {
                dst[x] = 0xFF000000u | src[x];
            }
        }
    } else if (g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
        for (unsigned y = 0; y < height; y++) {
            const uint16_t* src = (const uint16_t*)((const uint8_t*)data + y * pitch);
            uint32_t* dst = &g_frameBuffer[y * width];
            for (unsigned x = 0; x < width; x++) {
                dst[x] = convert_RGB565(src[x]);
            }
        }
    } else { // RETRO_PIXEL_FORMAT_0RGB1555
        for (unsigned y = 0; y < height; y++) {
            const uint16_t* src = (const uint16_t*)((const uint8_t*)data + y * pitch);
            uint32_t* dst = &g_frameBuffer[y * width];
            for (unsigned x = 0; x < width; x++) {
                dst[x] = convert_0RGB1555(src[x]);
            }
        }
    }
}

// Logger function passed to libretro cores via GET_LOG_INTERFACE.
// Must be a regular C function, not a lambda — variadic lambdas can't
// be converted to function pointers on GCC.
static void libretro_log(enum retro_log_level level, const char* fmt, ...) {
    if (level < RETRO_LOG_ERROR) return;

    va_list args;
    va_start(args, fmt);
    vfprintf(stderr, fmt, args);
    va_end(args);
    fflush(stderr);
}

static void cb_input_poll(void) {
    // Nothing to do — input state is set by Kotlin via nativeSetInput
}

static int16_t cb_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;
    if (device != RETRO_DEVICE_JOYPAD) return 0;
    if (id > 15) return 0;
    return (g_inputState >> id) & 1;
}

static void cb_audio_sample(int16_t left, int16_t right) {
    // TODO: queue audio for playback. Ignored for now.
    (void)left; (void)right;
}

static size_t cb_audio_sample_batch(const int16_t* data, size_t frames) {
    // TODO: queue audio for playback. Ignored for now.
    (void)data;
    return frames;
}

static bool cb_environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            enum retro_pixel_format fmt = *(enum retro_pixel_format*)data;
            // We support all three standard formats
            if (fmt == RETRO_PIXEL_FORMAT_0RGB1555 ||
                fmt == RETRO_PIXEL_FORMAT_XRGB8888 ||
                fmt == RETRO_PIXEL_FORMAT_RGB565) {
                g_pixelFormat = fmt;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char**)data = g_systemDir;
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char**)data = g_saveDir;
            return true;

        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool*)data = true;
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            struct retro_log_callback* cb = (struct retro_log_callback*)data;
            cb->log = libretro_log;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_GET_VARIABLE:
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
            // Politely refuse — defaults are fine
            return false;

        default:
            // Most env calls can be safely ignored
            return false;
    }
}

// ============================================================
// Helpers
// ============================================================

static void clear_function_pointers() {
    p_retro_init = nullptr;
    p_retro_deinit = nullptr;
    p_retro_api_version = nullptr;
    p_retro_get_system_info = nullptr;
    p_retro_get_system_av_info = nullptr;
    p_retro_set_environment = nullptr;
    p_retro_set_video_refresh = nullptr;
    p_retro_set_audio_sample = nullptr;
    p_retro_set_audio_sample_batch = nullptr;
    p_retro_set_input_poll = nullptr;
    p_retro_set_input_state = nullptr;
    p_retro_reset = nullptr;
    p_retro_run = nullptr;
    p_retro_load_game = nullptr;
    p_retro_unload_game = nullptr;
    p_retro_get_memory_data = nullptr;
    p_retro_get_memory_size = nullptr;
}

#define LOAD_FN(name) \
    p_##name = (decltype(p_##name))LOAD_SYMBOL(g_coreHandle, #name); \
    if (!p_##name) { \
        fprintf(stderr, "[libretro] Missing symbol: %s\n", #name); \
        fflush(stderr); \
        return false; \
    }

static bool load_all_symbols() {
    LOAD_FN(retro_init);
    LOAD_FN(retro_deinit);
    LOAD_FN(retro_api_version);
    LOAD_FN(retro_get_system_info);
    LOAD_FN(retro_get_system_av_info);
    LOAD_FN(retro_set_environment);
    LOAD_FN(retro_set_video_refresh);
    LOAD_FN(retro_set_audio_sample);
    LOAD_FN(retro_set_audio_sample_batch);
    LOAD_FN(retro_set_input_poll);
    LOAD_FN(retro_set_input_state);
    LOAD_FN(retro_reset);
    LOAD_FN(retro_run);
    LOAD_FN(retro_load_game);
    LOAD_FN(retro_unload_game);
    LOAD_FN(retro_get_memory_data);
    LOAD_FN(retro_get_memory_size);
    return true;
}

// ============================================================
// JNI exports
// IMPORTANT: Update the package path below to match YOUR Kotlin package!
// Current: org.example.dualocketest.LibretroCore
// ============================================================

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_example_project_LibretroCore_nativeLoadCore(
        JNIEnv* env, jobject /*self*/, jstring corePath) {

    if (g_coreHandle) {
        fprintf(stderr, "[libretro] A core is already loaded\n");
        fflush(stderr);
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(corePath, nullptr);
    if (!path) return JNI_FALSE;

    fprintf(stderr, "[libretro] Loading core: %s\n", path);
    fflush(stderr);

    g_coreHandle = LOAD_CORE(path);
    env->ReleaseStringUTFChars(corePath, path);

    if (!g_coreHandle) {
#ifndef _WIN32
        fprintf(stderr, "[libretro] dlopen failed: %s\n", dlerror());
#endif
        fflush(stderr);
        return JNI_FALSE;
    }

    if (!load_all_symbols()) {
        UNLOAD_CORE(g_coreHandle);
        g_coreHandle = nullptr;
        clear_function_pointers();
        return JNI_FALSE;
    }

    fprintf(stderr, "[libretro] API version: %u\n", p_retro_api_version());
    fflush(stderr);

    // Register callbacks BEFORE retro_init (some cores set state in environment during init)
    p_retro_set_environment(cb_environment);
    p_retro_set_video_refresh(cb_video_refresh);
    p_retro_set_audio_sample(cb_audio_sample);
    p_retro_set_audio_sample_batch(cb_audio_sample_batch);
    p_retro_set_input_poll(cb_input_poll);
    p_retro_set_input_state(cb_input_state);

    p_retro_init();

    fprintf(stderr, "[libretro] Core initialized\n");
    fflush(stderr);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_example_project_LibretroCore_nativeLoadGame(
        JNIEnv* env, jobject /*self*/, jstring romPath) {

    if (!g_coreHandle || !p_retro_load_game) return JNI_FALSE;

    const char* path = env->GetStringUTFChars(romPath, nullptr);
    if (!path) return JNI_FALSE;

    // Read ROM into memory (libretro keeps a pointer; we own the buffer)
    FILE* f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "[libretro] Cannot open ROM: %s\n", path);
        fflush(stderr);
        env->ReleaseStringUTFChars(romPath, path);
        return JNI_FALSE;
    }
    fseek(f, 0, SEEK_END);
    g_romSize = (size_t)ftell(f);
    fseek(f, 0, SEEK_SET);

    if (g_romData) free(g_romData);
    g_romData = malloc(g_romSize);
    if (!g_romData) {
        fclose(f);
        env->ReleaseStringUTFChars(romPath, path);
        return JNI_FALSE;
    }
    fread(g_romData, 1, g_romSize, f);
    fclose(f);

    struct retro_game_info info = {};
    info.path = path;
    info.data = g_romData;
    info.size = g_romSize;
    info.meta = nullptr;

    bool ok = p_retro_load_game(&info);
    env->ReleaseStringUTFChars(romPath, path);

    if (!ok) {
        fprintf(stderr, "[libretro] retro_load_game failed\n");
        fflush(stderr);
        free(g_romData);
        g_romData = nullptr;
        g_romSize = 0;
        return JNI_FALSE;
    }

    // Get system AV info — useful to know native screen size
    struct retro_system_av_info av = {};
    p_retro_get_system_av_info(&av);
    fprintf(stderr, "[libretro] Game loaded. Native size: %ux%u, fps: %.2f\n",
            av.geometry.base_width, av.geometry.base_height, av.timing.fps);
    fflush(stderr);

    g_frameWidth  = (int)av.geometry.base_width;
    g_frameHeight = (int)av.geometry.base_height;

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_org_example_project_LibretroCore_nativeRunFrame(
        JNIEnv* env, jobject /*self*/, jintArray outBuffer) {

if (!g_coreHandle || !p_retro_run) return;

p_retro_run();

// Copy frame buffer to Java array
jsize bufLen = env->GetArrayLength(outBuffer);
int pixels = g_frameWidth * g_frameHeight;
if (pixels > bufLen) pixels = bufLen;

env->SetIntArrayRegion(outBuffer, 0, pixels, (const jint*)g_frameBuffer);
}

JNIEXPORT jint JNICALL
Java_org_example_project_LibretroCore_nativeGetFrameWidth(
        JNIEnv* /*env*/, jobject /*self*/) {
    return g_frameWidth;
}

JNIEXPORT jint JNICALL
Java_org_example_project_LibretroCore_nativeGetFrameHeight(
        JNIEnv* /*env*/, jobject /*self*/) {
    return g_frameHeight;
}

JNIEXPORT void JNICALL
Java_org_example_project_LibretroCore_nativeSetInput(
        JNIEnv* /*env*/, jobject /*self*/, jint state) {
g_inputState = (uint16_t)state;
}

// Read a byte from the core's main system RAM.
// `id` is one of:
//   0 = RETRO_MEMORY_SAVE_RAM
//   1 = RETRO_MEMORY_RTC
//   2 = RETRO_MEMORY_SYSTEM_RAM
//   3 = RETRO_MEMORY_VIDEO_RAM
JNIEXPORT jint JNICALL
        Java_org_example_project_LibretroCore_nativeReadByte(
        JNIEnv* /*env*/, jobject /*self*/, jint memoryId, jint offset) {

if (!g_coreHandle || !p_retro_get_memory_data) return 0;

uint8_t* mem = (uint8_t*)p_retro_get_memory_data((unsigned)memoryId);
size_t size = p_retro_get_memory_size((unsigned)memoryId);
if (!mem || (size_t)offset >= size) return 0;

return (jint)mem[offset];
}

JNIEXPORT void JNICALL
Java_org_example_project_LibretroCore_nativeWriteByte(
        JNIEnv* /*env*/, jobject /*self*/, jint memoryId, jint offset, jint value) {

if (!g_coreHandle || !p_retro_get_memory_data) return;

uint8_t* mem = (uint8_t*)p_retro_get_memory_data((unsigned)memoryId);
size_t size = p_retro_get_memory_size((unsigned)memoryId);
if (!mem || (size_t)offset >= size) return;

mem[offset] = (uint8_t)(value & 0xFF);
}

JNIEXPORT void JNICALL
Java_org_example_project_LibretroCore_nativeReset(
        JNIEnv* /*env*/, jobject /*self*/) {
if (g_coreHandle && p_retro_reset) p_retro_reset();
}

JNIEXPORT void JNICALL
Java_org_example_project_LibretroCore_nativeUnloadGame(
        JNIEnv* /*env*/, jobject /*self*/) {
if (g_coreHandle && p_retro_unload_game) {
p_retro_unload_game();
}
if (g_romData) {
free(g_romData);
g_romData = nullptr;
g_romSize = 0;
}
}

JNIEXPORT void JNICALL
Java_org_example_project_LibretroCore_nativeUnloadCore(
        JNIEnv* /*env*/, jobject /*self*/) {
if (g_coreHandle) {
if (p_retro_deinit) p_retro_deinit();
UNLOAD_CORE(g_coreHandle);
g_coreHandle = nullptr;
}
clear_function_pointers();
if (g_romData) {
free(g_romData);
g_romData = nullptr;
g_romSize = 0;
}
}

} // extern "C"