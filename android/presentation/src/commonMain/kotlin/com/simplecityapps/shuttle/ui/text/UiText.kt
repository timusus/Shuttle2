package com.simplecityapps.shuttle.ui.text

/**
 * User-visible text that shared code names but doesn't resolve: each platform turns it into a string where it
 * draws, from its own catalogue (Android: `strings.xml` through `Resources.getString(UiText)`; iOS: the
 * Localizable table through `UiText.resolve()`). Shared ViewModels and their state carry this, never a
 * resource id or a resolved string.
 */
sealed interface UiText {
    /** The string [key], formatted with [args] in order; an arg that is itself a [UiText] is resolved first. */
    data class Resource(
        val key: StringKey,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * The plural [key]'s variant for [count]. The count is always the string's first format argument (`%1$d`),
     * whether or not the variant shows it; [args] follow it, from `%2$`.
     */
    data class Plural(
        val key: PluralKey,
        val count: Int,
        val args: List<Any> = emptyList(),
    ) : UiText
}

/** [this] string with no arguments. */
val StringKey.text: UiText get() = UiText.Resource(this)
