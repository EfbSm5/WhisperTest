package com.example.whispertest.whisperEngine

object WhisperVocab {
    var golden_generated_ids: IntArray = intArrayOf(
        50257,
        50362,
        1770,
        13,
        2264,
        346,
        353,
        318,
        262,
        46329,
        286,
        262,
        3504,
        6097,
        11,
        290,
        356,
        389,
        9675,
        284,
        7062
    )

    var tokenEOT: Int = 50256 // end of transcript
    var tokenSOT: Int = 50257 // start of transcript
    var tokenPREV: Int = 50360
    var tokenSOLM: Int = 50361 // ??
    var tokenNOT: Int = 50362 // no timestamps
    var tokenBEG: Int = 50363

    const val tokenTRANSLATE: Int = 50358
    const val tokenTRANSCRIBE: Int = 50359

    const val nVocabEnglish: Int = 51864 // for english only vocab
    const val nVocabMultilingual: Int = 51865 // for multilingual vocab
    var tokenToWord: MutableMap<Int?, String?> = HashMap<Int?, String?>()
}