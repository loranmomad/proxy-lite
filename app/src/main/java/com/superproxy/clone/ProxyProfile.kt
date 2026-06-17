package com.superproxy.clone

import com.google.gson.annotations.SerializedName

data class ProxyProfile(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("type") val type: String // "HTTP" or "SOCKS5"
) {
    fun display(): String = "$name ($type) - $host:$port"
}
