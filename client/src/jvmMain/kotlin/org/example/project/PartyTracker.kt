package org.example.project

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.LibretroCore
import org.example.project.memory.FireRedAddresses
import org.example.project.memory.PartyMonOffsets

/**
 * One Pokémon as read from FireRed party RAM.
 *
 * Note: [species] may be 0 or unmapped if decryption fails (corrupt checksum),
 * which is why [speciesName] falls back gracefully via SpeciesNames.get().
 *
 * [personality] is unique per Pokémon and serves as a stable identity key
 * across polls — useful for "did this slot's Pokémon change?" comparisons.
 */
data class PartyMember(
    val nickname: String,
    val species: Int,
    val speciesName: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val personality: Int,
    val otId: Int,
    val isValid: Boolean = true, // false if checksum mismatch
) {
    val isFainted: Boolean get() = currentHp == 0 && maxHp > 0
}

/**
 * Polls FireRed's player party from emulator RAM at a fixed cadence.
 *
 * Cadence: 4 Hz by default. Reading and decrypting the full party is ~600
 * bytes of JNI calls plus four XOR decryptions per mon — cheap, but no point
 * doing it every frame when the UI doesn't need it that fast.
 *
 * Threading: runs on Dispatchers.Default. RAM reads via JNI are synchronous;
 * the only writer is the emulator core during runFrame() on the
 * EmulatorSession loop thread. We're not racing because reads don't tear at
 * the byte level and a torn party struct will simply fail the checksum and
 * be skipped on this poll — the next poll will pick up the consistent state.
 */
class PartyTracker(
    private val coreProvider: () -> LibretroCore?,
    private val pollIntervalMs: Long = 250L,
) {

    private val _party = MutableStateFlow<List<PartyMember>>(emptyList())
    val party: StateFlow<List<PartyMember>> = _party.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    val core = coreProvider()
                    if (core != null) {
                        _party.value = readParty(core)
                    }
                } catch (t: Throwable) {
                    // Non-fatal: torn read, RAM not yet mapped, etc. Try again next tick.
                    println("[PartyTracker] read failed: ${t.message}")
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    private fun readParty(core: LibretroCore): List<PartyMember> {
        val count = core.readByte(FireRedAddresses.PLAYER_PARTY_COUNT) and 0xFF
        if (count !in 1..FireRedAddresses.MAX_PARTY) return emptyList()

        val out = ArrayList<PartyMember>(count)
        for (i in 0 until count) {
            val base = FireRedAddresses.PLAYER_PARTY + i * FireRedAddresses.PARTY_MON_SIZE
            out += readMon(core, base)
        }
        return out
    }

    private fun readMon(core: LibretroCore, base: Int): PartyMember {
        // Unencrypted header
        val pid = core.readInt(base + PartyMonOffsets.PERSONALITY)
        val otId = core.readInt(base + PartyMonOffsets.OT_ID)
        val nickname = readGbaString(core, base + PartyMonOffsets.NICKNAME, 10)
        val storedChecksum = core.readShort(base + PartyMonOffsets.CHECKSUM) and 0xFFFF

        // Unencrypted tail
        val level = core.readByte(base + PartyMonOffsets.LEVEL) and 0xFF
        val currentHp = core.readShort(base + PartyMonOffsets.CURRENT_HP) and 0xFFFF
        val maxHp = core.readShort(base + PartyMonOffsets.MAX_HP) and 0xFFFF

        // Decrypt all four substructures (we need them all to verify the checksum).
        // Reading the encrypted block as one 48-byte slab, then decrypting per-substructure.
        val encryptedBlock = readBytes(core, base + PartyMonOffsets.ENCRYPTED_BLOCK, 48)

        val growthOffset = Gen3Decryption.substructureOffset(pid, Gen3Decryption.Substructure.GROWTH)
        val attacksOffset = Gen3Decryption.substructureOffset(pid, Gen3Decryption.Substructure.ATTACKS)
        val evsOffset = Gen3Decryption.substructureOffset(pid, Gen3Decryption.Substructure.EVS_CONDITION)
        val miscOffset = Gen3Decryption.substructureOffset(pid, Gen3Decryption.Substructure.MISC)

        val growth = Gen3Decryption.decryptSubstructure(
            encryptedBlock.copyOfRange(growthOffset, growthOffset + 12), pid, otId
        )
        val attacks = Gen3Decryption.decryptSubstructure(
            encryptedBlock.copyOfRange(attacksOffset, attacksOffset + 12), pid, otId
        )
        val evs = Gen3Decryption.decryptSubstructure(
            encryptedBlock.copyOfRange(evsOffset, evsOffset + 12), pid, otId
        )
        val misc = Gen3Decryption.decryptSubstructure(
            encryptedBlock.copyOfRange(miscOffset, miscOffset + 12), pid, otId
        )

        // Concatenate in semantic G/A/E/M order for the checksum.
        val combined = ByteArray(48)
        System.arraycopy(growth, 0, combined, 0, 12)
        System.arraycopy(attacks, 0, combined, 12, 12)
        System.arraycopy(evs, 0, combined, 24, 12)
        System.arraycopy(misc, 0, combined, 36, 12)

        val computedChecksum = Gen3Decryption.checksum(combined)
        val checksumOk = computedChecksum == storedChecksum

        val species = if (checksumOk) Gen3Decryption.speciesFromGrowth(growth) else 0

        return PartyMember(
            nickname = nickname,
            species = species,
            speciesName = SpeciesNames.get(species),
            level = level,
            currentHp = currentHp,
            maxHp = maxHp,
            personality = pid,
            otId = otId,
            isValid = checksumOk,
        )
    }

    private fun readBytes(core: LibretroCore, offset: Int, length: Int): ByteArray {
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = (core.readByte(offset + i) and 0xFF).toByte()
        }
        return out
    }

    /**
     * Decode FireRed's proprietary Western character set into a Kotlin String.
     * Encoding ref: https://bulbapedia.bulbagarden.net/wiki/Character_encoding_in_Generation_III
     * Only printable ASCII subset is mapped here; unknown bytes become '?'.
     * Strings terminate at 0xFF.
     */
    private fun readGbaString(core: LibretroCore, offset: Int, maxLen: Int): String {
        val sb = StringBuilder()
        for (i in 0 until maxLen) {
            val b = core.readByte(offset + i) and 0xFF
            if (b == 0xFF) break
            sb.append(decodeChar(b))
        }
        return sb.toString().trim()
    }

    private fun decodeChar(b: Int): Char = when (b) {
        0x00 -> ' '
        in 0xA1..0xAA -> ('0' + (b - 0xA1))                 // 0–9
        in 0xBB..0xD4 -> ('A' + (b - 0xBB))                 // A–Z
        in 0xD5..0xEE -> ('a' + (b - 0xD5))                 // a–z
        0xAD -> '.'
        0xAE -> '-'
        0xB0 -> '…'
        0xB1 -> '"'
        0xB2 -> '"'
        0xB3 -> '\''
        0xB4 -> '\''
        0xB8 -> ','
        0xBA -> '/'
        else -> '?'
    }
}