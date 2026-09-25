import NativePdfPageImage from './NativePdfPageImage';

export type PageImage = {
  uri: string;
  width: number;
  height: number;
};

export type PdfInfo = {
  uri: string;
  pageCount: number;
};

export type PageImageFormat = 'jpeg' | 'png';

export type GenerateOptions = {
  /** Output encoding. JPEG is ~10–20× smaller for scanned pages. Default: 'jpeg'. */
  format?: PageImageFormat;
  /** JPEG quality 1–100 (ignored for PNG). Default: 80. */
  quality?: number;
  /**
   * Cap the output's long edge in pixels — the effective scale is reduced
   * when scale × page size would exceed it. Ideal for thumbnails without
   * knowing the page dimensions. 0 (default) = no cap.
   */
  maxDimension?: number;
};

export type CompressOptions = {
  /** Resolution of the re-encoded pages. Default 150 (sharp on screen and in print), range 50–300. */
  dpi?: number;
  /** JPEG quality 1–100. Default 70. */
  quality?: number;
  /** Cap the long edge of each page image in pixels. Default 2200; 0 = no cap. */
  maxDimension?: number;
};

export type CompressResult = {
  /** file:// URI of the new PDF in the temp/cache directory. */
  uri: string;
  pageCount: number;
  originalBytes: number;
  bytes: number;
};

const normalizeCompressOptions = (options?: CompressOptions) => ({
  dpi: Math.min(300, Math.max(50, Math.round(options?.dpi ?? 150))),
  quality: Math.min(100, Math.max(1, Math.round(options?.quality ?? 70))),
  maxDimension: Math.max(0, Math.round(options?.maxDimension ?? 2200)),
});

const clampScale = (scale?: number): number =>
  Math.min(10, Math.max(0.1, scale ?? 1.0));

const normalizeOptions = (options?: GenerateOptions) => ({
  format: options?.format === 'png' ? 'png' : 'jpeg',
  quality: Math.min(100, Math.max(1, Math.round(options?.quality ?? 80))),
  maxDimension: Math.max(0, Math.round(options?.maxDimension ?? 0)),
});

export class PdfPageImage {
  static async open(uri: string): Promise<PdfInfo> {
    return NativePdfPageImage.openPdf(uri);
  }

  static async generate(
    uri: string,
    page: number,
    scale?: number,
    options?: GenerateOptions,
  ): Promise<PageImage> {
    return NativePdfPageImage.generate(
      uri,
      page,
      clampScale(scale),
      normalizeOptions(options),
    );
  }

  static async generateAllPages(
    uri: string,
    scale?: number,
    options?: GenerateOptions,
  ): Promise<PageImage[]> {
    return NativePdfPageImage.generateAllPages(
      uri,
      clampScale(scale),
      normalizeOptions(options),
    );
  }

  /**
   * Write a smaller copy of a PDF: every page re-encoded as a JPEG. Page
   * sizes are kept; text becomes part of the image (no longer selectable).
   * The original is untouched — compare `bytes` with `originalBytes` and
   * keep the copy only if it is smaller.
   */
  static async compress(uri: string, options?: CompressOptions): Promise<CompressResult> {
    return NativePdfPageImage.compress(uri, normalizeCompressOptions(options));
  }

  static async close(uri: string): Promise<void> {
    return NativePdfPageImage.closePdf(uri);
  }
}

export default PdfPageImage;
