package org.example.project.data

import org.example.project.domain.models.GameVersion
import java.io.File

object GbaValidator {

    private val KNOWN_HEADERS = mapOf(
        "POKEMON FIRE" to GameVersion.FIRE_RED,
        "POKEMON LEAF" to GameVersion.LEAF_GREEN,
        "POKEMON EMER" to GameVersion.EMERALD,
        "POKEMON RUBY" to GameVersion.RUBY,
        "POKEMON SAPP" to GameVersion.SAPPHIRE,
    )

    data class GbaValidationResult(
        val isValid: Boolean,
        val gameVersion: GameVersion? = null,
        val gameTitle: String? = null,
        val errorMessage: String? = null
    )

    fun validate(file: File): GbaValidationResult {
        if (!file.exists())
            return GbaValidationResult(false, errorMessage = "File not found")

        if (file.extension.lowercase() != "gba")
            return GbaValidationResult(false, errorMessage = "File must be a .gba file")

        if (file.length() < 192)
            return GbaValidationResult(false, errorMessage = "File is too small to be a valid GBA ROM")

        return try {
            val header = ByteArray(192)
            file.inputStream().use { it.read(header) }

            // Log the first byte to see what we're getting
            println("🔍 First byte: 0x${header[0].toInt().and(0xFF).toString(16)}")

            // Skip entry point check, just validate game title
            val titleBytes = header.copyOfRange(0xA0, 0xAC)
            val gameTitle = String(titleBytes, Charsets.US_ASCII).trimEnd('\u0000').trim()
            println("🔍 Game title from header: '$gameTitle'")

            val gameVersion = KNOWN_HEADERS.entries
                .firstOrNull { gameTitle.startsWith(it.key) }
                ?.value

            if (gameVersion == null) {
                return GbaValidationResult(
                    false,
                    errorMessage = "ROM not supported: '$gameTitle'. Only Fire Red, Leaf Green, Emerald, Ruby, and Sapphire are supported."
                )
            }

            GbaValidationResult(
                isValid = true,
                gameVersion = gameVersion,
                gameTitle = gameTitle
            )
        } catch (e: Exception) {
            GbaValidationResult(false, errorMessage = "Failed to read ROM: ${e.message}")
        }
    }

}