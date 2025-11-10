#include <jni.h>
#include <mgba/core/core.h>
#include <mgba/core/interface.h>
#include <stdio.h>

static struct mCore* core = NULL;

JNIEXPORT void JNICALL
Java_com_example_mgba_MgbaBridge_init(JNIEnv* env, jobject thiz) {
core = mCoreFind("gba");
if (core) {
core->init(core);
printf("mGBA initialized\n");
}
}

JNIEXPORT void JNICALL
Java_com_example_mgba_MgbaBridge_loadRom(JNIEnv* env, jobject thiz, jstring romPath) {
const char* path = (*env)->GetStringUTFChars(env, romPath, 0);
if (core && core->loadROM(core, path)) {
printf("Loaded ROM: %s\n", path);
} else {
printf("Failed to load ROM\n");
}
(*env)->ReleaseStringUTFChars(env, romPath, path);
}

JNIEXPORT void JNICALL
Java_com_example_mgba_MgbaBridge_step(JNIEnv* env, jobject thiz) {
if (core) {
core->runFrame(core);
}
}

JNIEXPORT jobject JNICALL
        Java_com_example_mgba_MgbaBridge_getFramebuffer(JNIEnv* env, jobject thiz) {
if (!core) return NULL;
const void* fb = core->getFramebuffer(core);
return (*env)->NewDirectByteBuffer(env, (void*)fb, 240 * 160 * 4); // GBA: 240x160 RGBA
}