export type PageImage = {
  uri: string;
  width: number;
  height: number;
};

export type PdfInfo = {
  uri: string;
  pageCount: number;
};

export declare class PdfPageImage {
  /**
   * Opens a PDF and returns page count.
   * @param uri - PDF file URI (file://, http://, data:, content://)
   */
  static open(uri: string): Promise<PdfInfo>;

  /**
   * Renders a single page to a PNG image.
   * @param uri - PDF file URI
   * @param page - Page index (0-based)
   * @param scale - Scale factor (default: 1.0, range: 0.1–10.0)
   */
  static generate(uri: string, page: number, scale?: number): Promise<PageImage>;

  /**
   * Renders all pages to PNG images.
   * @param uri - PDF file URI
   * @param scale - Scale factor (default: 1.0, range: 0.1–10.0)
   */
  static generateAllPages(uri: string, scale?: number): Promise<PageImage[]>;

  /**
   * Closes the PDF and deletes temporary PNG files.
   * @param uri - PDF file URI previously passed to open/generate
   */
  static close(uri: string): Promise<void>;
}

export default PdfPageImage;
