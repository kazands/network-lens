package io.github.kazands.networklens

/** Legacy evidence vocabulary. No socket reader or active TPI check is exposed. */
internal object EnvisalinkInfo {
    const val PORT = 4025
    const val SOURCE = "Envisalink identity check"
    const val SERVICE = "Envisalink TPI"
    const val PRODUCT_ID = "envisalink:tpi-family"
    private val greeting = "5053CD\r\n".toByteArray(Charsets.US_ASCII)
    enum class Report(val connected: Boolean, val text: String) {
        IDENTIFIED(true, "Envisalink greeting received; model not reported"),
        OTHER(true, "No Envisalink greeting reported"),
        NO_GREETING(true, "No greeting received; service may be in use"),
        UNAVAILABLE(false, "Service unavailable")
    }
    fun matches(bytes: ByteArray): Boolean = bytes.contentEquals(greeting)
    fun identity() = DeviceCatalog.Match(DeviceCatalog.Product(PRODUCT_ID, "Envisacor", "Envisalink",
        DeviceCatalog.Category.ALARM_INTERFACE, emptySet(), familyOnly = true, titleSource = DeviceCatalog.TitleSource.CATALOGUE),
        "Envisalink DSC TPI greeting", "5053CD (login request; checksum verified)")

}
