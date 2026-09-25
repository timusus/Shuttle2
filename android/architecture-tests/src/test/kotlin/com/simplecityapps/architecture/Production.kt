package com.simplecityapps.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File

/** The production sources of every module under `android/`: no test source sets, no generated code. */
object Production {
    val scope: KoScope by lazy {
        Konsist.scopeFromDirectory("android").slice { file ->
            val path = file.path.replace('\\', '/')
            "/src/" in path &&
                "/build/" !in path &&
                "/architecture-tests/" !in path &&
                !file.sourceSetName.contains("test", ignoreCase = true)
        }
    }

    val classes: List<KoClassDeclaration> by lazy { scope.classes(includeNested = true, includeLocal = false) }

    val viewModels: List<KoClassDeclaration> by lazy {
        classes.filter { it.hasParentWithName("ViewModel", "AndroidViewModel") }
    }
}

/** The file's path relative to the repo root. */
val KoFileDeclaration.relativePath: String
    get() = File(path).relativeTo(File(requireNotNull(System.getProperty("architecture.rootDir")))).invariantSeparatorsPath

/** The Gradle path of the module a file lives in, e.g. `:android:mediaprovider:local`. */
val KoFileDeclaration.module: String
    get() = ":" + relativePath.substringBefore("/src/").replace('/', ':')

/** `package.FileName`, the fully-qualified name used for file-level violations. */
val KoFileDeclaration.fqn: String
    get() = listOfNotNull(packagee?.name, name).joinToString(".")

val KoClassDeclaration.fqn: String
    get() = fullyQualifiedName ?: "${containingFile.fqn}.$name"

val KoFileDeclaration.importNames: List<String>
    get() = imports.map { it.name }

/** Resolves a simple type name against a file's imports, falling back to its own package. */
fun KoFileDeclaration.resolve(simpleName: String): String = importNames.firstOrNull { it.substringAfterLast('.') == simpleName }
    ?: listOfNotNull(packagee?.name, simpleName).joinToString(".")

/** Simple type names mentioned in a type's text, so `Lazy<SongRepository>?` yields `Lazy` and `SongRepository`. */
fun typeNames(typeText: String): List<String> = Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(typeText).map { it.value }.toList()

fun KoClassDeclaration.constructorTypeNames(): List<String> = constructors
    .flatMap { it.parameters }
    .flatMap { typeNames(it.type.text) }
    .distinct()

fun KoFileDeclaration.violation(entry: String) = Violation(module, entry, relativePath)

fun KoClassDeclaration.violation(detail: String = "") = containingFile.violation("$fqn$detail")
