package com.clipsync.test

import com.clipsync.app.ime.PinyinCandidateAction
import com.clipsync.app.ime.PinyinCandidateActionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinCandidateActionResolverTest {

    @Test
    fun `shows pin action for unpinned candidate`() {
        val actions = PinyinCandidateActionResolver.resolveActions(
            composingPinyin = "nihao",
            candidateText = "你好",
            pinnedPhrases = emptyMap()
        )

        assertEquals(PinyinCandidateAction.PinPhrase, actions.first())
        assertTrue(actions.contains(PinyinCandidateAction.ClearLearning))
    }

    @Test
    fun `shows unpin action for pinned candidate`() {
        val actions = PinyinCandidateActionResolver.resolveActions(
            composingPinyin = "nihao",
            candidateText = "你好",
            pinnedPhrases = mapOf("nihao" to "你好")
        )

        assertEquals(PinyinCandidateAction.UnpinPhrase, actions.first())
        assertTrue(actions.contains(PinyinCandidateAction.ClearLearning))
    }
}
