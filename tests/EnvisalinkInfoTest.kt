import io.github.kazands.networklens.*

object EnvisalinkInfoTest {
    private var checks = 0
    private fun check(ok: Boolean, message: String) { if (!ok) throw AssertionError(message); checks++ }
    @JvmStatic fun main(args: Array<String>) {
        val valid = "5053CD\r\n".toByteArray()
        check(EnvisalinkInfo.matches(valid), "Documented login greeting including checksum and CRLF")
        check("%02X".format("5053".sumOf { it.code } and 255) == "CD", "Checksum agrees with vendor specification")
        for (bad in listOf("", "Login:", "5053", "5053CD", "5053CC\r\n", "5051CB\r\n", "5053CD\n", "x5053CD\r\n", "5053CD\r\nextra"))
            check(!EnvisalinkInfo.matches(bad.toByteArray()), "Reject ambiguous/malformed frame: ${bad.length}")
        val evidence = DeviceCatalog.Evidence().apply { offerEnvisalink() }
        check(evidence.match()?.product?.title == "Envisalink" && evidence.match()!!.product.familyOnly, "Family recognition does not invent EVL generation")
        check(evidence.categories().single().category == DeviceCatalog.Category.ALARM_INTERFACE, "Physical alarm-interface category")
        check(RecognitionCopy.explanation(evidence.match()!!).contains("exact model was not reported"), "Explanation distinguishes model from family")
        val copy = evidence.copy()
        evidence.offer("Model", "Google Home", "Synthetic model")
        check(evidence.match() == null && evidence.categories().isEmpty(), "Contradictory product cannot restore a different hardware category")
        check(copy.match() != null, "Snapshot remains independent")
        val combined = DeviceCatalog.Evidence().apply { include(copy); include(evidence) }
        check(combined.match() == null && combined.categories().isEmpty() && combined.hasEnvisalinkIdentity(), "Grouped contradiction retains protocol constraint")
        val other = DeviceCatalog.Evidence().apply { offerEnvisalink(); offer("Manufacturer", "Synthetic maker", "Synthetic response") }
        check(other.match() == null && other.categories().isEmpty(), "Conflicting manufacturer suppresses match")

        check(EnvisalinkInfo::class.java.declaredMethods.none { method -> method.parameterTypes.any {
            java.net.Socket::class.java.isAssignableFrom(it)
        } }, "Legacy evidence vocabulary has no active socket reader")
        println("Envisalink checks passed: $checks")
    }
}
