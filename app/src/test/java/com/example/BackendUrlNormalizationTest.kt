package com.example

import com.example.data.normalizeBackendBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendUrlNormalizationTest {

    @Test
    fun stripsHealthAndEndpointSuffixes() {
        assertEquals(
            "https://my-walkie-backend.onrender.com",
            normalizeBackendBaseUrl("https://my-walkie-backend.onrender.com/")
        )
        assertEquals(
            "https://my-walkie-backend.onrender.com",
            normalizeBackendBaseUrl("https://my-walkie-backend.onrender.com/healthz")
        )
        assertEquals(
            "https://my-walkie-backend.onrender.com",
            normalizeBackendBaseUrl("https://my-walkie-backend.onrender.com/health")
        )
        assertEquals(
            "https://my-walkie-backend.onrender.com",
            normalizeBackendBaseUrl("https://my-walkie-backend.onrender.com/api")
        )
        assertEquals(
            "https://my-walkie-backend.onrender.com",
            normalizeBackendBaseUrl("https://my-walkie-backend.onrender.com/api/livekit/token")
        )
        assertEquals(
            "https://my-walkie-backend.onrender.com",
            normalizeBackendBaseUrl("my-walkie-backend.onrender.com/api/health/")
        )
        assertEquals(
            "",
            normalizeBackendBaseUrl("   ")
        )
    }
}
