package com.dibe.eduhive

import com.dibe.eduhive.data.source.ai.TextChunker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TextChunker.
 *
 * These are PURE tests - no Android dependencies, no mocks needed.
 * Just input → output testing.
 */
class TextChunkerTest {

    private lateinit var chunker: TextChunker

    /**
     * @Before runs before EACH test.
     * Sets up fresh objects for each test.
     */
    @Before
    fun setup() {
        chunker = TextChunker()
    }

    // ========== TOKEN COUNTING TESTS ==========

    @Test
    fun `estimateTokenCount returns correct count for simple text`() {
        // ARRANGE - Prepare input
        val text = "Hello world this is a test"
        // 6 words × 1.3 = 7.8 ≈ 7 tokens

        // ACT - Call function
        val tokens = chunker.estimateTokenCount(text)

        // ASSERT - Check result
        assertEquals(7, tokens)
    }

    @Test
    fun `estimateTokenCount returns zero for empty text`() {
        val text = ""

        val tokens = chunker.estimateTokenCount(text)

        assertEquals(0, tokens)
    }

    @Test
    fun `estimateTokenCount handles multiple spaces`() {
        val text = "Hello    world     test"  // Multiple spaces
        // 3 words × 1.3 = 3.9 ≈ 3 tokens

        val tokens = chunker.estimateTokenCount(text)

        assertEquals(3, tokens)
    }

    // ========== CHUNKING TESTS ==========

