package com.simplecityapps.shuttle.parcel

/**
 * Specifies what [Parceler] should be used for a particular type [T].
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Retention(AnnotationRetention.SOURCE)
@Repeatable
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
expect annotation class TypeParceler<T, P: Parceler<in T>>()