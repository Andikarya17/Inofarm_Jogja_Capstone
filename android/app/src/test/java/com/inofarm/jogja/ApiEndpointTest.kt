package com.inofarm.jogja

import com.inofarm.jogja.data.ApiEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEndpointTest {
    @Test fun emulatorLoginRecoversLegacyLoopbackWithoutChangingOtherServers() {
        val local = "http://10.0.2.2:8000/api/v1/"
        assertEquals(local, ApiEndpoint.forLogin("http://127.0.0.1:8000/api/v1/", local))
        assertEquals(local, ApiEndpoint.forLogin("", local))
        for (saved in listOf("https://owner.example/api/v1/", "http://127.0.0.1:8001/api/v1/", "http://localhost:8000/custom/")) {
            assertEquals(saved, ApiEndpoint.forLogin(saved, local))
        }
    }
    @Test fun releaseDoesNotInventServerAndUsbRetainsItsLoopback() {
        assertEquals("", ApiEndpoint.forLogin("", ""))
        assertEquals("https://owner.example/api/v1/", ApiEndpoint.forLogin("", "https://owner.example/api/v1/"))
        val usb = "http://127.0.0.1:8000/api/v1/"
        assertEquals(usb, ApiEndpoint.forLogin(usb, usb))
        assertEquals(usb, ApiEndpoint.forLogin(usb, ""))
    }
}
