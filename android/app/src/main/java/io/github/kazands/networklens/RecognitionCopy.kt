package io.github.kazands.networklens

/** Explanations describe the evidence used; they never supply missing identity fields. */
internal object RecognitionCopy {
    const val NEXT_SCAN = "If a device is missing or hard to identify, make sure it is awake and connected to this Wi-Fi, then scan again. Some devices do not share identifying details."
    const val UNNAMED = "This device replied, but we did not receive a usable name or model. Its address can help you tell it apart from other devices."
    fun explanation(match: DeviceCatalog.Match): String = when {
        match.product.id == KiddeInfo.PRODUCT_ID -> "Identified from matching Kidde discovery names and the WD model field. The exact model and sensor capabilities were not reported."
        match.product.id == SmartThingsInfo.PRODUCT_ID -> "Identified from this device’s SmartThings service and matching hub identifier. The exact generation was not verified."
        match.product.id == EnvisalinkInfo.PRODUCT_ID -> "Identified from this device’s Envisalink alarm-interface greeting. The exact model was not reported."
        match.product.id.startsWith("matter:") -> "Matched locally using the vendor and product IDs shared by this device and the bundled Matter registry."
        match.product.id.startsWith("protocol:") || match.product.id.startsWith("omada:") ->
            "Identified from the model shared through this device's local discovery service."
        match.recog != null -> "Matched locally using the model code shared by this device and the offline Apple model catalogue."
        else -> "Matched locally using details shared by this device and the offline product catalogue."
    }
}
