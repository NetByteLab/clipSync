package com.clipsync.app.ime

data class CompositionCommitResult(
    val committedText: String,
    val remainingPinyin: String
)

object PinyinCompositionStateMachine {

    fun resolveSelection(
        inputPinyin: String,
        selectedCandidate: PinyinCandidate?,
        fallbackText: String
    ): CompositionCommitResult {
        val normalizedInput = PinyinCandidateEngine.normalize(inputPinyin)
        if (normalizedInput.isBlank()) {
            return CompositionCommitResult(
                committedText = fallbackText,
                remainingPinyin = ""
            )
        }

        if (selectedCandidate == null) {
            return CompositionCommitResult(
                committedText = fallbackText,
                remainingPinyin = ""
            )
        }

        val sourceParts = selectedCandidate.sourcePinyin.split("'")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val consumedPinyin = sourceParts.joinToString(separator = "")
        val remaining = normalizedInput.removePrefix(consumedPinyin)

        return CompositionCommitResult(
            committedText = selectedCandidate.text,
            remainingPinyin = remaining
        )
    }
}