    @Test
    fun `chunkText returns single chunk for small text`() {
        // ARRANGE
        val text = "This is a small text that fits in one chunk."

        // ACT
        val chunks = chunker.chunkText(text, maxTokens = 1000)

        // ASSERT
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].text)
        assertEquals(0, chunks[0].chunkIndex)
        assertEquals(1, chunks[0].totalChunks)
        assertFalse(chunks[0].overlapWithPrevious)
        assertFalse(chunks[0].overlapWithNext)
    }

    @Test
    fun `chunkText returns multiple chunks for large text`() {
        // ARRANGE - Create text with 10 sentences
        val sentences = (1..10).map { "This is sentence number $it." }
        val text = sentences.joinToString(" ")

        // ACT - Set small max tokens to force chunking
        val chunks = chunker.chunkText(text, maxTokens = 10, overlapTokens = 2)

        // ASSERT
        assertTrue(chunks.size > 1)  // Should have multiple chunks
        assertEquals(chunks.size, chunks[0].totalChunks)  // Total chunks set correctly

        // First chunk should not overlap with previous
        assertFalse(chunks[0].overlapWithPrevious)

        // Middle chunks should overlap both sides
        if (chunks.size > 2) {
            assertTrue(chunks[1].overlapWithPrevious)
            assertTrue(chunks[1].overlapWithNext)
        }

        // Last chunk should not overlap with next
        assertFalse(chunks.last().overlapWithNext)
    }

    @Test
    fun `chunkText returns empty list for empty text`() {
        val text = ""

        val chunks = chunker.chunkText(text)

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `chunkText preserves chunk indices correctly`() {
        // ARRANGE
        val text = (1..100).joinToString(" ") { "word" }  // 100 words

        // ACT
        val chunks = chunker.chunkText(text, maxTokens = 20)

        // ASSERT - Check indices are sequential
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
        }
    }

    @Test
    fun `chunkText calculates progress correctly`() {
        // Single-chunk case
        val singleChunkText = "This is a short text."
        val singleChunks = chunker.chunkText(singleChunkText, maxTokens = 100)

        assertEquals(1f, singleChunks.first().progress, 0.01f) // Only one chunk → 100%
        assertEquals(1, singleChunks.size) // Should only be 1 chunk

        // Multi-chunk case
        val multiChunkText = "\n" +
                "\n" +
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. In luctus maximus mauris eget placerat. Suspendisse mattis felis nec mi fringilla, pellentesque tempor nisi gravida. Duis vehicula massa ut tempus placerat. Proin eu eros consequat, facilisis ex sed, facilisis ante. Pellentesque vel pellentesque risus. In hendrerit in ligula at rhoncus. Duis pulvinar ultricies interdum.\n" +
                "\n" +
                "Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec id fringilla nunc. Suspendisse elit velit, lobortis vitae mattis vitae, scelerisque eget magna. Phasellus commodo quis tellus quis fermentum. Suspendisse potenti. Aliquam non nunc nisl. Ut sit amet dignissim ligula. Nam commodo nunc at augue sodales, non porta metus sollicitudin. Aenean vitae arcu gravida, ullamcorper nisi vitae, efficitur ante. Vivamus consectetur mattis massa, eu malesuada velit posuere quis. Aliquam non mollis lectus. Ut vestibulum purus tortor, vel condimentum lectus venenatis sed. Mauris quis mi vitae tellus pretium pellentesque at quis orci.\n" +
                "\n" +
                "In risus ex, porta sit amet feugiat nec, cursus eget lacus. Fusce cursus odio sodales elit dapibus bibendum. Donec tempus ligula sed magna tristique, vel suscipit lacus rhoncus. Vivamus ante sapien, euismod nec felis sed, luctus ullamcorper sem. Ut euismod commodo quam a ornare. Morbi ornare ligula lorem, eu fermentum mauris volutpat in. Ut scelerisque ante a dui pharetra, a posuere velit convallis. Aenean volutpat bibendum mi, quis commodo urna dapibus quis. Sed sit amet sem quis elit commodo condimentum. Donec nec auctor turpis. Aliquam congue leo ac libero tincidunt blandit. Praesent in orci ullamcorper nulla finibus consequat. Duis elit velit, iaculis a arcu quis, lobortis bibendum nibh. Quisque velit orci, consequat quis pulvinar et, rutrum id est.\n" +
                "\n" +
                "Aenean bibendum quis magna eu consequat. Etiam consectetur tincidunt mattis. Pellentesque ut ultrices est, at gravida massa. Vivamus erat nulla, malesuada at tincidunt non, maximus eget justo. Sed facilisis elit quis risus vestibulum, ac porta nisl euismod. Nunc eget ligula id quam elementum dapibus. Cras pretium eu tortor non porttitor. Donec mauris quam, blandit in odio a, sagittis tristique velit. In luctus ligula nisl, et convallis mauris molestie vitae. Curabitur magna quam, suscipit varius lacinia nec, condimentum id ante. Curabitur sodales faucibus rhoncus. Pellentesque laoreet ipsum at fringilla facilisis. Etiam lacinia ullamcorper laoreet.\n" +
                "\n" +
                "Sed euismod eleifend nisi ac pulvinar. Cras a lobortis nisi. Mauris viverra ipsum ullamcorper, vehicula felis ut, blandit turpis. Aenean vitae mi vitae lectus ullamcorper placerat. Nam dignissim est nec semper tempus. Interdum et malesuada fames ac ante ipsum primis in faucibus. Duis ut pellentesque libero. Donec non elit a odio ornare sagittis id sed quam. In mauris neque, tempus at vulputate a, auctor et lorem. Curabitur et sem sit amet magna fringilla bibendum sit amet at nibh. Aliquam erat volutpat. Ut id varius lacus, a faucibus turpis. Suspendisse sed gravida dolor. Nullam bibendum erat lorem, sed rhoncus lectus gravida sed. Curabitur sagittis orci at massa finibus viverra. Etiam sed elit pretium, cursus nibh in, dignissim lectus. "

        val chunks = chunker.chunkText(multiChunkText, maxTokens = 20)

        assertEquals(0f, chunks.first().progress, 0.01f)  // First chunk = 0%
        assertEquals(1f, chunks.last().progress, 0.01f)   // Last chunk = 100%

        // Optional: check intermediate chunks are between 0 and 1
        chunks.drop(1).dropLast(1).forEach { chunk ->
            assertTrue(chunk.progress > 0f && chunk.progress < 1f)
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun `chunkText handles text with only punctuation`() {
        val text = "... !!! ???"

        val chunks = chunker.chunkText(text)

        assertEquals(1, chunks.size)
    }

    @Test
    fun `chunkText handles very long single word`() {
        val text = "a".repeat(10000)  // 10,000 character word

        val chunks = chunker.chunkText(text, maxTokens = 100)

        // Should still chunk even with no sentence boundaries
        assertTrue(chunks.isNotEmpty())
    }
}