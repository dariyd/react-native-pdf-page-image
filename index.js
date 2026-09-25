import { TurboModuleRegistry } from 'react-native';

const NativePdfPageImage = TurboModuleRegistry.getEnforcing('PdfPageImage');

const normalizeCompressOptions = (options) => ({
  dpi: Math.min(300, Math.max(50, Math.round(options?.dpi ?? 150))),
  quality: Math.min(100, Math.max(1, Math.round(options?.quality ?? 70))),
  maxDimension: Math.max(0, Math.round(options?.maxDimension ?? 2200)),
});

const clampScale = (scale) =>
  Math.min(10, Math.max(0.1, scale ?? 1.0));

const normalizeOptions = (options) => ({
  format: options?.format === 'png' ? 'png' : 'jpeg',
  quality: Math.min(100, Math.max(1, Math.round(options?.quality ?? 80))),
  maxDimension: Math.max(0, Math.round(options?.maxDimension ?? 0)),
});

export class PdfPageImage {
  static async open(uri) {
    return NativePdfPageImage.openPdf(uri);
  }

  static async generate(uri, page, scale, options) {
    return NativePdfPageImage.generate(
      uri,
      page,
      clampScale(scale),
      normalizeOptions(options),
    );
  }

  static async generateAllPages(uri, scale, options) {
    return NativePdfPageImage.generateAllPages(
      uri,
      clampScale(scale),
      normalizeOptions(options),
    );
  }

  static async compress(uri, options) {
    return NativePdfPageImage.compress(uri, normalizeCompressOptions(options));
  }

  static async close(uri) {
    return NativePdfPageImage.closePdf(uri);
  }
}

export default PdfPageImage;
