package org.example.project

import org.example.project.emulator.EmulatorSession

/**
 * App-level container for objects whose lifetime should match the application,
 * not any single screen.
 *
 * Created once in main() and passed down explicitly. Each screen that needs
 * one of these takes it as a parameter — no DI framework, no globals, no
 * CompositionLocals. If this gets unwieldy as the app grows we can replace
 * it with a real DI setup; for now this is the simplest thing that works.
 *
 * Why this exists: the EmulatorSession needs to outlive HomeScreen — when the
 * user navigates from Home to the emulator screen, the game must keep running.
 * Owning the session here ensures it survives any single screen's lifecycle.
 */
class AppContainer {
    val emulatorSession: EmulatorSession = EmulatorSession()

    /** Call once on app shutdown to release native resources cleanly. */
    fun dispose() {
        emulatorSession.dispose()
    }
}