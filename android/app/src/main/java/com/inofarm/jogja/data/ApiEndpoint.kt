package com.inofarm.jogja.data

/** Login-only default selection. Active sessions retain their original server and cache scope. */
object ApiEndpoint {
    fun forLogin(saved: String, configured: String): String {
        val existing = saved.trim()
        val default = configured.trim()
        val legacyLocal = existing.trimEnd('/') in setOf("http://127.0.0.1:8000/api/v1", "http://localhost:8000/api/v1")
        // Both addresses name the same host server. Migrate only the old debug port/path.
        return if (existing.isBlank() || (legacyLocal && default == "http://10.0.2.2:8000/api/v1/")) default else existing
    }
}
