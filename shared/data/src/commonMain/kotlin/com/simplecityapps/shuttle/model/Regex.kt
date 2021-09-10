package com.simplecityapps.shuttle.model

object Regex {
    val articlePattern = Regex("^(?i)\\s*(?:the |an |a )|(?:, the|, an|, a)\\s*$|[\\[\\]()!?.,']")
}

fun String.removeArticles(): String {
    return Regex.articlePattern.replace(this, "")
}