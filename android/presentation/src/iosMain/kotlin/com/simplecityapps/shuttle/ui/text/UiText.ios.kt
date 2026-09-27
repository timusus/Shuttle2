package com.simplecityapps.shuttle.ui.text

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.localizedStringWithFormat

/**
 * [this] text from [bundle]'s Localizable table, looked up by [StringKey.key] / [PluralKey.key]. A plural's key
 * names a `.stringsdict` entry, so Foundation picks the variant for the count. The iOS catalogue is keyed like
 * Android's `strings.xml`; its formats take a plural's count as `%1$d` and every other argument as a string
 * (`%N$@`, where Android has `%N$s` or `%N$d`). Until phase 5 ships the catalogue, a missing key resolves to
 * the key itself.
 */
fun UiText.resolve(bundle: NSBundle = NSBundle.mainBundle): String = when (this) {
    is UiText.Resource -> formatLocalized(bundle.localized(key.key), args.strings(bundle))
    is UiText.Plural -> formatPlural(bundle.localized(key.key), count, args.strings(bundle))
}

private fun NSBundle.localized(key: String): String = localizedStringForKey(key, value = null, table = null)

private fun List<Any>.strings(bundle: NSBundle): List<String> = map { if (it is UiText) it.resolve(bundle) else it.toString() }

// Kotlin/Native passes a variadic Objective-C argument by its static type (an NSString as an object, an Int as a C
// int, so a boxed Int would print as a pointer) and only spreads a literal array, hence one call per argument count.

/** [format] with each of [args] as a string argument. */
internal fun formatLocalized(format: String, args: List<String>): String = when (args.size) {
    0 -> format
    1 -> NSString.localizedStringWithFormat(format, args[0].ns)
    2 -> NSString.localizedStringWithFormat(format, args[0].ns, args[1].ns)
    3 -> NSString.localizedStringWithFormat(format, args[0].ns, args[1].ns, args[2].ns)
    else -> error("UiText supports up to 3 format arguments, got ${args.size}")
}

/** A `.stringsdict` plural [format] with [count] as its first argument, then each of [args] as a string. */
internal fun formatPlural(format: String, count: Int, args: List<String>): String = when (args.size) {
    0 -> NSString.localizedStringWithFormat(format, count)
    1 -> NSString.localizedStringWithFormat(format, count, args[0].ns)
    2 -> NSString.localizedStringWithFormat(format, count, args[0].ns, args[1].ns)
    else -> error("UiText supports up to 2 plural arguments after the count, got ${args.size}")
}

@Suppress("CAST_NEVER_SUCCEEDS")
private val String.ns: NSString get() = this as NSString
