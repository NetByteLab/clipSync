package com.clipsync.test

import com.clipsync.app.ime.PinyinCandidateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinCandidateEngineTest {

    @Test
    fun `returns exact phrase before single characters`() {
        val candidates = PinyinCandidateEngine.getCandidates("nihao", limit = 5)

        assertEquals("你好", candidates.first())
    }

    @Test
    fun `supports fuzzy initials for zh z confusion`() {
        val candidates = PinyinCandidateEngine.getCandidates("zongguo", limit = 10)

        assertTrue("中国 should be suggested for fuzzy zhongguo", candidates.contains("中国"))
    }

    @Test
    fun `supports fuzzy finals for ang an confusion`() {
        val candidates = PinyinCandidateEngine.getCandidates("sheng", limit = 10)

        assertTrue("生 should be suggested even when ang and an style finals vary", candidates.contains("生"))
    }

    @Test
    fun `paginates candidate results`() {
        val page = PinyinCandidateEngine.getCandidatePage(
            pinyin = "sh",
            pageIndex = 1,
            pageSize = 3
        )

        assertEquals(1, page.pageIndex)
        assertEquals(3, page.items.size)
        assertTrue(page.hasPrevious)
        assertTrue(page.totalPages >= 2)
    }

    @Test
    fun `normalizes umlaut spellings`() {
        val candidates = PinyinCandidateEngine.getCandidates("lü", limit = 5)

        assertTrue(candidates.contains("绿"))
    }
}
