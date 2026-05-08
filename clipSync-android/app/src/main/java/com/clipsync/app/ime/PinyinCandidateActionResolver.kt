package com.clipsync.app.ime

enum class PinyinCandidateAction {
    PinPhrase,
    UnpinPhrase,
    ClearLearning
}

object PinyinCandidateActionResolver {

    fun resolveActions(
        composingPinyin: String,
        candidateText: String,
        pinnedPhrases: Map<String, String>
    ): List<PinyinCandidateAction> {
        val pinnedText = pinnedPhrases[composingPinyin]
        return if (pinnedText == candidateText) {
            listOf(
                PinyinCandidateAction.UnpinPhrase,
                PinyinCandidateAction.ClearLearning
            )
        } else {
            listOf(
                PinyinCandidateAction.PinPhrase,
                PinyinCandidateAction.ClearLearning
            )
        }
    }
}
