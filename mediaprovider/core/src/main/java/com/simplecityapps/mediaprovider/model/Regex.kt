package com.simplecityapps.mediaprovider.model

import java.util.regex.Pattern

object Regex {
    val articlePattern: Pattern = Pattern.compile("^(?i)\\s*(?:the |an |a )|(?:, the|, an|, a)\\s*$|[\\[\\]()!?.,']")
}

fun String.removeArticles(): String {
    return Regex.articlePattern.matcher(this).replaceAll("")
}