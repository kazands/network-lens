@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.LocalServices
import io.github.kazands.networklens.MdnsPacket
import java.util.Locale

/** Native callback representation must match current wire data without widening wire syntax. */
object LocalServicesTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        for (type in LocalServices.INITIAL) {
            var bare: String = type.substring(0, (type.length - 1))
            for (form in
                arrayOf(
                    type,
                    bare,
                    ("." + bare),
                    ("." + type),
                    (type + "local"),
                    (type + "local."),
                    (("." + type) + "local."),
                    bare.uppercase(Locale.ROOT),
                )) {
                check(
                    (LocalServices.canonicalType(form))!!.equals(type),
                    ("Native forms identify the same service: " + form),
                )
                check(
                    (LocalServices.canonicalType(LocalServices.canonicalType(form)))!!.equals(type),
                    "Canonicalization is stable",
                )
                check(
                    (LocalServices.tcpConfirmation(form) ==
                        ((type.endsWith("._tcp.") && !(type.equals("_matter._tcp."))))),
                    "Representation cannot turn UDP or Matter into a TCP probe",
                )
            }
        }
        for (invalid in
            arrayOf(
                null,
                "",
                ".",
                ".._http._tcp",
                "_http.._tcp",
                "_http._tcp..",
                "Printer._http._tcp",
                "_sub._http._tcp",
                "_http._tcp.example.com",
                "_http._tcp.local.evil",
                "_http._sctp",
                "http._tcp",
                "_http._tcp\u0000",
                "_ht\ntp._tcp",
                " _http._tcp",
                "_http._tcp ",
                "_h\u202ettp._tcp",
                (("_" + "a".repeat(63)) + "._tcp"),
                (("_" + "a".repeat(128)) + "._tcp"),
            )) {
            check(
                (LocalServices.canonicalType(invalid))!!.isEmpty(),
                "Malformed/foreign types stay invalid",
            )
            if ((invalid !== null)) {
                check(
                    !(LocalServices.tcpConfirmation(invalid)),
                    "Invalid native type cannot trigger TCP",
                )
            }
        }
        check(
            (LocalServices.canonicalType((("._" + "a".repeat(62)) + "._tcp")))!!.equals(
                (("_" + "a".repeat(62)) + "._tcp.")
            ),
            "Maximum supported service label remains valid",
        )
        check(
            (MdnsPacket.serviceType("._http._tcp.local"))!!.isEmpty(),
            "Native normalization does not relax the wire parser",
        )
        check(
            (MdnsPacket.serviceType("_http._tcp.local"))!!.equals("_http._tcp."),
            "Wire format remains unchanged",
        )
        System.out.println(
            (("PASS · " + checks) + " native service-type normalization and transport checks")
        )
    }
}
