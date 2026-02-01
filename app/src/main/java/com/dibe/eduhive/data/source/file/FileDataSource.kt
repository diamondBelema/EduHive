package com.dibe.eduhive.data.source.file

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import jakarta.inject.Inject


/**
 * Data source for extracting text from various file formats.
 *
 * Supported formats:
 * - PDF (text-based)
 * - PDF (scanned with OCR)
 * - PPTX (PowerPoint)
 * - DOCX (Word)
 * - Images (JPG, PNG via OCR)
 * - Plain text (.txt, .md)
 */
class FileDataSource @Inject constructor(
    val context: Context
)  {

    /**
     * Main entry point: Extract text from any supported file format.
     * Automatically detects format based on URI.
     */
    suspend fun extractText(uri: Uri): Result<String> {
        return try {
            val mimeType = context.contentResolver.getType(uri)

            val text = when {
                mimeType?.contains("pdf") == true -> extractTextFromPdf(uri)
                mimeType?.contains("powerpoint") == true || mimeType?.contains("presentation") == true -> extractTextFromPptx(uri)
                mimeType?.contains("word") == true || mimeType?.contains("document") == true -> extractTextFromDocx(uri)
                mimeType?.contains("image") == true -> extractTextFromImage(uri)
                mimeType?.contains("text") == true -> extractTextFromPlainText(uri)
                else -> throw UnsupportedFileTypeException("Unsupported file type: $mimeType")
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
     * Extract text from PDF file.
     * Handles text-based PDFs directly.
     * For scanned PDFs, use extractTextFromScannedPdf() instead.
     */
    suspend fun extractTextFromPdf(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)

            try {
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                // If PDF appears to be scanned (very little text), suggest OCR
                if (text.trim().length < 50 && document.numberOfPages > 0) {
                    throw ScannedPdfException("This appears to be a scanned PDF. Use OCR instead.")
                }

                text
            } finally {
                document.close()
            }
        } ?: throw FileReadException("Failed to open PDF file")
    }

    /**
     * Extract text from scanned PDF using OCR.
     * Converts each page to image and runs OCR.
     */
    suspend fun extractTextFromScannedPdf(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val textBuilder = StringBuilder()

            try {
                val renderer = org.apache.rendering.PDFRenderer(document)

                for (pageIndex in 0 until document.numberOfPages) {
                    // Render page to bitmap
                    val bitmap = renderer.renderImageWithDPI(pageIndex, 300f)

                    // Run OCR on bitmap
                    val pageText = extractTextFromBitmap(bitmap)
                    textBuilder.append(pageText)
                    textBuilder.append("\n\n--- Page ${pageIndex + 1} ---\n\n")
                }

                textBuilder.toString()
            } finally {
                document.close()
            }
        } ?: throw FileReadException("Failed to open PDF file")
    }

    /**
     * Extract text from PowerPoint file (.pptx).
     */
    suspend fun extractTextFromPptx(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val slideShow = XMLSlideShow(inputStream)
            val textBuilder = StringBuilder()

            slideShow.slides.forEachIndexed { index, slide ->
                textBuilder.append("--- Slide ${index + 1} ---\n")

                // Extract text from all shapes
                slide.shapes.forEach { shape ->
                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        textBuilder.append(shape.text)
                        textBuilder.append("\n")
                    }
                }

                // Extract speaker notes if available
                val notes = slide.notes
                if (notes != null) {
                    textBuilder.append("\nNotes:\n")
                    notes.shapes.forEach { shape ->
                        if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                            textBuilder.append(shape.text)
                            textBuilder.append("\n")
                        }
                    }
                }

                textBuilder.append("\n")
            }

            slideShow.close()
            textBuilder.toString()
        } ?: throw FileReadException("Failed to open PPTX file")
    }

    /**
     * Extract text from Word document (.docx).
     */
    suspend fun extractTextFromDocx(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = XWPFDocument(inputStream)
            val textBuilder = StringBuilder()

            // Extract paragraphs
            document.paragraphs.forEach { paragraph ->
                textBuilder.append(paragraph.text)
                textBuilder.append("\n")
            }

            // Extract tables
            document.tables.forEach { table ->
                table.rows.forEach { row ->
                    row.tableCells.forEach { cell ->
                        textBuilder.append(cell.text)
                        textBuilder.append("\t")
                    }
                    textBuilder.append("\n")
                }
            }

            document.close()
            textBuilder.toString()
        } ?: throw FileReadException("Failed to open DOCX file")
    }

    /**
     * Extract text from image using Google ML Kit OCR.
     */
    suspend fun extractTextFromImage(uri: Uri): String {
        val inputImage = InputImage.fromFilePath(context, uri)
        return extractTextFromInputImage(inputImage)
    }

    /**
     * Extract text from bitmap using OCR.
     */
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return extractTextFromInputImage(inputImage)
    }

    /**
     * Common OCR logic using ML Kit.
     */
    private suspend fun extractTextFromInputImage(inputImage: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return try {
            val result = recognizer.process(inputImage).await()
            result.text
        } catch (e: Exception) {
            throw OcrException("OCR failed: ${e.message}", e)
        } finally {
            recognizer.close()
        }
    }

    /**
     * Extract text from plain text file (.txt, .md).
     */
    suspend fun extractTextFromPlainText(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw FileReadException("Failed to open text file")
    }

    /**
     * Check if a file is likely scanned (needs OCR).
     * Quick heuristic: if PDF has very little extractable text, it's probably scanned.
     */
    suspend fun isScannedPdf(uri: Uri): Boolean {
        return try {
            val text = extractTextFromPdf(uri)
            text.trim().length < 50 // Less than 50 chars = probably scanned
        } catch (e: ScannedPdfException) {
            true
        } catch (e: Exception) {
            false
        }
    }
}

// ========== EXCEPTIONS ==========

class UnsupportedFileTypeException(message: String) : Exception(message)
class FileReadException(message: String) : Exception(message)
class EmptyFileException(message: String) : Exception(message)
class ScannedPdfException(message: String) : Exception(message)
class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)