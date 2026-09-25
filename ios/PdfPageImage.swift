import Foundation
import PDFKit
import UIKit

@objc(PdfPageImage)
class PdfPageImage: NSObject {
  private var documentCache: [String: PdfDocument] = [:]

  @objc
  func openPdf(_ uri: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      do {
        let doc = try self?.getOrOpen(uri: uri)
        resolve([
          "uri": uri,
          "pageCount": doc?.pageCount ?? 0,
        ])
      } catch {
        reject("INTERNAL_ERROR", error.localizedDescription, error)
      }
    }
  }

  @objc
  func generate(_ uri: String, page: Int, scale: Float, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      do {
        let doc = try self?.getOrOpen(uri: uri)
        let result = try doc?.renderPage(index: page, scale: CGFloat(scale), options: RenderOptions(options))
        resolve(result)
      } catch {
        reject("INTERNAL_ERROR", error.localizedDescription, error)
      }
    }
  }

  @objc
  func generateAllPages(_ uri: String, scale: Float, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      do {
        let doc = try self?.getOrOpen(uri: uri)
        guard let doc = doc else {
          reject("INTERNAL_ERROR", "Document not available", nil)
          return
        }
        let renderOptions = RenderOptions(options)
        var pages: [[String: Any]] = []
        for i in 0..<doc.pageCount {
          let result = try doc.renderPage(index: i, scale: CGFloat(scale), options: renderOptions)
          pages.append(result)
        }
        resolve(pages)
      } catch {
        reject("INTERNAL_ERROR", error.localizedDescription, error)
      }
    }
  }

  /// Re-encode every page as a JPEG at `dpi` and write a new PDF to the temp
  /// directory. Pages keep their size in points; text becomes part of the
  /// image. Runs page by page (autoreleasepool) so a long scan never holds
  /// more than one page bitmap in memory.
  @objc
  func compress(_ uri: String, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        resolve(try PdfCompressor.compress(uri: uri, options: CompressOptions(options)))
      } catch {
        reject("INTERNAL_ERROR", error.localizedDescription, error)
      }
    }
  }

  @objc
  func closePdf(_ uri: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    documentCache[uri]?.close()
    documentCache.removeValue(forKey: uri)
    resolve(nil)
  }

  @objc static func requiresMainQueueSetup() -> Bool { false }

  private func getOrOpen(uri: String) throws -> PdfDocument {
    if let cached = documentCache[uri] { return cached }
    let doc = try PdfDocument(uri: uri)
    documentCache[uri] = doc
    return doc
  }
}

// MARK: - RenderOptions

/// Normalized output options (the JS wrapper always sends all three keys;
/// defaults here only guard direct native callers).
struct RenderOptions {
  let format: String
  let quality: CGFloat
  let maxDimension: CGFloat

  init(_ dict: NSDictionary) {
    let rawFormat = dict["format"] as? String
    format = rawFormat == "png" ? "png" : "jpeg"
    let rawQuality = (dict["quality"] as? NSNumber)?.doubleValue ?? 80
    quality = CGFloat(min(100, max(1, rawQuality)))
    let rawMax = (dict["maxDimension"] as? NSNumber)?.doubleValue ?? 0
    maxDimension = CGFloat(max(0, rawMax))
  }

  var isPng: Bool { format == "png" }
  var fileExtension: String { isPng ? "png" : "jpg" }
}

// MARK: - CompressOptions

struct CompressOptions {
  let dpi: CGFloat
  let quality: CGFloat
  let maxDimension: CGFloat

  init(_ dict: NSDictionary) {
    let rawDpi = (dict["dpi"] as? NSNumber)?.doubleValue ?? 150
    dpi = CGFloat(min(300, max(50, rawDpi)))
    let rawQuality = (dict["quality"] as? NSNumber)?.doubleValue ?? 70
    quality = CGFloat(min(100, max(1, rawQuality)))
    let rawMax = (dict["maxDimension"] as? NSNumber)?.doubleValue ?? 2200
    maxDimension = CGFloat(max(0, rawMax))
  }
}

