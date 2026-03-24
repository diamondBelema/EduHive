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
 * Enhanced file data source with page-aware extraction.
 */
class FileDataSource @Inject constructor(
    val context: Context
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extract text from any supported file format as a single string.
     */
    suspend fun extractText(uri: Uri): Result<String> {
        return extractTextPages(uri).map { it.joinToString("\n\n") }
    }

    /**
     * Extract text from any supported file format as a list of pages.
     * This is crucial for processing large PDFs without exceeding AI context limits.
     */
    suspend fun extractTextPages(uri: Uri): Result<List<String>> {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: ""

            val pages = when {
                mimeType.contains("pdf") ->
                    extractPagesFromPdf(uri)

                mimeType.contains("image") ->
                    listOf(extractTextFromImage(uri))

                mimeType.contains("html") ->
                    listOf(extractTextFromHtml(uri))

                mimeType.contains("text") ->
                    chunkPlainText(extractTextFromPlainText(uri))

                else -> throw UnsupportedFileTypeException("Unsupported file type: $mimeType")
            }

            if (pages.all { it.isBlank() }) {
                Result.failure(EmptyFileException("File contains no readable text"))
            } else {
                Result.success(pages.filter { it.isNotBlank() })
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractPagesFromPdf(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val pages = mutableListOf<String>()

            try {
                val stripper = PDFTextStripper()
                val totalPages = document.numberOfPages

                for (i in 1..totalPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val pageText = stripper.getText(document).trim()
                    
                    if (pageText.length < 50) {
                        // Fallback to OCR for this specific page if it seems like an image
                        val renderer = PDFRenderer(document)
                        val bitmap = renderer.renderImageWithDPI(i - 1, 300f)
                        val ocrText = extractTextFromBitmap(bitmap)
                        bitmap.recycle()
                        pages.add(ocrText)
                    } else {
                        pages.add(pageText)
                    }
                }
                pages
            } finally {
                document.close()
            }
        } ?: throw FileReadException("Failed to open PDF file")
    }

    private suspend fun extractTextFromImage(uri: Uri): String {
        val inputImage = InputImage.fromFilePath(context, uri)
        return extractTextFromInputImage(inputImage)
    }

    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return extractTextFromInputImage(inputImage)
    }

    private suspend fun extractTextFromInputImage(inputImage: InputImage): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText -> cont.resume(visionText.text) }
            .addOnFailureListener { e -> cont.resumeWithException(OcrException("OCR failed", e)) }
        cont.invokeOnCancellation { recognizer.close() }
    }

    private suspend fun extractTextFromHtml(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val html = inputStream.bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(html)
            doc.select("script, style, nav, header, footer").remove()
            doc.body().text()
        } ?: throw FileReadException("Failed to open HTML file")
    }

    private suspend fun extractTextFromPlainText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw FileReadException("Failed to open text file")
    }

    private fun chunkPlainText(text: String): List<String> {
        // Split large text files into chunks of ~4000 chars (safe for AI)
        return text.chunked(4000)
    }
}

class UnsupportedFileTypeException(message: String) : Exception(message)
class FileReadException(message: String) : Exception(message)
class EmptyFileException(message: String) : Exception(message)
class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)
