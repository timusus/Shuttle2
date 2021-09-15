package com.simplecityapps.shuttle

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}