package com.clipsync.test

import com.clipsync.app.core.PinyinPreferenceStoreCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinPreferenceStoreCodecTest {

    @Test
    fun `removes learned weight entry cleanly`() {
        val encoded = PinyinPreferenceStoreCodec.encodeLearnedWeights(
            mapOf(
                "ni|泥" to 3,
                "wo|我" to 1
            )
        )

        val decoded = PinyinPreferenceStoreCodec.decodeLearnedWeights(encoded).toMutableMap()
        decoded.remove("ni|泥")

        val roundtrip = PinyinPreferenceStoreCodec.decodeLearnedWeights(
            PinyinPreferenceStoreCodec.encodeLearnedWeights(decoded)
        )

        assertFalse(roundtrip.containsKey("ni|泥"))
        assertEquals(1, roundtrip["wo|我"])
    }

    @Test
    fun `preserves pinned phrase mappings`() {
        val encoded = PinyinPreferenceStoreCodec.encodePinnedPhrases(
            mapOf(
                "nihao" to "你好",
                "women" to "我们"
            )
        )

        val decoded = PinyinPreferenceStoreCodec.decodePinnedPhrases(encoded)

        assertEquals("你好", decoded["nihao"])
        assertEquals("我们", decoded["women"])
        assertTrue(decoded.size == 2)
    }
}
