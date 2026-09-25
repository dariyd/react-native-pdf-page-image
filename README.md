# @dariyd/react-native-pdf-page-image

Render PDF pages to JPEG or PNG images in React Native. Built for the **New Architecture** (TurboModules + Codegen).

- **iOS**: PDFKit
- **Android**: PdfRenderer

## Installation

```bash
npm install @dariyd/react-native-pdf-page-image
# or
yarn add @dariyd/react-native-pdf-page-image
```

### iOS

```bash
cd ios && pod install
```

### Android

No additional setup required — auto-linked via Gradle.

## Requirements

- React Native **0.76+** (New Architecture enabled)
- iOS **15.0+**
- Android API **24+**

## What's new in 2.1

- **`PdfPageImage.compress(uri, options?)`** — writes a smaller copy of a PDF with every page re-encoded as a JPEG (default 150 dpi, quality 70). Page sizes are kept; text becomes part of the page image. Works page by page on a background thread, so long scans don't spike memory. On Android the JPEGs are embedded directly (the system `PdfDocument` would store them uncompressed).

```typescript
const result = await PdfPageImage.compress('file:///path/to/scan.pdf', { dpi: 150, quality: 70 });
if (result.bytes < result.originalBytes * 0.8) {
  // keep result.uri (a file:// in the temp/cache directory)
}
```

Best for scans and photos. A PDF made of text (an emailed invoice) may come out larger — always compare `bytes` with `originalBytes`.

## What changed in 2.0

- **JPEG output by default** (`quality: 80`) — ~10–20× smaller than PNG for scanned/photographic pages. Pass `{ format: 'png' }` for the old behavior.
- **iOS renders at true 1× scale.** Previously the output was silently multiplied by the device screen scale (3× on modern iPhones), so `scale: 1.0` produced 3× the pixels — and different sizes than Android. Now 1 PDF point = `scale` pixels on both platforms.
- **New `maxDimension` option** — cap the long edge in pixels without knowing the page size. Ideal for thumbnails.
- **iOS temp files moved** from the app's Documents directory to the temporary directory (no more user-visible litter if `close()` is missed).

## API

### `PdfPageImage.open(uri)`

Opens a PDF and returns page count.

```typescript
const info = await PdfPageImage.open('file:///path/to/document.pdf');
console.log(info.pageCount); // 5
```

**Returns:** `Promise<{ uri: string, pageCount: number }>`

### `PdfPageImage.generate(uri, page, scale?, options?)`

Renders a single page to an image.

```typescript
// Full-quality render at 2x
const image = await PdfPageImage.generate('file:///path/to/document.pdf', 0, 2.0);

// Lightweight thumbnail: long edge capped at 640 px
const thumb = await PdfPageImage.generate(pdfUri, 0, 1.0, { maxDimension: 640 });
console.log(thumb.uri);    // file:///.../<uuid>.jpg
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `uri` | `string` | PDF file URI |
| `page` | `number` | Page index (0-based) |
| `scale` | `number?` | Pixels per PDF point (default: `1.0`, range: `0.1` – `10.0`) |
| `options` | `GenerateOptions?` | Output options (below) |

**`GenerateOptions`:**

| Option | Type | Description |
|--------|------|-------------|
| `format` | `'jpeg' \| 'png'` | Output encoding (default: `'jpeg'`) |
| `quality` | `number?` | JPEG quality 1–100 (default: `80`; ignored for PNG) |
| `maxDimension` | `number?` | Cap the long edge in pixels; reduces the effective scale when needed (default: `0` = no cap) |

**Returns:** `Promise<{ uri: string, width: number, height: number }>`

### `PdfPageImage.generateAllPages(uri, scale?, options?)`

Renders all pages to images. Same `options` as `generate`, applied per page.

```typescript
const pages = await PdfPageImage.generateAllPages(pdfUri, 1.5, { quality: 90 });
pages.forEach((page, i) => {
  console.log(`Page ${i}: ${page.uri} (${page.width}x${page.height})`);
});
```

**Returns:** `Promise<Array<{ uri: string, width: number, height: number }>>`

### `PdfPageImage.close(uri)`

Closes the PDF and deletes temporary image files. Call this when you're done to free memory.

```typescript
await PdfPageImage.close('file:///path/to/document.pdf');
```

**Returns:** `Promise<void>`

## Supported URI formats

| Format | Example |
|--------|---------|
| File path | `/path/to/file.pdf` |
| File URI | `file:///path/to/file.pdf` |
| HTTP/HTTPS | `https://example.com/doc.pdf` |
| Base64 data URI | `data:application/pdf;base64,JVBERi0...` |
| Content URI (Android) | `content://com.provider/doc.pdf` |

## Example

```typescript
import { PdfPageImage } from '@dariyd/react-native-pdf-page-image';

async function renderPdfThumbnails(pdfUri: string) {
  try {
    const { pageCount } = await PdfPageImage.open(pdfUri);
    console.log(`PDF has ${pageCount} pages`);

    // Render first page as a lightweight thumbnail
    const thumbnail = await PdfPageImage.generate(pdfUri, 0, 1.0, {
      maxDimension: 640,
    });
    // Use thumbnail.uri in an <Image /> component

    // Or render all pages at full quality
    const allPages = await PdfPageImage.generateAllPages(pdfUri, 2.0, {
      quality: 90,
    });

    // Clean up when done
    await PdfPageImage.close(pdfUri);
  } catch (error) {
    console.error('PDF rendering failed:', error);
  }
}
```

## Notes

- Pages are cached per URI + page index + scale + options — repeated calls return cached results instantly
- Temporary image files are stored in the temporary directory (iOS) or cache directory (Android)
- Always call `close()` when done to free memory and delete temporary files
- Pages are drawn on a white background, so JPEG (no alpha channel) loses nothing
- Page rotation (90°, 180°, 270°) is handled automatically on iOS

## License

MIT