// MARK: - Page rendering (shared by generate and compress)

/// Draws `page` onto a white bitmap at `scale` px per point (capped by
/// `maxDimension` on the long edge). Returns the image and the page size in
/// points, rotation applied.
fileprivate func renderPageImage(_ page: PDFPage, scale: CGFloat, maxDimension: CGFloat) -> (image: UIImage, pointSize: CGSize, pixelSize: CGSize) {
  let mediaBox = page.bounds(for: .mediaBox)
  let rotation = page.rotation

  var width = mediaBox.width
  var height = mediaBox.height
  if rotation == 90 || rotation == 270 {
    swap(&width, &height)
  }

  // maxDimension caps the long edge: shrink the effective scale when the
  // requested scale would exceed it.
  var effectiveScale = scale
  let longEdge = max(width, height)
  if maxDimension > 0, longEdge * effectiveScale > maxDimension {
    effectiveScale = maxDimension / longEdge
  }

  let scaledWidth = width * effectiveScale
  let scaledHeight = height * effectiveScale
  let size = CGSize(width: scaledWidth, height: scaledHeight)

  // 1 pt == 1 px: the default renderer format multiplies by the device's
  // screen scale (3x on modern iPhones), silently tripling the output
  // resolution behind the caller's back.
  let rendererFormat = UIGraphicsImageRendererFormat.default()
  rendererFormat.scale = 1
  let renderer = UIGraphicsImageRenderer(size: size, format: rendererFormat)
  let image = renderer.image { ctx in
    UIColor.white.setFill()
    ctx.fill(CGRect(origin: .zero, size: size))

    let context = ctx.cgContext
    context.translateBy(x: 0, y: scaledHeight)
    context.scaleBy(x: effectiveScale, y: -effectiveScale)

    if rotation == 90 {
      context.translateBy(x: 0, y: -width)
      context.rotate(by: .pi / 2)
    } else if rotation == 180 {
      context.translateBy(x: -width, y: -height)
      context.rotate(by: .pi)
    } else if rotation == 270 {
      context.translateBy(x: -height, y: 0)
      context.rotate(by: -.pi / 2)
    }

    page.draw(with: .mediaBox, to: context)
  }
  return (image, CGSize(width: width, height: height), size)
}

// MARK: - PdfCompressor

fileprivate enum PdfCompressor {
  static func compress(uri: String, options: CompressOptions) throws -> [String: Any] {
    let data = try PdfDocument.loadData(uri: uri)
    guard let document = PDFDocument(data: data) else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Data is not a valid PDF"])
    }

    let outputURL = FileManager.default.temporaryDirectory
      .appendingPathComponent("\(UUID().uuidString).pdf")
    guard let consumer = CGDataConsumer(url: outputURL as CFURL),
          let pdf = CGContext(consumer: consumer, mediaBox: nil, nil) else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Could not create the output PDF"])
    }

    var failure: Error?
    for index in 0..<document.pageCount {
      autoreleasepool {
        guard failure == nil, let page = document.page(at: index) else { return }
        let rendered = renderPageImage(page, scale: options.dpi / 72, maxDimension: options.maxDimension)
        // A CGImage built from the JPEG bytes is embedded as-is (DCTDecode),
        // so the page costs exactly the JPEG size, not a re-encoded bitmap.
        guard let jpeg = rendered.image.jpegData(compressionQuality: options.quality / 100),
              let provider = CGDataProvider(data: jpeg as CFData),
              let cgImage = CGImage(jpegDataProviderSource: provider, decode: nil,
                                    shouldInterpolate: true, intent: .defaultIntent) else {
          failure = NSError(domain: "PdfPageImage", code: 500,
                            userInfo: [NSLocalizedDescriptionKey: "Could not encode page \(index)"])
          return
        }
        var box = CGRect(origin: .zero, size: rendered.pointSize)
        pdf.beginPage(mediaBox: &box)
        pdf.draw(cgImage, in: box)
        pdf.endPage()
      }
    }
    pdf.closePDF()
    if let failure = failure {
      try? FileManager.default.removeItem(at: outputURL)
      throw failure
    }

    let attributes = try? FileManager.default.attributesOfItem(atPath: outputURL.path)
    let bytes = (attributes?[.size] as? NSNumber)?.intValue ?? 0
    return [
      "uri": outputURL.absoluteString,
      "pageCount": document.pageCount,
      "originalBytes": data.count,
      "bytes": bytes,
    ]
  }
}

