package com.spotisave.app

import com.spotisave.app.data.api.SpotifyAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class SpotifyAuthManagerTest {

    @Test
    fun testCodeVerifierGeneration() {
        val verifier1 = SpotifyAuthManager.generateCodeVerifier(64)
        val verifier2 = SpotifyAuthManager.generateCodeVerifier(64)

        assertEquals(64, verifier1.length)
        assertEquals(64, verifier2.length)
        assertNotEquals(verifier1, verifier2)

        // RFC 7636 unreserved characters: [A-Z], [a-z], [0-9], "-", ".", "_", "~"
        val regex = Regex("^[A-Za-z0-9\\-._~]{64}$")
        assertTrue("Verifier enthält nur RFC 7636 Zeichen", regex.matches(verifier1))
        assertTrue("Verifier enthält nur RFC 7636 Zeichen", regex.matches(verifier2))
    }

    @Test
    fun testRfc7636OfficialTestVectorChallenge() {
        // Offizieller RFC 7636 Appendix B Test-Vector
        val testVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

        val generatedChallenge = SpotifyAuthManager.generateCodeChallenge(testVerifier)
        assertEquals("Code Challenge muss exakt RFC 7636 Appendix B entsprechen", expectedChallenge, generatedChallenge)
        assertFalse("Code Challenge darf kein '=' Padding enthalten", generatedChallenge.contains("="))
    }

    @Test
    fun testBuildAuthorizeUrl() {
        val clientId = "test_client_id_12345"
        val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        val redirectUri = "spotisave://callback"

        val authUrl = SpotifyAuthManager.buildAuthorizeUrl(
            clientId = clientId,
            codeChallenge = challenge,
            redirectUri = redirectUri
        )

        assertTrue(authUrl.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(authUrl.contains("client_id=$clientId"))
        assertTrue(authUrl.contains("response_type=code"))
        assertTrue(authUrl.contains("code_challenge_method=S256"))
        assertTrue(authUrl.contains("code_challenge=$challenge"))

        val decoded = URLDecoder.decode(authUrl, "UTF-8")
        assertTrue(decoded.contains("redirect_uri=spotisave://callback"))
        assertTrue(decoded.contains("user-read-playback-state"))
        assertTrue(decoded.contains("user-modify-playback-state"))
        assertTrue(decoded.contains("user-read-recently-played"))
    }
}
