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

const clampScale = (scale?: number): number =>
  Math.min(10, Math.max(0.1, scale ?? 1.0));

export class PdfPageImage {
  static async open(uri: string): Promise<PdfInfo> {
    return NativePdfPageImage.openPdf(uri);
  }

  static async generate(
    uri: string,
    page: number,
    scale?: number,
  ): Promise<PageImage> {
    return NativePdfPageImage.generate(uri, page, clampScale(scale));
  }

  static async generateAllPages(
    uri: string,
    scale?: number,
  ): Promise<PageImage[]> {
    return NativePdfPageImage.generateAllPages(uri, clampScale(scale));
  }

  static async close(uri: string): Promise<void> {
    return NativePdfPageImage.closePdf(uri);
  }
}

export default PdfPageImage;
