class MgbaWrapper {
    init {
        System.loadLibrary("libnative-mgba") // Loads libmgba_jni.so
    }

    external fun init(): Boolean
    external fun loadRom(path: String): Boolean
    external fun runFrame()
    external fun getFramebuffer(): java.nio.ByteBuffer
}