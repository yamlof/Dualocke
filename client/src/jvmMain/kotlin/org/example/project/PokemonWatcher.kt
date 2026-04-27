package org.example.project


/**
 * Example Pokémon memory watcher — your first ported Lua hook.
 *
 * GBA RAM layout note:
 *   The GBA's EWRAM lives at game-space address 0x02000000.
 *   Libretro gives you a flat buffer for SYSTEM_RAM, so subtract 0x02000000
 *   from any in-game address to get the libretro offset.
 *
 *   Example: party Pokémon data in Pokémon Emerald is around 0x02024284 in-game,
 *   which is offset 0x24284 in the libretro buffer.
 *
 * IMPORTANT: The exact addresses below are placeholders for FireRed / Emerald.
 * You'll need to use the same addresses your old Lua scripts used,
 * just shifted to libretro offsets.
 */
class PokemonWatcher(private val core: LibretroCore) {

    // --- GBA EWRAM offset ---
    private val EWRAM_BASE = 0x02000000

    // --- Memory addresses (in-game space) ---
    // These are FireRed examples — check your old Lua scripts for the right ones
    private val PARTY_COUNT_ADDR = 0x02024284
    private val PARTY_DATA_ADDR  = 0x02024284 + 4
    private val ENCOUNTER_ADDR   = 0x02024064  // wild Pokémon during battle

    private val POKEMON_STRUCT_SIZE = 100

    // --- Tracked state ---
    private var lastPartyCount = 0
    private var lastShinySeen = false
    private var deathCount = 0

    // --- Listeners ---
    var onShinyDetected: ((personality: Int, otId: Int) -> Unit)? = null
    var onPokemonFainted: (() -> Unit)? = null
    var onPartyChanged: ((oldCount: Int, newCount: Int) -> Unit)? = null

    /** Call this once per emulated frame, after core.runFrame(). */
    fun pollFrame() {
        checkPartyCount()
        checkShinyEncounter()
        // checkFaints()  ... add more as you port them
    }

    // ============================================================
    // Convert in-game address → libretro offset
    // ============================================================

    private fun toOffset(gameAddress: Int): Int = gameAddress - EWRAM_BASE

    private fun readByte(gameAddress: Int): Int =
        core.readByte(toOffset(gameAddress))

    private fun readInt(gameAddress: Int): Int =
        core.readInt(toOffset(gameAddress))

    // ============================================================
    // Hooks (each one mirrors a Lua script you used to have)
    // ============================================================

    private fun checkPartyCount() {
        val count = readByte(PARTY_COUNT_ADDR) and 0xFF
        if (count != lastPartyCount) {
            onPartyChanged?.invoke(lastPartyCount, count)
            lastPartyCount = count
        }
    }

    private fun checkShinyEncounter() {
        val personality = readInt(ENCOUNTER_ADDR)
        val otId        = readInt(ENCOUNTER_ADDR + 4)

        // Skip if no encounter active (all zeros usually means no Pokémon there)
        if (personality == 0 && otId == 0) {
            lastShinySeen = false
            return
        }

        val shiny = isShiny(personality, otId)
        if (shiny && !lastShinySeen) {
            onShinyDetected?.invoke(personality, otId)
            lastShinySeen = true
        } else if (!shiny) {
            lastShinySeen = false
        }
    }

    // ============================================================
    // Game logic helpers
    // ============================================================

    /** Standard GBA-era shiny check: XOR of PID halves and OT ID halves < 8. */
    private fun isShiny(personality: Int, otId: Int): Boolean {
        val p1 = (personality ushr 16) and 0xFFFF
        val p2 = personality and 0xFFFF
        val o1 = (otId ushr 16) and 0xFFFF
        val o2 = otId and 0xFFFF
        return (p1 xor p2 xor o1 xor o2) < 8
    }
}

// ============================================================
// Wiring example — use this as a guide in your game loop
// ============================================================

/*
val core = LibretroCore()
val coreFile = NativeLoader.extractCore("mgba")
core.loadCore(coreFile)
core.loadGame("/path/to/firered.gba")

val watcher = PokemonWatcher(core).apply {
    onShinyDetected = { pid, ot ->
        println("✨ Shiny detected! PID=$pid OT=$ot")
        // TODO: notify UI, record event, manipulate catch rate, etc.
    }
    onPartyChanged = { old, new ->
        println("Party size: $old → $new")
    }
}

// Game loop
while (running) {
    val frame = core.runFrame()
    watcher.pollFrame()
    renderFrameToCompose(frame, core.frameWidth, core.frameHeight)
    delay(16) // ~60 FPS
}
*/