package com.simplecityapps.shuttle.ui.common;

import java.util.regex.Pattern

object Regex {

    val articlePattern: Pattern = Pattern.compile("^(?i)\\s*(?:the |an |a )|(?:, the|, an|, a)\\s*$|[\\[\\]()!?.,']")

}
