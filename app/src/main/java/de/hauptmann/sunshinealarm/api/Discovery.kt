package de.hauptmann.sunshinealarm.api

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class FoundDevice(val host: String, val name: String)

/** Finds Somneo devices in the Wi-Fi via SSDP, like the Philips app does */
object Discovery {
    private const val SSDP_HOST = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private val SEARCH = (
        "M-SEARCH * HTTP/1.1\r\nHOST: $SSDP_HOST:$SSDP_PORT\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n"
        ).toByteArray()

    suspend fun search(context: Context, timeoutMs: Int = 3000): List<FoundDevice> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi.createMulticastLock("somneo-discovery").apply { setReferenceCounted(false); acquire() }
        val candidates = mutableSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.send(DatagramPacket(SEARCH, SEARCH.size, InetAddress.getByName(SSDP_HOST), SSDP_PORT))
                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    candidates += packet.address.hostAddress ?: continue
                }
            }
        } finally {
            lock.release()
        }
        candidates.mapNotNull { host -> describeBlocking(host)?.let { FoundDevice(host, it) } }
    }

    /**
     * Reads the UPnP description to confirm it is a Somneo. Only public device info
     * is read here, so this one request skips certificate checks.
     */
    private fun describeBlocking(host: String): String? = runCatching {
        val request = Request.Builder().url("https://$host/upnp/description.xml").build()
        insecure.newCall(request).execute().use { r ->
            val xml = r.body?.string().orEmpty()
            if (!r.isSuccessful || !(xml.contains("HF367") || xml.contains("Somneo", ignoreCase = true))) return null
            Regex("<friendlyName>(.*?)</friendlyName>").find(xml)?.groupValues?.get(1) ?: "Somneo"
        }
    }.getOrNull()

    private val insecure: OkHttpClient by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), null) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }
}
