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
  func generate(_ uri: String, page: Int, scale: Float, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      do {
        let doc = try self?.getOrOpen(uri: uri)
        let result = try doc?.renderPage(index: page, scale: CGFloat(scale))
        resolve(result)
      } catch {
        reject("INTERNAL_ERROR", error.localizedDescription, error)
      }
    }
  }

  @objc
  func generateAllPages(_ uri: String, scale: Float, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      do {
        let doc = try self?.getOrOpen(uri: uri)
        guard let doc = doc else {
          reject("INTERNAL_ERROR", "Document not available", nil)
          return
        }
        var pages: [[String: Any]] = []
        for i in 0..<doc.pageCount {
          let result = try doc.renderPage(index: i, scale: CGFloat(scale))
          pages.append(result)
        }
        resolve(pages)
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

  func renderPage(index: Int, scale: CGFloat) throws -> [String: Any] {
    let cacheKey = "\(index):\(scale)"
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

    let mediaBox = page.bounds(for: .mediaBox)
    let rotation = page.rotation

    var width = mediaBox.width
    var height = mediaBox.height
    if rotation == 90 || rotation == 270 {
      swap(&width, &height)
    }

    let scaledWidth = width * scale
    let scaledHeight = height * scale
    let size = CGSize(width: scaledWidth, height: scaledHeight)

    let renderer = UIGraphicsImageRenderer(size: size)
    let image = renderer.image { ctx in
      UIColor.white.setFill()
      ctx.fill(CGRect(origin: .zero, size: size))

      let context = ctx.cgContext
      context.translateBy(x: 0, y: scaledHeight)
      context.scaleBy(x: scale, y: -scale)

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

    guard let pngData = image.pngData() else {
      throw NSError(domain: "PdfPageImage", code: 500,
                    userInfo: [NSLocalizedDescriptionKey: "Could not convert image to PNG format"])
    }

    let outputURL = outputFilename()
    try pngData.write(to: outputURL)
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

  private func outputFilename() -> URL {
    let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    return dir.appendingPathComponent("\(UUID().uuidString).png")
  }

  // MARK: - URI Loading

  private static func loadData(uri: String) throws -> Data {
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
