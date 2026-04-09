#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(PdfPageImage, NSObject)

RCT_EXTERN_METHOD(openPdf:(NSString *)uri
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(generate:(NSString *)uri
                  page:(int)page
                  scale:(float)scale
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(generateAllPages:(NSString *)uri
                  scale:(float)scale
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(closePdf:(NSString *)uri
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end
