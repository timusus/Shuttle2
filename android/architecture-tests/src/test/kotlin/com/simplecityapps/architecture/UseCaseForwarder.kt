package com.simplecityapps.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

/** Whether this class is only an `invoke` that [forwardsToOwnProperty]; the caller can inject that dependency itself. */
internal fun KoClassDeclaration.isForwarder(): Boolean {
    val invoke = functions(includeNested = false, includeLocal = false).singleOrNull { it.name == "invoke" } ?: return false
    val others = functions(includeNested = false, includeLocal = false)
        .any { it != invoke && it.hasPublicOrDefaultModifier && !it.hasOverrideModifier }
    val constructorParameters = primaryConstructor?.parameters.orEmpty()
    if (others || hasSecondaryConstructors() || Regex("""\binit\s*\{""").containsMatchIn(text)) return false
    if (constructorParameters.any { !it.hasValModifier && !it.hasVarModifier }) return false
    val ownProperties = constructorParameters.map { it.name } + properties(includeNested = false).map { it.name }
    return forwardsToOwnProperty(invoke.text, invoke.parameters.map { it.name }, ownProperties)
}

/**
 * `invoke`'s body reads or calls through one of [ownProperties] (`dep.call(args)`, `this.dep.inner.call<T>(args)`,
 * `dep.prop`) with [parameters] as the arguments, optionally followed by a chain of lambda-free calls (`.flowOn(..)`,
 * `.first()`, `.distinctUntilChanged()`). Mirrors codehealth's `kotlin-usecase-pass-through` rule.
 */
internal fun forwardsToOwnProperty(invokeText: String, parameters: List<String>, ownProperties: List<String>): Boolean {
    val afterName = invokeText.substringAfter("fun invoke")
    var depth = 0
    val close = afterName.indexOfFirst { char ->
        if (char == '(') {
            depth++
        } else if (char == ')') {
            depth--
        }
        char == ')' && depth == 0
    }
    if (close < 0) return false
    val signatureEnd = afterName.substring(close + 1)
    val body = signatureEnd.substring(signatureEnd.indexOfFirst { it == '=' || it == '{' }.takeIf { it >= 0 } ?: return false).trim()
    val arguments = parameters.joinToString("""\s*,\s*""") { Regex.escape(it) }
    val generics = """(?:<[^()]*>)?"""
    val path = """(?:\s*\.\s*\w+)"""
    // Parameters must reach the dependency; with none, a bare property read (`= store.folders`) qualifies too.
    val call = """\s*\.\s*\w+$generics\(\s*$arguments\s*\)"""
    val target = if (parameters.isEmpty()) "$path*(?:$call)?" else "$path*$call"
    val chain = """(?:\s*\.\s*\w+$generics(?:\([\w.\s,]*\))?)*"""
    val forward = Regex("""^(?:=\s*|\{\s*(?:return\s+)?)(?:this\s*\.\s*)?(\w+)$target$chain\s*\}?$""")
    val receiver = forward.matchEntire(body)?.groupValues?.get(1) ?: return false
    return receiver in ownProperties
}
