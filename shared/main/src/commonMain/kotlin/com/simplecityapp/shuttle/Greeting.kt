package com.simplecityapp.shuttle

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}