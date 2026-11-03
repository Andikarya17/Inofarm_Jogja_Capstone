package com.inofarm.jogja

import com.inofarm.jogja.core.network.FinanceHttpApi
import org.junit.Assert.*
import org.junit.Test

class FinanceApiBaseTest {
    @Test fun releaseRequiresHttpsAndPreservesSubdirectoryScope() {
        val api=FinanceHttpApi(false)
        assertEquals("https://example.invalid/farm/api/v2/",api.validateBase(" https://example.invalid/farm/api/v2 "))
        for(url in listOf("http://example.invalid/api/v2/","https://example.invalid/api/v1/","https://user:password@example.invalid/api/v2/","https://example.invalid/api/v2/?token=x","https://example.invalid/api/v2/#part")) assertThrows(IllegalArgumentException::class.java) {api.validateBase(url)}
    }
    @Test fun onlyDebugAllowsExplicitLocalHttpWithoutChangingServers() {
        val api=FinanceHttpApi(true)
        for(base in listOf("http://127.0.0.1:8001/api/v2/","http://10.0.2.2:8000/api/v2/","https://example.invalid/custom/api/v2/")) assertEquals(base,api.validateBase(base))
    }
}
