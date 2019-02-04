package com.simplecityapps.localmediaprovider

interface ContentsComparator<T> {

    fun areContentsEqual(other: T): Boolean
}