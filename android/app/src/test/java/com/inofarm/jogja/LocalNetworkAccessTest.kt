package com.inofarm.jogja

import com.inofarm.jogja.core.network.LocalNetworkAccess
import org.junit.Assert.*
import org.junit.Test

class LocalNetworkAccessTest {
    @Test fun privateServersRequirePermissionOnlyOnApi37AndPublicLoopbackDoNot() {
        for(server in listOf("http://10.0.2.2:8001/api/v2/","https://192.168.1.10/api/v2/","https://172.16.0.1/api/v2/","https://farm.local/api/v2/","https://[fd00::1]/api/v2/")) {
            assertTrue(LocalNetworkAccess.requiredFor(server,37));assertFalse(LocalNetworkAccess.requiredFor(server,29))
        }
        for(server in listOf("https://farm.example.org/api/v2/","http://127.0.0.1:8000/api/v2/","https://10.example.org/api/v2/","bad url")) assertFalse(LocalNetworkAccess.requiredFor(server,37))
    }
}
