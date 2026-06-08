package org.example.project

import org.example.project.memory.PartyMonOffsets

/**
 * Decryption logic for Gen 3 Pokémon encrypted substructures.
 *
 * Gen 3 stores most "real" data (species, moves, EVs, IVs) in a 48-byte block
 * made of four 12-byte substructures: Growth (G), Attacks (A), EVs (E), Misc (M).
 * The order varies per Pokémon — `PID % 24` selects one of 24 permutations.
 * Each 4-byte word in the block is XOR'd with `key = PID xor OTID`.
 *
 * This object is pure: no emulator/JNI dependency, easy to unit test.
 *
 * Reference: https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_data_structure_(Generation_III)
 */
object Gen3Decryption {

    /** The four substructure types. */
    enum class Substructure { GROWTH, ATTACKS, EVS_CONDITION, MISC }

    /**
     * 24 permutations of {G, A, E, M}, indexed by PID % 24.
     * Each row is the order substructures appear in the encrypted block.
     * Source: pret/pokefirered, src/pokemon.c, gSubstructTypes.
     */
    private val PERMUTATIONS: Array<Array<Substructure>> = arrayOf(
        arrayOf(Substructure.GROWTH,        Substructure.ATTACKS,       Substructure.EVS_CONDITION, Substructure.MISC),          //  0 GAEM
        arrayOf(Substructure.GROWTH,        Substructure.ATTACKS,       Substructure.MISC,          Substructure.EVS_CONDITION), //  1 GAME
        arrayOf(Substructure.GROWTH,        Substructure.EVS_CONDITION, Substructure.ATTACKS,       Substructure.MISC),          //  2 GEAM
        arrayOf(Substructure.GROWTH,        Substructure.EVS_CONDITION, Substructure.MISC,          Substructure.ATTACKS),       //  3 GEMA
        arrayOf(Substructure.GROWTH,        Substructure.MISC,          Substructure.ATTACKS,       Substructure.EVS_CONDITION), //  4 GMAE
        arrayOf(Substructure.GROWTH,        Substructure.MISC,          Substructure.EVS_CONDITION, Substructure.ATTACKS),       //  5 GMEA
        arrayOf(Substructure.ATTACKS,       Substructure.GROWTH,        Substructure.EVS_CONDITION, Substructure.MISC),          //  6 AGEM
        arrayOf(Substructure.ATTACKS,       Substructure.GROWTH,        Substructure.MISC,          Substructure.EVS_CONDITION), //  7 AGME
        arrayOf(Substructure.ATTACKS,       Substructure.EVS_CONDITION, Substructure.GROWTH,        Substructure.MISC),          //  8 AEGM
        arrayOf(Substructure.ATTACKS,       Substructure.EVS_CONDITION, Substructure.MISC,          Substructure.GROWTH),        //  9 AEMG
        arrayOf(Substructure.ATTACKS,       Substructure.MISC,          Substructure.GROWTH,        Substructure.EVS_CONDITION), // 10 AMGE
        arrayOf(Substructure.ATTACKS,       Substructure.MISC,          Substructure.EVS_CONDITION, Substructure.GROWTH),        // 11 AMEG
        arrayOf(Substructure.EVS_CONDITION, Substructure.GROWTH,        Substructure.ATTACKS,       Substructure.MISC),          // 12 EGAM
        arrayOf(Substructure.EVS_CONDITION, Substructure.GROWTH,        Substructure.MISC,          Substructure.ATTACKS),       // 13 EGMA
        arrayOf(Substructure.EVS_CONDITION, Substructure.ATTACKS,       Substructure.GROWTH,        Substructure.MISC),          // 14 EAGM
        arrayOf(Substructure.EVS_CONDITION, Substructure.ATTACKS,       Substructure.MISC,          Substructure.GROWTH),        // 15 EAMG
        arrayOf(Substructure.EVS_CONDITION, Substructure.MISC,          Substructure.GROWTH,        Substructure.ATTACKS),       // 16 EMGA
        arrayOf(Substructure.EVS_CONDITION, Substructure.MISC,          Substructure.ATTACKS,       Substructure.GROWTH),        // 17 EMAG
        arrayOf(Substructure.MISC,          Substructure.GROWTH,        Substructure.ATTACKS,       Substructure.EVS_CONDITION), // 18 MGAE
        arrayOf(Substructure.MISC,          Substructure.GROWTH,        Substructure.EVS_CONDITION, Substructure.ATTACKS),       // 19 MGEA
        arrayOf(Substructure.MISC,          Substructure.ATTACKS,       Substructure.GROWTH,        Substructure.EVS_CONDITION), // 20 MAGE
        arrayOf(Substructure.MISC,          Substructure.ATTACKS,       Substructure.EVS_CONDITION, Substructure.GROWTH),        // 21 MAEG
        arrayOf(Substructure.MISC,          Substructure.EVS_CONDITION, Substructure.GROWTH,        Substructure.ATTACKS),       // 22 MEGA
        arrayOf(Substructure.MISC,          Substructure.EVS_CONDITION, Substructure.ATTACKS,       Substructure.GROWTH),        // 23 MEAG
    )

