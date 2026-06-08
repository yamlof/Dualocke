package org.example.project.memory

/**
 * FireRed (USA v1.0) RAM addresses and struct layouts.
 *
 * Sources:
 *   - Datacrystal RAM map: https://datacrystal.tcrf.net/wiki/Pok%C3%A9mon_3rd_Generation/Pok%C3%A9mon_FireRed_and_LeafGreen/RAM_map
 *   - Bulbapedia Gen 3 Pokémon data structure
 *   - pret/pokefirered decompilation
 *
 * Important:
 *   - FireRed v1.1 shifts most addresses slightly. If we ever need to support
 *     v1.1, this file is the only place that should change.
 *   - Libretro's SYSTEM_RAM region for GBA is the EWRAM block (256KB) starting
 *     at game-space 0x02000000. To convert a documented game-space address to a
 *     SYSTEM_RAM offset, subtract 0x02000000.
 *   - gPlayerParty and gPlayerPartyCount are static (non-DMA-protected) — safe
 *     to read directly without chasing pointers in IWRAM.
 */
object FireRedAddresses {

    /** Subtract this from a game-space EWRAM address to get a SYSTEM_RAM offset. */
    const val EWRAM_BASE: Int = 0x02000000

    /** Number of Pokémon currently in the player's party (0–6). 1 byte. */
    const val PLAYER_PARTY_COUNT: Int = 0x02024029 - EWRAM_BASE

    /** Start of the 6 × 100-byte player party array. */
    const val PLAYER_PARTY: Int = 0x02024284 - EWRAM_BASE

    /** Size of one Pokémon entry in the party (vs. 80 bytes in PC boxes). */
    const val PARTY_MON_SIZE: Int = 100

    const val MAX_PARTY: Int = 6
}

/**
 * Offsets within a single 100-byte party-Pokémon struct.
 *
 * Layout:
 *   - Bytes  0..31 : unencrypted header (PID, OTID, nickname, language, misc, checksum)
 *   - Bytes 32..79 : encrypted block — four 12-byte substructures permuted by PID%24,
 *                    XOR'd with (PID xor OTID). Decoded by Gen3Decryption.
 *   - Bytes 80..99 : unencrypted tail — status, level, mail, HP, stats.
 */
object PartyMonOffsets {
    // Unencrypted header
    const val PERSONALITY: Int = 0   // u32 — PID
    const val OT_ID: Int = 4         // u32 — Original Trainer ID
    const val NICKNAME: Int = 8      // 10 bytes — proprietary GBA char encoding
    const val LANGUAGE: Int = 18     // u8
    const val MISC_FLAGS: Int = 19   // u8 — bit 0: is bad egg, bit 1: has species, bit 2: is egg
    const val OT_NAME: Int = 20      // 7 bytes
    const val MARKINGS: Int = 27     // u8
    const val CHECKSUM: Int = 28     // u16
    // 30..31: unused/padding

    // Encrypted region
    const val ENCRYPTED_BLOCK: Int = 32        // 48 bytes total: 4 substructures × 12 bytes
    const val SUBSTRUCT_SIZE: Int = 12

    // Unencrypted tail
    const val STATUS: Int = 80       // u32 — sleep counter / poison / burn / etc.
    const val LEVEL: Int = 84        // u8
    const val MAIL: Int = 85         // u8 — mail ID, 0xFF if none
    const val CURRENT_HP: Int = 86   // u16
    const val MAX_HP: Int = 88       // u16
    const val ATTACK: Int = 90       // u16
    const val DEFENSE: Int = 92      // u16
    const val SPEED: Int = 94        // u16
    const val SP_ATTACK: Int = 96    // u16
    const val SP_DEFENSE: Int = 98   // u16
}