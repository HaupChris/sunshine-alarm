package de.hauptmann.sunshinealarm.api

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/** Storage for the pinned certificate fingerprint of one device */
interface PinStore {
    var fingerprint: String?
}

class CertificateChangedException(expected: String, actual: String) :
    CertificateException("Certificate of the wake-up light changed (pinned $expected, got $actual)")

/**
 * The Somneo serves a self-signed certificate, so normal validation always fails.
 * Instead: trust on first use. The first certificate seen is pinned; afterwards
 * only that exact certificate is accepted, so another device on the network
 * cannot pose as the wake-up light.
 */
class TrustOnFirstUseManager(private val store: PinStore) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
        if (chain.isEmpty()) throw CertificateException("Empty certificate chain")
        val actual = sha256(chain[0].encoded)
        when (val pinned = store.fingerprint) {
            null -> store.fingerprint = actual
            actual -> Unit
            else -> throw CertificateChangedException(pinned, actual)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) =
        throw CertificateException("Client certificates are not supported")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
    }
}
