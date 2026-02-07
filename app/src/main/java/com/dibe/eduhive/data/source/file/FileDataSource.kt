package com.dibe.eduhive.data.source.file


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Simple, bulletproof file data source.
 *
 * Supported formats:
 * - PDF (text-based)
 * - PDF (scanned with OCR)
 * - Images (JPG, PNG via OCR)
 * - HTML (web pages)
 * - Plain text (.txt, .md)
 *
 * All using battle-tested, stable libraries.
 */
class FileDataSource @Inject constructor(
    val context: Context
) {

    init {
        // Initialize PDFBox (required for Android)
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extract text from any supported file format.
     */
    suspend fun extractText(uri: Uri): Result<String> {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: ""

            val text = when {
                mimeType.contains("pdf") ->
                    extractTextFromPdf(uri)

                mimeType.contains("image") ->
                    extractTextFromImage(uri)

                mimeType.contains("html") ->
                    extractTextFromHtml(uri)

                mimeType.contains("text") ->
                    extractTextFromPlainText(uri)

                else -> throw UnsupportedFileTypeException(
                    "Unsupported file type: $mimeType. " +
                            "Supported: PDF, Images, HTML, Text"
                )
            }

            if (text.isBlank()) {
                Result.failure(EmptyFileException("File contains no readable text"))
            } else {
                Result.success(text)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract text from PDF.
     * Tries text extraction first, falls back to OCR if needed.
     */
    private suspend fun extractTextFromPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)

            try {
                // Try text extraction first
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                // If very little text, it's probably scanned - use OCR
                if (text.trim().length < 50 && document.numberOfPages > 0) {
                    document.close()
                    return@withContext extractTextFromScannedPdf(uri)
                }

                text
            } finally {
                document.close()
            }
        } ?: throw FileReadException("Failed to open PDF file")
    }

    /**
     * Extract text from scanned PDF using OCR.
     * Renders each page as image and runs OCR.
     */
    private suspend fun extractTextFromScannedPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val textBuilder = StringBuilder()

            try {
                val renderer = PDFRenderer(document)
                val totalPages = document.numberOfPages

                for (pageIndex in 0 until totalPages) {
                    // Render page to bitmap (300 DPI for good OCR quality)
                    val bitmap = renderer.renderImageWithDPI(pageIndex, 300f)

                    // Run OCR on bitmap
                    val pageText = extractTextFromBitmap(bitmap)

                    if (pageText.isNotBlank()) {
                        textBuilder.append(pageText)
                        textBuilder.append("\n\n--- Page ${pageIndex + 1} ---\n\n")
                    }

                    // Clean up bitmap
                    bitmap.recycle()
                }

                textBuilder.toString()
            } finally {
                document.close()
            }
        } ?: throw FileReadException("Failed to open PDF file for OCR")
    }

    /**
     * Extract text from image using ML Kit OCR.
     */
    private suspend fun extractTextFromImage(uri: Uri): String {
        val inputImage = InputImage.fromFilePath(context, uri)
        return extractTextFromInputImage(inputImage)
    }

    /**
     * Extract text from bitmap using ML Kit OCR.
     */
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return extractTextFromInputImage(inputImage)
    }

    /**
     * Common OCR logic using ML Kit.
     */
    private suspend fun extractTextFromInputImage(
        inputImage: InputImage
    ): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(
                    OcrException("OCR failed: ${e.message}", e)
                )
            }

        // Clean up on cancellation
        cont.invokeOnCancellation {
            recognizer.close()
        }
    }

    /**
     * Extract text from HTML file using Jsoup.
     */
    private suspend fun extractTextFromHtml(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            try {
                val html = inputStream.bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(html)

                // Remove script, style, and nav elements
                doc.select("script, style, nav, header, footer").remove()

                // Get clean text
                doc.body().text()
            } catch (e: Exception) {
                throw FileReadException("Failed to parse HTML: ${e.message}")
            }
        } ?: throw FileReadException("Failed to open HTML file")
    }

    /**
     * Extract text from plain text file (.txt, .md).
     */
    private suspend fun extractTextFromPlainText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw FileReadException("Failed to open text file")
    }

    /**
     * Check if a PDF is likely scanned (needs OCR).
     */
    suspend fun isScannedPdf(uri: Uri): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    try {
                        val stripper = PDFTextStripper()
                        val text = stripper.getText(document)
                        text.trim().length < 50 // Less than 50 chars = probably scanned
                    } finally {
                        document.close()
                    }
                } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}

// ========== EXCEPTIONS ==========

class UnsupportedFileTypeException(message: String) : Exception(message)
class FileReadException(message: String) : Exception(message)
class EmptyFileException(message: String) : Exception(message)
class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)