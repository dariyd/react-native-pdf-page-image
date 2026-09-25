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

export declare class PdfPageImage {
  /**
   * Opens a PDF and returns page count.
   * @param uri - PDF file URI (file://, http://, data:, content://)
   */
  static open(uri: string): Promise<PdfInfo>;

  /**
   * Renders a single page to an image (JPEG by default).
   * @param uri - PDF file URI
   * @param page - Page index (0-based)
   * @param scale - Scale factor in px per PDF point (default: 1.0, range: 0.1–10.0)
   * @param options - Output format, quality and size cap
   */
  static generate(
    uri: string,
    page: number,
    scale?: number,
    options?: GenerateOptions,
  ): Promise<PageImage>;

  /**
   * Renders all pages to images (JPEG by default).
   * @param uri - PDF file URI
   * @param scale - Scale factor in px per PDF point (default: 1.0, range: 0.1–10.0)
   * @param options - Output format, quality and size cap
   */
  static generateAllPages(
    uri: string,
    scale?: number,
    options?: GenerateOptions,
  ): Promise<PageImage[]>;

  /**
   * Write a smaller copy of a PDF: every page re-encoded as a JPEG (text is
   * no longer selectable). The original is untouched.
   */
  static compress(uri: string, options?: CompressOptions): Promise<CompressResult>;

  /**
   * Closes the PDF and deletes temporary image files.
   * @param uri - PDF file URI previously passed to open/generate
   */
  static close(uri: string): Promise<void>;
}

export default PdfPageImage;
