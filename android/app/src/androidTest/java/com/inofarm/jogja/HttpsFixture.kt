package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit

/** A test-only certificate trusted by this client alone; platform hostname verification stays enabled. */
internal fun httpsFixture(server: MockWebServer): OkHttpClient {
    val keyStore=KeyStore.getInstance("PKCS12")
    val password="fixture-only-password".toCharArray()
    InstrumentationRegistry.getInstrumentation().context.assets.open("mock-server.p12").use {keyStore.load(it,password)}
    val keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {init(keyStore,password)}
    val trusts=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {init(keyStore)}
    val trust=trusts.trustManagers.filterIsInstance<X509TrustManager>().single()
    val ssl=SSLContext.getInstance("TLS").apply {init(keys.keyManagers,arrayOf(trust),null)}
    server.useHttps(ssl.socketFactory,false)
    return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory,trust).followRedirects(false)
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
}
