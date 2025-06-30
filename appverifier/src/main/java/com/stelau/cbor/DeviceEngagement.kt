package com.stelau.cbor

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceEngagement(
    @JsonProperty("0") val version: String,
    @JsonProperty("-7") val cevData: ByteArray?
)
