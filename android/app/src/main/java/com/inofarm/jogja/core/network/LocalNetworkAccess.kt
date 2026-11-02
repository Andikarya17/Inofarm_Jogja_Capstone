package com.inofarm.jogja.core.network

import java.net.URI

/** No DNS probing: request only for a server explicitly configured with a private address/.local. */
object LocalNetworkAccess {
    const val PERMISSION="android.permission.ACCESS_LOCAL_NETWORK"
    fun requiredFor(base:String,sdk:Int):Boolean {
        if(sdk<37) return false
        val host=runCatching { URI(base.trim()).host?.lowercase()?.removeSurrounding("[","]") }.getOrNull() ?: return false
        if(host.endsWith(".local")) return true
        if(':' in host) return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")
        val parts=host.split('.').map {it.toIntOrNull()}
        if(parts.size!=4 || parts.any {it==null || it !in 0..255}) return false
        return parts[0]==10 || (parts[0]==172 && parts[1]!! in 16..31) || (parts[0]==192 && parts[1]==168) || (parts[0]==169 && parts[1]==254)
    }
}
