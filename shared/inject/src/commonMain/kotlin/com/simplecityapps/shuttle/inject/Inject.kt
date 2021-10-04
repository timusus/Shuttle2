package com.simplecityapps.shuttle.inject

@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class Inject()