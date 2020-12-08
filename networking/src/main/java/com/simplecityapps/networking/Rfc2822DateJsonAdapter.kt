package com.simplecityapps.networking

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class Rfc2822DateJsonAdapter : JsonAdapter<Date?>() {

    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): Date? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        val string = reader.nextString()
        return dateFormat.parse(string)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun toJson(
        writer: JsonWriter,
        value: Date?
    ) {
        if (value == null) {
            writer.nullValue()
        } else {
            val string = dateFormat.format(value)
            writer.value(string)
        }
    }

    companion object {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
    }
}