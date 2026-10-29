package dev.netlens

/** Preserve Java's protocol-text trimming and split rules during the language port. */
internal fun String.javaTrim(): String = trim { it <= ' ' }
internal fun String.javaSplit(pattern: String, limit: Int = 0): Array<String> =
    java.util.regex.Pattern.compile(pattern).split(this, limit)

