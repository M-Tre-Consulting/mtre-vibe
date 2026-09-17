package com.vibe.core.network.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PkceCrypto {

    fun generateCodeVerifier(length: Int = 64): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(length)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(
            code,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        ).take(length)
    }

    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