// MARK: - PdfDocument (handles loading, caching, rendering)

private class PdfDocument {
  private let document: PDFDocument
  private var pageCache: [String: [String: Any]] = [:]
  private var tempFiles: [URL] = []

  init(uri: String) throws {
    let data = try PdfDocument.loadData(uri: uri)
    guard let doc = PDFDocument(data: data) else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Data is not a valid PDF"])
    }
    self.document = doc
  }

  var pageCount: Int { document.pageCount }

  func renderPage(index: Int, scale: CGFloat, options: RenderOptions) throws -> [String: Any] {
    let cacheKey = "\(index):\(scale):\(options.format):\(options.quality):\(options.maxDimension)"
    if let cached = pageCache[cacheKey] { return cached }

    guard index >= 0, index < document.pageCount else {
      throw NSError(domain: "PdfPageImage", code: 404,
                    userInfo: [NSLocalizedDescriptionKey:
                      "Page number \(index) is invalid, file has \(document.pageCount) pages"])
    }

    guard let page = document.page(at: index) else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Could not load page \(index)"])
    }

    let rendered = renderPageImage(page, scale: scale, maxDimension: options.maxDimension)
    let image = rendered.image
    let scaledWidth = rendered.pixelSize.width
    let scaledHeight = rendered.pixelSize.height

    // JPEG (default) is ~10-20x smaller than PNG for scanned/photographic
    // pages; the white fill above guarantees no alpha is lost.
    let imageData: Data?
    if options.isPng {
      imageData = image.pngData()
    } else {
      imageData = image.jpegData(compressionQuality: options.quality / 100)
    }
    guard let data = imageData else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Could not encode image as \(options.format)"])
    }

    let outputURL = outputFilename(fileExtension: options.fileExtension)
    try data.write(to: outputURL)
    tempFiles.append(outputURL)

    let result: [String: Any] = [
      "uri": outputURL.absoluteString,
      "width": Int(scaledWidth),
      "height": Int(scaledHeight),
    ]
    pageCache[cacheKey] = result
    return result
  }

  func close() {
    pageCache.removeAll()
    for file in tempFiles {
      try? FileManager.default.removeItem(at: file)
    }
    tempFiles.removeAll()
  }

  private func outputFilename(fileExtension: String) -> URL {
    // Temporary directory, NOT Documents: these are scratch files the caller
    // copies out; writing them to Documents pollutes user-visible storage
    // (and backups) when close() is never reached.
    let dir = FileManager.default.temporaryDirectory
    return dir.appendingPathComponent("\(UUID().uuidString).\(fileExtension)")
  }

  // MARK: - URI Loading

  fileprivate static func loadData(uri: String) throws -> Data {
    if uri.hasPrefix("data:") {
      return try loadBase64(uri)
    }

    let url: URL
    if uri.hasPrefix("file://") {
      url = URL(string: uri)!
    } else if uri.hasPrefix("http://") || uri.hasPrefix("https://") {
      url = URL(string: uri)!
      return try Data(contentsOf: url)
    } else {
      url = URL(fileURLWithPath: uri)
    }

    guard FileManager.default.fileExists(atPath: url.path) else {
      throw NSError(domain: "PdfPageImage", code: 404,
                    userInfo: [NSLocalizedDescriptionKey: "File Not Found: \(url.path)"])
    }

    return try Data(contentsOf: url)
  }

  private static func loadBase64(_ uri: String) throws -> Data {
    guard let commaIndex = uri.firstIndex(of: ",") else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Header not found in base64 string"])
    }
    let base64String = String(uri[uri.index(after: commaIndex)...])
    guard let data = Data(base64Encoded: base64String) else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to decode base64 string"])
    }
    return data
  }
}
