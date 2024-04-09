package com.example.mapmywifi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.IOException

object FileUtils {

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return if (uri.toString().endsWith(".pdf", ignoreCase = true)) {
            // Load and render the first page of the PDF as a Bitmap
            loadPdfFirstPageAsBitmap(context, uri)
        } else {
            // Load the image directly
            loadImageFromUri(context, uri)
        }
    }

    private fun loadPdfFirstPageAsBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun loadImageFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}