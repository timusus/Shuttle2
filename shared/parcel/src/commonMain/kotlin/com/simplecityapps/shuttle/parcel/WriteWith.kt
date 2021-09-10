package com.simplecityapps.shuttle.parcel
/**
 * Specifies what [Parceler] should be used for the annotated type.
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE)
expect annotation class WriteWith<P : Parceler<*>>