package com.simplecityapps.shuttle.parcel

/**
 * Specifies what [Parceler] should be used for the annotated type.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE)
actual annotation class WriteWith<P>
