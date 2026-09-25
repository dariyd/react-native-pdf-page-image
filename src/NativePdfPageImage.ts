import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

type PageImage = {
  uri: string;
  width: number;
  height: number;
};

type PdfInfo = {
  uri: string;
  pageCount: number;
};

// All members required at the native boundary — the JS wrapper in index.ts
// normalizes user input and fills defaults.
type NativeGenerateOptions = {
  format: string; // 'jpeg' | 'png'
  quality: number; // 1–100, JPEG only
  maxDimension: number; // long-edge cap in px; 0 = no cap
};

type NativeCompressOptions = {
  dpi: number; // 50–300
  quality: number; // JPEG 1–100
  maxDimension: number; // long-edge cap in px; 0 = no cap
};

type CompressResult = {
  uri: string;
  pageCount: number;
  originalBytes: number;
  bytes: number;
};

export interface Spec extends TurboModule {
  openPdf(uri: string): Promise<PdfInfo>;
  generate(
    uri: string,
    page: number,
    scale: number,
    options: NativeGenerateOptions,
  ): Promise<PageImage>;
  generateAllPages(
    uri: string,
    scale: number,
    options: NativeGenerateOptions,
  ): Promise<PageImage[]>;
  compress(uri: string, options: NativeCompressOptions): Promise<CompressResult>;
  closePdf(uri: string): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PdfPageImage');
