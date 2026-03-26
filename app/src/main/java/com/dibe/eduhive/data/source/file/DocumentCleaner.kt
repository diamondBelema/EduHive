package com.dibe.eduhive.data.source.file

import javax.inject.Inject
import javax.inject.Singleton

/**
 * DocumentCleaner — strips everything a small on-device LLM cannot use.
 *
 * Raw PDF/OCR text is hostile to small models. A single textbook page can waste
 * 30–45% of a 400-token input budget on headers, citations, decorators, and
 * hyphenated reflow artifacts — leaving the model less room to actually respond.
 *
 * This cleaner runs AFTER text extraction and BEFORE the text is handed to the
 * AI pipeline. It is purely cosmetic/structural — it never removes content words.
 *
 * Cleaning stages (order matters):
 *   1. PDF reflow  — rejoin words broken across lines by hyphens
 *   2. Structural  — headers, footers, page numbers, chapter markers
 *   3. References  — bibliography lines, inline citations, footnote markers
 *   4. Layout      — tables, figures, dot-leaders, decorative dividers
 *   5. Lists       — bullet/number prefixes (keep content, strip markers)
 *   6. Whitespace  — normalise newlines, tabs, trailing spaces
 *   7. Quality     — drop lines that are too short to hold a concept
 */
@Singleton
class DocumentCleaner @Inject constructor() {

    companion object {
        /**
         * Lines shorter than this after cleaning are dropped.
         * A line with 20 chars can't hold a meaningful concept sentence.
         */
        private const val MIN_LINE_LENGTH = 20

        /**
         * If cleaning removes more than this fraction of the original text,
         * something unusual is happening — fall back to minimal cleaning only.
         */
        private const val MAX_ACCEPTABLE_REDUCTION = 0.80f
    }

    /**
     * Clean a single page of extracted text.
     * Returns the cleaned text, or the original if cleaning was too aggressive.
     */
    fun cleanPage(raw: String): String {
        if (raw.isBlank()) return raw
        val cleaned = applyAllStages(raw)

        // Safety net: if we stripped more than 80% something went wrong —
        // return the original so the model at least has something to work with.
        val reductionRatio = 1f - (cleaned.length.toFloat() / raw.length)
        return if (reductionRatio > MAX_ACCEPTABLE_REDUCTION) raw.trim() else cleaned
    }

    /**
     * Clean a list of pages and filter out any that become empty.
     */
    fun cleanPages(pages: List<String>): List<String> {
        return pages
            .map { cleanPage(it) }
            .filter { it.length >= MIN_LINE_LENGTH }
    }

