package com.pdfpageimage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.facebook.react.bridge.*
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.Locale
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

  override fun generate(uri: String, page: Double, scale: Double, options: ReadableMap, promise: Promise) {
    try {
      val doc = getOrOpen(uri)
      val result = doc.renderPage(page.toInt(), scale.toFloat(), RenderOptions.from(options))
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("INTERNAL_ERROR", e.message, e)
    }
  }

  override fun generateAllPages(uri: String, scale: Double, options: ReadableMap, promise: Promise) {
    try {
      val doc = getOrOpen(uri)
      val renderOptions = RenderOptions.from(options)
      val pages = WritableNativeArray()
      for (i in 0 until doc.pageCount) {
        pages.pushMap(doc.renderPage(i, scale.toFloat(), renderOptions))
      }
      promise.resolve(pages)
    } catch (e: Exception) {
      promise.reject("INTERNAL_ERROR", e.message, e)
    }
  }

  /**
   * Re-encode every page as a JPEG at `dpi` into a new PDF in the cache dir.
   * Runs off the JS thread, one page bitmap at a time.
   */
  override fun compress(uri: String, options: ReadableMap, promise: Promise) {
    val opts = CompressOptions.from(options)
    Thread {
      try {
        promise.resolve(PdfCompressor.compress(reactApplicationContext, uri, opts))
      } catch (e: Throwable) {
        // Throwable, not Exception: a huge page can still run out of memory,
        // and that must reach JS as a rejection, not kill the app.
        promise.reject("INTERNAL_ERROR", e.message ?: e.toString(), e)
      }
    }.start()
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

// -- RenderOptions: normalized output options --

/**
 * The JS wrapper always sends all three keys; the fallbacks here only guard
 * direct native callers.
 */
private data class RenderOptions(
  val format: String,
  val quality: Int,
  val maxDimension: Int,
) {
  val isPng: Boolean get() = format == "png"
  val fileExtension: String get() = if (isPng) "png" else "jpg"

  companion object {
    fun from(map: ReadableMap): RenderOptions {
      val rawFormat = if (map.hasKey("format")) map.getString("format") else null
      val rawQuality = if (map.hasKey("quality")) map.getInt("quality") else 80
      val rawMax = if (map.hasKey("maxDimension")) map.getInt("maxDimension") else 0
      return RenderOptions(
        format = if (rawFormat == "png") "png" else "jpeg",
        quality = rawQuality.coerceIn(1, 100),
        maxDimension = rawMax.coerceAtLeast(0),
      )
    }
  }
}

// -- Compression --

private data class CompressOptions(val dpi: Float, val quality: Int, val maxDimension: Int) {
  companion object {
    fun from(map: ReadableMap): CompressOptions {
      val dpi = if (map.hasKey("dpi")) map.getDouble("dpi") else 150.0
      val quality = if (map.hasKey("quality")) map.getInt("quality") else 70
      val max = if (map.hasKey("maxDimension")) map.getInt("maxDimension") else 2200
      return CompressOptions(
        dpi = dpi.coerceIn(50.0, 300.0).toFloat(),
        quality = quality.coerceIn(1, 100),
        maxDimension = max.coerceAtLeast(0),
      )
    }
  }
}

private object PdfCompressor {
  fun compress(context: ReactApplicationContext, uri: String, options: CompressOptions): WritableNativeMap {
    val descriptor = openPdfDescriptor(context, uri)
    val originalBytes = descriptor.statSize
    val output = File(context.cacheDir, "${UUID.randomUUID()}.pdf")
    var pageCount = 0
    try {
      PdfRenderer(descriptor).use { renderer ->
        JpegPdfWriter(output).use { writer ->
          pageCount = renderer.pageCount
          for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
              // PdfRenderer reports page size in points (1/72 inch).
              val ptW = page.width.toFloat()
              val ptH = page.height.toFloat()
              var scale = options.dpi / 72f
              val longEdge = maxOf(ptW, ptH)
              if (options.maxDimension > 0 && longEdge * scale > options.maxDimension) {
                scale = options.maxDimension / longEdge
              }
              val w = (ptW * scale).toInt().coerceAtLeast(1)
              val h = (ptH * scale).toInt().coerceAtLeast(1)
              val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
              try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                val jpeg = ByteArrayOutputStream().also {
                  bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality, it)
                }.toByteArray()
                writer.addPage(jpeg, w, h, ptW, ptH)
              } finally {
                bitmap.recycle()
              }
            }
          }
          writer.finish()
        }
      }
    } catch (e: Throwable) {
      output.delete()
      throw e
    } finally {
      try { descriptor.close() } catch (_: Exception) {}
    }
    return WritableNativeMap().apply {
      putString("uri", "file://${output.absolutePath}")
      putInt("pageCount", pageCount)
      putDouble("originalBytes", originalBytes.toDouble())
      putDouble("bytes", output.length().toDouble())
    }
  }
}

