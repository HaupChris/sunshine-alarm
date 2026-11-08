package de.hauptmann.sunshinealarm.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.X509Certificate

class TrustOnFirstUseTest {
    private class MemoryStore : PinStore { override var fingerprint: String? = null }

    /** Minimal fake certificate, only getEncoded() is used */
    private fun cert(bytes: ByteArray): X509Certificate = object : X509Certificate() {
        override fun getEncoded() = bytes
        override fun checkValidity() = Unit
        override fun checkValidity(date: java.util.Date?) = Unit
        override fun getVersion() = 3
        override fun getSerialNumber() = java.math.BigInteger.ONE
        override fun getIssuerDN() = null
        override fun getSubjectDN() = null
        override fun getNotBefore() = null
        override fun getNotAfter() = null
        override fun getTBSCertificate() = bytes
        override fun getSignature() = bytes
        override fun getSigAlgName() = ""
        override fun getSigAlgOID() = ""
        override fun getSigAlgParams() = null
        override fun getIssuerUniqueID() = null
        override fun getSubjectUniqueID() = null
        override fun getKeyUsage() = null
        override fun getBasicConstraints() = -1
        override fun verify(key: java.security.PublicKey?) = Unit
        override fun verify(key: java.security.PublicKey?, sigProvider: String?) = Unit
        override fun toString() = "fake"
        override fun getPublicKey() = null
        override fun hasUnsupportedCriticalExtension() = false
        override fun getCriticalExtensionOIDs() = null
        override fun getNonCriticalExtensionOIDs() = null
        override fun getExtensionValue(oid: String?) = null
    }

    @Test fun `pins first certificate and rejects a different one`() {
        val store = MemoryStore()
        val tm = TrustOnFirstUseManager(store)
        val a = cert(byteArrayOf(1, 2, 3))

        tm.checkServerTrusted(arrayOf(a), "RSA")
        assertEquals(TrustOnFirstUseManager.sha256(byteArrayOf(1, 2, 3)), store.fingerprint)

        tm.checkServerTrusted(arrayOf(a), "RSA") // same cert still fine
        assertThrows(CertificateChangedException::class.java) {
            tm.checkServerTrusted(arrayOf(cert(byteArrayOf(9))), "RSA")
        }
    }
}
