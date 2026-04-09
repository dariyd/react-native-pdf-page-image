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

export interface Spec extends TurboModule {
  openPdf(uri: string): Promise<PdfInfo>;
  generate(uri: string, page: number, scale: number): Promise<PageImage>;
  generateAllPages(uri: string, scale: number): Promise<PageImage[]>;
  closePdf(uri: string): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PdfPageImage');
