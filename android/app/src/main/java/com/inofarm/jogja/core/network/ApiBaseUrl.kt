package com.inofarm.jogja.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Transport validation without loading the retired device/owner client. */
object ApiBaseUrl {
    fun validate(value:String,allowHttp:Boolean):String {
        val url=value.trim().trimEnd('/').plus('/').toHttpUrl()
        require(url.scheme=="https" || (allowHttp && url.scheme=="http")) {"Gunakan HTTPS; HTTP hanya pada APK debug lokal"}
        require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) {"URL API tidak boleh mengandung credential/query/fragment"}
        return url.toString()
    }
}
