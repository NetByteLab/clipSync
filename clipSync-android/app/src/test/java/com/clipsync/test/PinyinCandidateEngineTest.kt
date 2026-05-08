package com.clipsync.test

import com.clipsync.app.ime.PinyinCandidateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinyinCandidateEngineTest {

    @Before
    fun resetEngineLearning() {
        PinyinCandidateEngine.setLearnedWeights(emptyMap())
        PinyinCandidateEngine.setPinnedPhrases(emptyMap())
    }

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

    @Test
    fun `keeps combined phrase ranked first for continuous input`() {
        val candidates = PinyinCandidateEngine.getCandidates("women", limit = 5)

        assertEquals("我们", candidates.first())
    }

    @Test
    fun `builds phrase candidates from segmented syllables when exact phrase is absent`() {
        val candidates = PinyinCandidateEngine.getCandidates("woai", limit = 8)

        assertTrue("我爱 should be suggested from wo + ai segmentation", candidates.contains("我爱"))
    }

    @Test
    fun `promotes learned candidate for the same pinyin`() {
        val baseline = PinyinCandidateEngine.getCandidates("ni", limit = 8)
        val baselineRank = baseline.indexOf("泥")
        assertTrue("baseline should contain 泥", baselineRank >= 0)

        PinyinCandidateEngine.setLearnedWeights(
            mapOf(
                "ni|泥" to 1
            )
        )

        val learned = PinyinCandidateEngine.getCandidates("ni", limit = 8)
        val learnedRank = learned.indexOf("泥")
        assertTrue("learned results should contain 泥", learnedRank >= 0)
        assertTrue("泥 should rank higher after learning", learnedRank < baselineRank)
    }

    @Test
    fun `pins strongly learned candidate to the top`() {
        PinyinCandidateEngine.setLearnedWeights(
            mapOf(
                "ni|泥" to 10
            )
        )

        val learned = PinyinCandidateEngine.getCandidates("ni", limit = 8)

        assertEquals("泥", learned.first())
    }

    @Test
    fun `prefers pinned phrase over learned ranking`() {
        PinyinCandidateEngine.setLearnedWeights(
            mapOf(
                "nihao|你号" to 10
            )
        )
        PinyinCandidateEngine.setPinnedPhrases(
            mapOf(
                "nihao" to "你好"
            )
        )

        val candidates = PinyinCandidateEngine.getCandidates("nihao", limit = 5)

        assertEquals("你好", candidates.first())
    }
}