/**
 * The smallest valid PDF that holds one JPEG per page. Android's PdfDocument
 * would store the page bitmaps losslessly (Flate), defeating the purpose, so
 * the JPEG bytes are embedded directly as DCTDecode image streams.
 */
internal class JpegPdfWriter(file: File) : Closeable {
  private val out = BufferedOutputStream(FileOutputStream(file))
  private var position = 0L
  /** Byte offset of object n at index n - 1. Objects 1 and 2 are the catalog and page tree. */
  private val offsets = mutableListOf(0L, 0L)
  private val pageIds = mutableListOf<Int>()
  private var finished = false

  init {
    ascii("%PDF-1.4\n")
    bytes(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
  }

  fun addPage(jpeg: ByteArray, pixelWidth: Int, pixelHeight: Int, pointWidth: Float, pointHeight: Float) {
    val image = beginObject()
    ascii("<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight " +
      "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
    bytes(jpeg)
    ascii("\nendstream\nendobj\n")

    val drawing = "q ${num(pointWidth)} 0 0 ${num(pointHeight)} 0 0 cm /Im0 Do Q"
    val content = beginObject()
    ascii("<< /Length ${drawing.length} >>\nstream\n$drawing\nendstream\nendobj\n")

    val page = beginObject()
    ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(pointWidth)} ${num(pointHeight)}] " +
      "/Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>\nendobj\n")
    pageIds.add(page)
  }

  fun finish() {
    offsets[0] = position
    ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    offsets[1] = position
    ascii("2 0 obj\n<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>\nendobj\n")
    val xref = position
    ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
    // Each xref entry is exactly 20 bytes.
    for (offset in offsets) ascii(String.format(Locale.US, "%010d 00000 n \n", offset))
    ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    out.flush()
    finished = true
  }

  override fun close() {
    out.close()
  }

  private fun beginObject(): Int {
    offsets.add(position)
    val id = offsets.size
    ascii("$id 0 obj\n")
    return id
  }

  private fun ascii(text: String) = bytes(text.toByteArray(Charsets.ISO_8859_1))

  private fun bytes(data: ByteArray) {
    out.write(data)
    position += data.size
  }

  private fun num(value: Float) = String.format(Locale.US, "%.2f", value)
}

private fun openPdfDescriptor(context: ReactApplicationContext, uri: String): ParcelFileDescriptor {
  return when {
    uri.startsWith("content://") ->
      context.contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")
        ?: throw IOException("Cannot open content URI: $uri")
    uri.startsWith("file://") ->
      ParcelFileDescriptor.open(File(uri.removePrefix("file://")), ParcelFileDescriptor.MODE_READ_ONLY)
    else -> ParcelFileDescriptor.open(File(uri), ParcelFileDescriptor.MODE_READ_ONLY)
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

  fun renderPage(index: Int, scale: Float, options: RenderOptions): WritableNativeMap {
    val cacheKey = "$index:$scale:${options.format}:${options.quality}:${options.maxDimension}"
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

    // maxDimension caps the long edge: shrink the effective scale when the
    // requested scale would exceed it.
    var effectiveScale = scale
    val longEdge = maxOf(page.width, page.height)
    if (options.maxDimension > 0 && longEdge * effectiveScale > options.maxDimension) {
      effectiveScale = options.maxDimension.toFloat() / longEdge
    }

    val width = (page.width * effectiveScale).toInt().coerceAtLeast(1)
    val height = (page.height * effectiveScale).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // White background — also guarantees JPEG (no alpha) loses nothing.
    canvas.drawColor(Color.WHITE)

    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()

    val outFile = File(context.cacheDir, "${UUID.randomUUID()}.${options.fileExtension}")
    FileOutputStream(outFile).use { out ->
      // JPEG (default) is ~10-20x smaller than PNG for scanned pages.
      if (options.isPng) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      } else {
        bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality, out)
      }
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
