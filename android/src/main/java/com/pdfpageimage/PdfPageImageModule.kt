package com.pdfpageimage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.facebook.react.bridge.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.UUID

class PdfPageImageModule(reactContext: ReactApplicationContext) :
  NativePdfPageImageSpec(reactContext) {

  override fun getName(): String = NAME

  private val pdfCache = HashMap<String, PdfDoc>()

  override fun openPdf(uri: String, promise: Promise) {
    try {
      val doc = getOrOpen(uri)
      val result = WritableNativeMap().apply {
        putString("uri", uri)
        putInt("pageCount", doc.pageCount)
      }
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("INTERNAL_ERROR", e.message, e)
    }
  }

  override fun generate(uri: String, page: Double, scale: Double, promise: Promise) {
    try {
      val doc = getOrOpen(uri)
      val result = doc.renderPage(page.toInt(), scale.toFloat())
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("INTERNAL_ERROR", e.message, e)
    }
  }

  override fun generateAllPages(uri: String, scale: Double, promise: Promise) {
    try {
      val doc = getOrOpen(uri)
      val pages = WritableNativeArray()
      for (i in 0 until doc.pageCount) {
        pages.pushMap(doc.renderPage(i, scale.toFloat()))
      }
      promise.resolve(pages)
    } catch (e: Exception) {
      promise.reject("INTERNAL_ERROR", e.message, e)
    }
  }

  override fun closePdf(uri: String, promise: Promise) {
    pdfCache[uri]?.close()
    pdfCache.remove(uri)
    promise.resolve(null)
  }

  private fun getOrOpen(uri: String): PdfDoc {
    pdfCache[uri]?.let { return it }
    val doc = PdfDoc(reactApplicationContext, uri)
    pdfCache[uri] = doc
    return doc
  }

  companion object {
    const val NAME = "PdfPageImage"
  }
}

// -- PdfDoc: handles loading, caching, rendering --

private class PdfDoc(
  private val context: ReactApplicationContext,
  private val uriString: String,
) {
  private val fileDescriptor: ParcelFileDescriptor
  private val renderer: PdfRenderer
  private val pageCache = HashMap<String, WritableNativeMap>()
  private val tempFiles = mutableListOf<File>()

  init {
    fileDescriptor = openFileDescriptor(uriString)
    renderer = PdfRenderer(fileDescriptor)
  }

  val pageCount: Int get() = renderer.pageCount

  fun renderPage(index: Int, scale: Float): WritableNativeMap {
    val cacheKey = "$index:$scale"
    pageCache[cacheKey]?.let {
      // Return a copy since WritableNativeMap can only be consumed once
      val copy = WritableNativeMap()
      copy.putString("uri", it.getString("uri"))
      copy.putInt("width", it.getInt("width"))
      copy.putInt("height", it.getInt("height"))
      return copy
    }

    if (index < 0 || index >= renderer.pageCount) {
      throw RuntimeException("Page number $index is invalid, file has ${renderer.pageCount} pages")
    }

    val page = renderer.openPage(index)
    val width = (page.width * scale).toInt()
    val height = (page.height * scale).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()

    val outFile = File(context.cacheDir, "${UUID.randomUUID()}.png")
    FileOutputStream(outFile).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bitmap.recycle()
    tempFiles.add(outFile)

    val result = WritableNativeMap().apply {
      putString("uri", "file://${outFile.absolutePath}")
      putInt("width", width)
      putInt("height", height)
    }
    pageCache[cacheKey] = result

    val copy = WritableNativeMap().apply {
      putString("uri", "file://${outFile.absolutePath}")
      putInt("width", width)
      putInt("height", height)
    }
    return copy
  }

  fun close() {
    pageCache.clear()
    for (f in tempFiles) {
      f.delete()
    }
    tempFiles.clear()
    try { renderer.close() } catch (_: Exception) {}
    try { fileDescriptor.close() } catch (_: Exception) {}
  }

  private fun openFileDescriptor(uri: String): ParcelFileDescriptor {
    return when {
      uri.startsWith("content://") -> {
        context.contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")
          ?: throw IOException("Cannot open content URI: $uri")
      }
      uri.startsWith("file://") -> {
        val path = uri.removePrefix("file://")
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
      }
      uri.startsWith("http://") || uri.startsWith("https://") -> {
        val tempFile = File(context.cacheDir, "pdf_${UUID.randomUUID()}.pdf")
        URL(uri).openStream().use { input ->
          FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
          }
        }
        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
      }
      uri.startsWith("data:") -> {
        val commaIdx = uri.indexOf(',')
        if (commaIdx == -1) throw IOException("Invalid base64 data URI")
        val base64 = uri.substring(commaIdx + 1)
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val tempFile = File(context.cacheDir, "pdf_${UUID.randomUUID()}.pdf")
        FileOutputStream(tempFile).use { it.write(bytes) }
        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
      }
      else -> {
        // Treat as absolute file path
        ParcelFileDescriptor.open(File(uri), ParcelFileDescriptor.MODE_READ_ONLY)
      }
    }
  }
}