    /**
     * Returns the byte offset of the requested substructure within the 48-byte
     * encrypted block, given a Pokémon's personality value.
     */
    fun substructureOffset(pid: Int, sub: Substructure): Int {
        val permIndex = (pid.toLong() and 0xFFFFFFFFL).rem(24).toInt()
        val order = PERMUTATIONS[permIndex]
        val slot = order.indexOf(sub)
        return slot * PartyMonOffsets.SUBSTRUCT_SIZE
    }

    /**
     * Decrypt a 12-byte substructure into a fresh ByteArray.
     *
     * @param encrypted The 12 raw (still-encrypted) bytes from RAM.
     * @param pid Personality value (u32).
     * @param otId Original Trainer ID (u32).
     */
    fun decryptSubstructure(encrypted: ByteArray, pid: Int, otId: Int): ByteArray {
        require(encrypted.size == 12) { "Substructure must be 12 bytes, got ${encrypted.size}" }
        val key = pid xor otId
        val out = ByteArray(12)
        // Process as three little-endian u32 words.
        for (wordIdx in 0 until 3) {
            val o = wordIdx * 4
            val word =
                (encrypted[o].toInt() and 0xFF) or
                        ((encrypted[o + 1].toInt() and 0xFF) shl 8) or
                        ((encrypted[o + 2].toInt() and 0xFF) shl 16) or
                        ((encrypted[o + 3].toInt() and 0xFF) shl 24)
            val decrypted = word xor key
            out[o]     = (decrypted and 0xFF).toByte()
            out[o + 1] = ((decrypted ushr 8) and 0xFF).toByte()
            out[o + 2] = ((decrypted ushr 16) and 0xFF).toByte()
            out[o + 3] = ((decrypted ushr 24) and 0xFF).toByte()
        }
        return out
    }

    /**
     * Compute the 16-bit checksum used in the unencrypted header (offset 28).
     * Sum of all 24 little-endian u16 words across the four decrypted substructures.
     * Caller must concatenate decrypted G+A+E+M (in that semantic order, NOT permutation order).
     */
    fun checksum(allFourDecrypted: ByteArray): Int {
        require(allFourDecrypted.size == 48) { "Need all 48 decrypted bytes, got ${allFourDecrypted.size}" }
        var sum = 0
        for (i in 0 until 24) {
            val o = i * 2
            val word = (allFourDecrypted[o].toInt() and 0xFF) or
                    ((allFourDecrypted[o + 1].toInt() and 0xFF) shl 8)
            sum = (sum + word) and 0xFFFF
        }
        return sum
    }

    /**
     * Convenience: extract just the species ID from a 12-byte decrypted Growth substructure.
     * Species lives at Growth byte offsets 0..1 as little-endian u16.
     */
    fun speciesFromGrowth(decryptedGrowth: ByteArray): Int {
        require(decryptedGrowth.size == 12) { "Growth must be 12 bytes" }
        return (decryptedGrowth[0].toInt() and 0xFF) or
                ((decryptedGrowth[1].toInt() and 0xFF) shl 8)
    }
}