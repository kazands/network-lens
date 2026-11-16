package io.github.kazands.networklens

import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/** Bounded XML for local read-only audio protocols; never resolves external resources. */
internal object BoundedXml {
    class Node(val name: String, val ns: String, val attrs: Map<String, String>) {
        val children = ArrayList<Node>(); val text = StringBuilder()
        fun one(name: String, ns: String) = children.singleOrNull { it.name == name && it.ns == ns }
        fun scalar(): String? = if (children.isEmpty()) text.toString() else null
    }
    /** No DTD/entities, nested scalar fields, unbounded strings or XML work. */
    fun parse(bytes: ByteArray): Node? {
        if (bytes.isEmpty() || bytes.size > 65536) return null
        return try {
            val handler = Xml()
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newSAXParser().xmlReader.apply {
                contentHandler = handler; errorHandler = handler; entityResolver = handler
                setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            }.parse(InputSource(ByteArrayInputStream(bytes)))
            handler.root
        } catch (_: Exception) { null }
    }
    private class Xml : DefaultHandler(), LexicalHandler {
        var root: Node? = null; val stack = ArrayList<Node>(); var nodes = 0
        override fun startElement(uri: String, local: String, q: String, attrs: Attributes) {
            if (++nodes > 1000 || stack.size >= 20 || attrs.length > 32) throw SAXException("XML limit")
            val fields = LinkedHashMap<String, String>()
            for (i in 0 until attrs.length) {
                if (attrs.getValue(i).length > 2048) throw SAXException("Attribute limit")
                if (attrs.getURI(i).isEmpty()) fields[attrs.getLocalName(i)] = attrs.getValue(i)
            }
            val node = Node(local, uri, fields)
            if (stack.isEmpty()) { if (root != null) throw SAXException("Repeated root"); root = node } else stack.last().children.add(node)
            stack.add(node)
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (stack.isEmpty()) return
            if (stack.last().text.length + length > 65536) throw SAXException("Text limit")
            stack.last().text.append(ch, start, length)
        }
        override fun endElement(uri: String, local: String, q: String) { stack.removeAt(stack.lastIndex) }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External entity")
        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD") }
        override fun error(e: SAXParseException) { throw e }
        override fun fatalError(e: SAXParseException) { throw e }
        override fun endDTD() {}; override fun startEntity(name: String?) {}; override fun endEntity(name: String?) {}
        override fun startCDATA() {}; override fun endCDATA() {}; override fun comment(ch: CharArray, start: Int, length: Int) {}
    }
}
