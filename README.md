# @dariyd/react-native-pdf-page-image

Render PDF pages to PNG images in React Native. Built for the **New Architecture** (TurboModules + Codegen).

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

## API

### `PdfPageImage.open(uri)`

Opens a PDF and returns page count.

```typescript
const info = await PdfPageImage.open('file:///path/to/document.pdf');
console.log(info.pageCount); // 5
```

**Returns:** `Promise<{ uri: string, pageCount: number }>`

### `PdfPageImage.generate(uri, page, scale?)`

Renders a single page to a PNG image.

```typescript
const image = await PdfPageImage.generate('file:///path/to/document.pdf', 0, 2.0);
console.log(image.uri);    // file:///.../<uuid>.png
console.log(image.width);  // 1224
console.log(image.height); // 1584
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `uri` | `string` | PDF file URI |
| `page` | `number` | Page index (0-based) |
| `scale` | `number?` | Scale factor (default: `1.0`, range: `0.1` – `10.0`) |

**Returns:** `Promise<{ uri: string, width: number, height: number }>`

### `PdfPageImage.generateAllPages(uri, scale?)`

Renders all pages to PNG images.

```typescript
const pages = await PdfPageImage.generateAllPages('file:///path/to/document.pdf', 1.5);
pages.forEach((page, i) => {
  console.log(`Page ${i}: ${page.uri} (${page.width}x${page.height})`);
});
```

**Returns:** `Promise<Array<{ uri: string, width: number, height: number }>>`

### `PdfPageImage.close(uri)`

Closes the PDF and deletes temporary PNG files. Call this when you're done to free memory.

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

    // Render first page as thumbnail
    const thumbnail = await PdfPageImage.generate(pdfUri, 0, 0.5);
    // Use thumbnail.uri in an <Image /> component

    // Or render all pages
    const allPages = await PdfPageImage.generateAllPages(pdfUri, 1.0);

    // Clean up when done
    await PdfPageImage.close(pdfUri);
  } catch (error) {
    console.error('PDF rendering failed:', error);
  }
}
```

## Notes

- Pages are cached per URI + page index + scale — repeated calls return cached results instantly
- Temporary PNG files are stored in the app's documents directory (iOS) or cache directory (Android)
- Always call `close()` when done to free memory and delete temporary files
- Page rotation (90°, 180°, 270°) is handled automatically on iOS

## License

MIT
