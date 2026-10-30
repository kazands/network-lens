package io.github.kazands.networklens

import java.util.Collections
import java.util.TreeSet

/** Names and nearby IPs do not establish device identity. */
class Grouping private constructor() {
    interface Endpoint {
        fun isSelf(): Boolean
        fun hardwareAddress(): String
        fun links(): Collection<String>
    }

    private class Facts(endpoint: Endpoint) {
        val self = endpoint.isSelf()
        val mac = endpoint.hardwareAddress()
        val links = endpoint.links().filterTo(HashSet()) { it.isNotEmpty() }
    }

    companion object {
        @JvmStatic fun <T : Endpoint> groups(endpoints: List<T>): MutableList<MutableList<T>> {
            val groups = ArrayList<MutableList<T>>()
            val facts = ArrayList<MutableList<Facts>>()
            val index = HashMap<String, MutableSet<Int>>()
            val selfGroups = TreeSet<Int>()
            for (endpoint in endpoints) {
                val next = Facts(endpoint)
                val candidates = TreeSet<Int>()
                if (next.self) candidates.addAll(selfGroups)
                else for (link in next.links) candidates.addAll(index[link] ?: emptySet())
                var match = -1
                for (candidate in candidates) {
                    if (facts[candidate].all { related(next, it) }) { match = candidate; break }
                }
                if (match < 0) {
                    match = groups.size
                    groups.add(ArrayList())
                    facts.add(ArrayList())
                }
                groups[match].add(endpoint)
                facts[match].add(next)
                if (next.self) selfGroups.add(match)
                else for (link in next.links) index.getOrPut(link) { HashSet() }.add(match)
            }
            return groups
        }
        private fun related(a: Facts, b: Facts): Boolean {
            if (a.self || b.self) return a.self && b.self
            if (a.mac.isNotEmpty() && b.mac.isNotEmpty() && a.mac != b.mac) return false
            return !Collections.disjoint(a.links, b.links)
        }
    }
}

