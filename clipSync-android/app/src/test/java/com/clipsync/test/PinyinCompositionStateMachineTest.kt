package com.clipsync.test

import com.clipsync.app.ime.PinyinCandidateEngine
import com.clipsync.app.ime.PinyinCompositionStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinCompositionStateMachineTest {

    @Test
    fun `keeps remaining pinyin after selecting prefix phrase candidate`() {
        val page = PinyinCandidateEngine.getCandidatePage(
            pinyin = "womenhao",
            pageIndex = 0,
            pageSize = 12
        )
        val selected = page.items.firstOrNull { it.text == "我们" }

        assertTrue("expected prefix phrase candidate 我们", selected != null)

        val result = PinyinCompositionStateMachine.resolveSelection(
            inputPinyin = "womenhao",
            selectedCandidate = selected,
            fallbackText = "womenhao"
        )

        assertEquals("我们", result.committedText)
        assertEquals("hao", result.remainingPinyin)
    }

    @Test
    fun `clears remaining pinyin after selecting full phrase candidate`() {
        val page = PinyinCandidateEngine.getCandidatePage(
            pinyin = "womenhao",
            pageIndex = 0,
            pageSize = 12
        )
        val selected = page.items.firstOrNull { it.text == "我们好" }

        assertTrue("expected full phrase candidate 我们好", selected != null)

        val result = PinyinCompositionStateMachine.resolveSelection(
            inputPinyin = "womenhao",
            selectedCandidate = selected,
            fallbackText = "womenhao"
        )

        assertEquals("我们好", result.committedText)
        assertEquals("", result.remainingPinyin)
    }
}