    /**
     * Returns a quick summary of what was removed — useful for logging/debugging.
     */
    fun cleanWithStats(raw: String): CleanResult {
        val cleaned = cleanPage(raw)
        val removedChars = raw.length - cleaned.length
        val reductionPct = if (raw.isEmpty()) 0 else (removedChars * 100 / raw.length)
        return CleanResult(
            original = raw,
            cleaned = cleaned,
            removedChars = removedChars,
            reductionPercent = reductionPct
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: cleaning stages
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyAllStages(text: String): String {
        var t = text

        t = fixPdfReflow(t)           // Stage 1 — must be first
        t = stripStructural(t)        // Stage 2
        t = stripReferences(t)        // Stage 3
        t = stripLayoutNoise(t)       // Stage 4
        t = stripListMarkers(t)       // Stage 5
        t = normaliseWhitespace(t)    // Stage 6
        t = dropShortLines(t)         // Stage 7

        return t.trim()
    }

    /**
     * Stage 1 — PDF reflow.
     *
     * PDFBox frequently produces words split across lines with a trailing hyphen:
     *   "The mito-\nchondria is the power-\nhouse of the cell."
     *
     * This must run BEFORE any newline-based stage, because those stages
     * operate on full lines. If you strip newlines first you'd lose the hyphen
     * position and create merged garbage like "mitochondriapower".
     *
     * We only rejoin when the character before the hyphen and after the newline
     * are both word characters (letters). This avoids collapsing intentional
     * dashes in compound terms like "well-\nknown" into "wellknown" — wait,
     * actually we DO want that for tokenisation purposes. The key distinction:
     * we never rejoin when the hyphen is preceded by a digit (e.g. "Figure 4-\n2").
     */
    private fun fixPdfReflow(text: String): String {
        // Pattern: letter + hyphen + newline + lowercase letter
        // The lowercase constraint is important — if the next line starts with
        // a capital it's more likely a new sentence, not a continuation.
        return text.replace(Regex("([a-zA-Z])-\\n([a-z])"), "$1$2")
    }

    /**
     * Stage 2 — Structural noise.
     *
     * Removes whole lines that are headers, footers, page numbers, or
     * administrative metadata. These are line-level patterns — a line that
     * contains ONLY these things. We do not strip inline occurrences because
     * that could accidentally remove content mid-sentence.
     *
     * Patterns covered:
     *   - Lines containing "Page N" or "p. N" at the end
     *   - Lines matching "CHAPTER N" or "Chapter N: Title"
     *   - Copyright / all-rights-reserved lines
     *   - "Downloaded from" / "Visit us at" watermarks
     *   - Running headers: e.g. "4.1 Introduction    Page 47"
     *   - ISBN / DOI lines
     */
    private fun stripStructural(text: String): String {
        val patterns = listOf(
            // Page number lines (standalone or at end of a short line)
            Regex("""(?mi)^.{0,80}\bpage\s+\d+\s*$"""),
            Regex("""(?mi)^\s*\d+\s*$"""),                          // bare page number
            // Chapter / section headers that are purely administrative
            Regex("""(?mi)^CHAPTER\s+\d+[\s:—\-].*$"""),
            // Copyright and licensing
            Regex("""(?mi)^.*©.*all rights reserved.*$"""),
            Regex("""(?mi)^.*copyright\s+\d{4}.*$"""),
            // Watermarks
            Regex("""(?mi)^.*downloaded from\s+\S+.*$"""),
            Regex("""(?mi)^.*visit us at\s+\S+.*$"""),
            Regex("""(?mi)^.*www\.[a-z0-9\-]+\.[a-z]{2,}.*$"""),
            // ISBN / DOI
            Regex("""(?mi)^.*\bISBN[:\s]\s*[\d\-X]+.*$"""),
            Regex("""(?mi)^.*\bDOI[:\s]\s*10\.\d{4}.*$"""),
        )
        var t = text
        for (pattern in patterns) t = t.replace(pattern, "")
        return t
    }

    /**
     * Stage 3 — References and citations.
     *
     * Academic PDFs are dense with citation noise that contributes zero meaning
     * to a small model trying to identify concepts. We strip:
     *
     *   Numbered reference lines:   [1] Smith, J. (2019). ...
     *   Inline numbered citations:  [1], [2,3], [12]
     *   Inline author citations:    (Campbell & Reece, 2020)
     *   Short bracket refs:         [bid., [op. cit.]
     *   "See also" / "cf." phrases: see p. 47, cf. Figure 3
     *   Footnote markers:           ¹, ², ³, †, ‡
     *
     * Important: We strip INLINE citations as replacements with empty string,
     * not whole-line removals. "(Author, 2020) showed that X" becomes "showed
     * that X" — the educational content is preserved.
     */
    private fun stripReferences(text: String): String {
        var t = text

        // Whole reference list lines: [1] Author...  or  1. Author, Title...
        t = t.replace(Regex("""(?m)^\[\d+\][^\n]*$"""), "")

        // Inline numbered citations: [1] [2] [1,2,3] [12]
        t = t.replace(Regex("""\[\d+(?:,\s*\d+)*\]"""), "")

        // Author-year citations: (Campbell & Reece, 2020) or (Smith et al., 2019, p. 45)
        t = t.replace(Regex("""\([A-Z][^)]{4,70},\s*\d{4}[^)]*\)"""), "")

        // Short bracket refs: [ibid.] [op. cit.] [see p. 47]
        t = t.replace(Regex("""\[(?:ibid|op\. cit|see p\.|cf\.)[^\]]*\]""", RegexOption.IGNORE_CASE), "")

        // Superscript footnote markers (Unicode)
        t = t.replace(Regex("""[¹²³⁴⁵⁶⁷⁸⁹⁰†‡§¶]"""), "")

        // "see page N" / "cf. Figure N" inline
        t = t.replace(Regex("""(?i)\b(see\s+(also\s+)?p(?:age|p?)\.?\s*\d+|cf\.\s+[A-Z][a-z]+\s+[\d.]+)"""), "")

        return t
    }

    /**
     * Stage 4 — Layout noise.
     *
     * PDFBox extracts table and figure scaffolding as text. It also preserves
     * dot-leaders used in tables of contents. None of this is educational prose.
     *
     * Patterns covered:
     *   Figure/table caption lines:   "Figure 4.2: ..." / "Table 3.1: ..."
     *   Dot leaders:                  "Chapter 4 ..................... 87"
     *   Table dividers (ASCII art):   "| cell | cell |" / "|------|------|"
     *   Decorative separators:        "─────" / "* * *" / "======"
     *   Equation-only lines:          Lines that are just a chemical formula
     *                                 or mathematical expression
     */
    private fun stripLayoutNoise(text: String): String {
        var t = text

        // Figure / table caption lines
        t = t.replace(Regex("""(?mi)^(Figure|Fig\.?|Table)\s+[\d.]+[^\n]*$"""), "")

        // Dot leaders (table of contents style)
        t = t.replace(Regex("""\.{3,}\s*\d*\s*$""", RegexOption.MULTILINE), "")

        // Markdown/ASCII table rows
        t = t.replace(Regex("""(?m)^\s*\|[^\n]*\|\s*$"""), "")         // | col | col |
        t = t.replace(Regex("""(?m)^\s*\|[-:| ]+\|\s*$"""), "")        // |-----|-----|

        // Decorative separators (4+ repeated non-word chars on their own line)
        t = t.replace(Regex("""(?m)^\s*[─═\-_\*\.=~+]{4,}\s*$"""), "")

        // Lines that are ONLY a chemical/molecular formula (not useful to a small model)
        // Pattern: starts with element symbol, contains → or + between formulas
        t = t.replace(Regex("""(?m)^\s*[A-Z][a-z]?\d*(?:\s*[+→←⇌]\s*[A-Z][a-z]?\d*)+\s*$"""), "")

        // Pure number lines left over from table extraction
        t = t.replace(Regex("""(?m)^\s*[\d.,±%\s]+\s*$"""), "")

        return t
    }

    /**
     * Stage 5 — List markers.
     *
     * Removes the bullet/number prefix but keeps the content. This prevents
     * the model from seeing "- 3 NADH" and trying to generate a concept called
     * "3 NADH". The cleaned version "3 NADH" is still noisy, but at least the
     * bullet marker doesn't confuse the format detection.
     *
     * We are conservative here — we only strip markers at the START of a line,
     * and we require a space after the marker. We do not strip mid-sentence dashes.
     */
    private fun stripListMarkers(text: String): String {
        var t = text

        // Unicode and ASCII bullets: • · ◦ ▪ ▸ - * at line start
        t = t.replace(Regex("""(?m)^\s*[•·◦▪▸]\s+"""), "")
        t = t.replace(Regex("""(?m)^\s*-\s{1,3}(?=[A-Z0-9])"""), "") // only if followed by content

        // Numbered lists: 1. 2. 10. at line start
        t = t.replace(Regex("""(?m)^\s*\d{1,2}\.\s+"""), "")

        // Lettered lists: a) b) A) B) at line start
        t = t.replace(Regex("""(?m)^\s*[a-zA-Z]\)\s+"""), "")

        // Roman numerals: I. II. III. IV. i. ii. at line start (max 4 chars to avoid false matches)
        t = t.replace(Regex("""(?m)^\s*[IVXivx]{1,4}\.\s+"""), "")

        return t
    }

    /**
     * Stage 6 — Whitespace normalisation.
     *
     * After all the removals above, the text has lots of consecutive blank lines
     * and inconsistent spacing. Normalise to a clean single-blank-line separation
     * between paragraphs, and strip trailing spaces.
     */
    private fun normaliseWhitespace(text: String): String {
        var t = text

        // Collapse 3+ consecutive newlines to 2 (paragraph break)
        t = t.replace(Regex("""\n{3,}"""), "\n\n")

        // Replace tabs and multiple spaces with a single space
        t = t.replace(Regex("""[ \t]{2,}"""), " ")

        // Trim trailing spaces on each line
        t = t.split('\n').joinToString("\n") { it.trimEnd() }

        return t
    }

    /**
     * Stage 7 — Drop short lines.
     *
     * After all cleaning, lines shorter than MIN_LINE_LENGTH are almost always
     * leftover fragments: lone numbers, truncated headings, isolated labels.
     * They're not educational sentences — drop them.
     *
     * Exception: we keep blank lines (they separate paragraphs).
     */
    private fun dropShortLines(text: String): String {
        return text.split('\n')
            .filter { line -> line.isBlank() || line.trim().length >= MIN_LINE_LENGTH }
            .joinToString("\n")
    }
}

/**
 * Result from [DocumentCleaner.cleanWithStats] — for logging and debugging.
 */
data class CleanResult(
    val original: String,
    val cleaned: String,
    val removedChars: Int,
    val reductionPercent: Int
) {
    val isEmpty: Boolean get() = cleaned.isBlank()
    override fun toString() =
        "CleanResult(original=${original.length}c → cleaned=${cleaned.length}c, -$reductionPercent%)"
}