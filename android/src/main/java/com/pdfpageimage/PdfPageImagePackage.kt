package com.pdfpageimage

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class PdfPageImagePackage : TurboReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == PdfPageImageModule.NAME) {
      PdfPageImageModule(reactContext)
    } else {
      null
    }
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      mapOf(
        PdfPageImageModule.NAME to ReactModuleInfo(
          PdfPageImageModule.NAME,
          PdfPageImageModule.NAME,
          false, // canOverrideExistingModule
          false, // needsEagerInit
          false, // isCxxModule
          true,  // isTurboModule
        )
      )
    }
  }
}
