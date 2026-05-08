package com.clipsync.app.core

object PinyinPreferenceStoreCodec {

    fun encodeLearnedWeights(weights: Map<String, Int>, maxEntries: Int = DEFAULT_MAX_ENTRIES): String {
        return weights.entries
            .sortedByDescending { it.value }
            .take(maxEntries)
            .joinToString(separator = "\n") { entry ->
                "${entry.key}\t${entry.value}"
            }
    }

    fun decodeLearnedWeights(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()

        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 2) {
                    return@mapNotNull null
                }
                val value = parts[1].toIntOrNull() ?: return@mapNotNull null
                parts[0] to value
            }
            .toMap()
    }

    fun encodePinnedPhrases(phrases: Map<String, String>, maxEntries: Int = DEFAULT_MAX_ENTRIES): String {
        return phrases.entries
            .sortedBy { it.key }
            .take(maxEntries)
            .joinToString(separator = "\n") { entry ->
                "${entry.key}\t${entry.value}"
            }
    }

    fun decodePinnedPhrases(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) {
                    return@mapNotNull null
                }
                parts[0] to parts[1]
            }
            .toMap()
    }

    private const val DEFAULT_MAX_ENTRIES = 128
}
