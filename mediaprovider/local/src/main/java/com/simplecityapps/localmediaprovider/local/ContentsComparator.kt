package com.simplecityapps.localmediaprovider.local

interface ContentsComparator<T> {

    fun areContentsEqual(other: T): Boolean
}