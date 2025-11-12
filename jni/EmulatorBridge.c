#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <mgba/core/core.h>
#include <mgba-util/vfs.h>

static struct mCore* core = NULL;

JNIEXPORT jboolean JNICALL
Java_org_example_project_MgbaBridge_init(JNIEnv* env, jclass clazz) {
    core = GBACoreCreate();
    if (!core) {
        fprintf(stderr, "Failed to create GBA core\n");
        return JNI_FALSE;
    }

    core->init(core);

    // Set up video and audio (required before loading ROM)
    struct mCoreOptions opts = {};
    mCoreConfigInit(&core->config, NULL);

    // Initialize video
    unsigned width, height;
    core->desiredVideoDimensions(core, &width, &height);
    fprintf(stderr, "Video dimensions: %ux%u\n", width, height);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_example_project_MgbaBridge_loadRom(JNIEnv* env, jclass clazz, jstring path) {
    if (!core) {
        fprintf(stderr, "Core not initialized\n");
        return JNI_FALSE;
    }

    const char* cpath = (*env)->GetStringUTFChars(env, path, 0);
    fprintf(stderr, "Loading ROM: %s\n", cpath);

    // Load ROM using VFile
    struct VFile* rom = VFileOpen(cpath, O_RDONLY);
    if (!rom) {
        fprintf(stderr, "Failed to open ROM file\n");
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return JNI_FALSE;
    }

    bool ok = core->loadROM(core, rom);
    if (!ok) {
        fprintf(stderr, "Failed to load ROM\n");
        rom->close(rom);
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return JNI_FALSE;
    }

    // Reset the core
    core->reset(core);
    fprintf(stderr, "ROM loaded and core reset successfully\n");

    (*env)->ReleaseStringUTFChars(env, path, cpath);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_org_example_project_MgbaBridge_runFrame(JNIEnv* env, jclass clazz) {
    if (!core) return;
    core->runFrame(core);
}

JNIEXPORT jobject JNICALL
Java_org_example_project_MgbaBridge_getFramebuffer(JNIEnv* env, jclass clazz) {
    if (!core) return NULL;

    unsigned width, height;
    core->desiredVideoDimensions(core, &width, &height);

    // Get the video buffer (RGB565 format)
    void* pixels = core->getVideoBuffer(core);
    if (!pixels) {
        fprintf(stderr, "Failed to get video buffer\n");
        return NULL;
    }

    // RGB565 = 2 bytes per pixel
    size_t bufferSize = width * height * 2;

    return (*env)->NewDirectByteBuffer(env, pixels, bufferSize);
}