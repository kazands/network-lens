@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.Grouping
import java.util.*

object GroupingTest {
    private var checks: Int = 0

    class Endpoint : Grouping.Endpoint {
        var self: Boolean = false

        var mac: String = ""

        var keys: MutableList<String> = ArrayList()

        constructor(self: Boolean, mac: String, vararg keys: String) {
            this.self = self
            this.mac = mac
            this.keys.addAll(Arrays.asList(*keys))
        }

        override fun isSelf(): Boolean {
            return self
        }

        override fun hardwareAddress(): String {
            return mac
        }

        override fun links(): Collection<String> {
            return keys
        }
    }

    private fun expect(count: Int, vararg endpoints: Endpoint): Unit {
        checks++
        if (((Grouping.groups(Arrays.asList(*endpoints)))!!.size != count)) {
            throw AssertionError(("Grouping case " + checks))
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        expect(0)
        expect(1, Endpoint(true, ""), Endpoint(true, ""), Endpoint(true, ""))
        expect(2, Endpoint(true, "", "host:x"), Endpoint(false, "", "host:x"))
        expect(2, Endpoint(false, ""), Endpoint(false, ""))
        // Same names alone have no key.
        expect(1, Endpoint(false, "", "host:x"), Endpoint(false, "", "host:x"))
        expect(1, Endpoint(false, "", "id:x"), Endpoint(false, "", "id:x"))
        expect(2, Endpoint(false, "", "id:x"), Endpoint(false, "", "id:y"))
        expect(
            2,
            Endpoint(false, "00:11:22:33:44:55", "id:x"),
            Endpoint(false, "00:11:22:33:44:66", "id:x"),
        )
        expect(1, Endpoint(false, "00:11:22:33:44:55", "id:x"), Endpoint(false, "", "id:x"))
        expect(
            2,
            Endpoint(false, "", "id:a"),
            Endpoint(false, "", "id:a", "id:b"),
            Endpoint(false, "", "id:b"),
        )
        // No weak transitive bridge.
        expect(2, Endpoint(false, "", ""), Endpoint(false, "", ""))
        var maximum: MutableList<Endpoint> = ArrayList()
        run {
            var i: Int = 0
            while ((i < 1200)) {
                maximum.add(Endpoint(false, "", ("id:" + i)))
                i++
            }
        }
        checks++
        if (((Grouping.groups(maximum))!!.size != 1200)) {
            throw AssertionError("Maximum inventory retains unrelated endpoints")
        }
        System.out.println((("PASS · " + checks) + " grouping assertions"))
    }
}
