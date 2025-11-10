object MgbaBridge {
    init {
        System.loadLibrary("/Users/edwinigbinoba/Documents/code/Dualocke/jni/mgba/build") // loads libmgba_jni.so
    }

    external fun init()
    external fun loadRom(path: String)
    external fun getFramebuffer(): ByteBuffer
    external fun step()
}