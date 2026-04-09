import { TurboModuleRegistry } from 'react-native';

const NativePdfPageImage = TurboModuleRegistry.getEnforcing('PdfPageImage');

const clampScale = (scale) =>
  Math.min(10, Math.max(0.1, scale ?? 1.0));

export class PdfPageImage {
  static async open(uri) {
    return NativePdfPageImage.openPdf(uri);
  }

  static async generate(uri, page, scale) {
    return NativePdfPageImage.generate(uri, page, clampScale(scale));
  }

  static async generateAllPages(uri, scale) {
    return NativePdfPageImage.generateAllPages(uri, clampScale(scale));
  }

  static async close(uri) {
    return NativePdfPageImage.closePdf(uri);
  }
}

export default PdfPageImage;
