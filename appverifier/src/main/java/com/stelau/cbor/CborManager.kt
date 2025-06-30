package com.stelau.cbor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory

object CborManager {
    private var objectMapper: ObjectMapper? = null

    fun initialize() {
        val cborFactory = CBORFactory()
        objectMapper = ObjectMapper(cborFactory)
    }

    fun decode(cborData: ByteArray): DeviceEngagement {
        return objectMapper?.readValue(cborData, DeviceEngagement::class.java)
            ?: DeviceEngagement("", byteArrayOf())
    }
}