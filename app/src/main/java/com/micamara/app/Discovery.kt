package com.micamara.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

data class FoundCamera(
    val host: String,
    val rtsp: Boolean,
    val onvifPort: Int?,
)

/** Fuerza a la app a usar la WiFi (aunque no tenga internet) para hablar con la cámara. */
object WifiBinder {
    @Suppress("DEPRECATION")
    fun bind(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val wifi = cm.allNetworks.firstOrNull { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifi != null) cm.bindProcessToNetwork(wifi)
        } catch (e: Exception) {
            // si no se puede, se usa la red por defecto
        }
    }
}

/** Libera el uso exclusivo de la WiFi (para poder salir por datos móviles al ver la cámara remota). */
internal fun WifiBinder.unbind(context: Context) {
    try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm.bindProcessToNetwork(null)
    } catch (e: Exception) {
        // nada que hacer
    }
}

object Discovery {

    /** Busca cámaras en la red local: ONVIF (WS-Discovery) + escaneo del puerto RTSP 554. */
    suspend fun scan(context: Context): List<FoundCamera> = coroutineScope {
        val ws = async(Dispatchers.IO) { wsDiscovery(context) }
        val scanned = async(Dispatchers.IO) { subnetScan() }
        val wsHosts: Map<String, Int?> = ws.await()
        val openHosts: Set<String> = scanned.await()

        val hosts = (wsHosts.keys + openHosts)
            .sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }

        hosts.map { h ->
            async(Dispatchers.IO) {
                val rtsp = h in openHosts || tcpOpen(h, 554, 500)
                val onvif = wsHosts[h] ?: OnvifClient.probePort(h)
                FoundCamera(h, rtsp, onvif)
            }
        }.awaitAll().filter { it.rtsp || it.onvifPort != null }
    }

    private fun probeXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" " +
            "xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
            "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
            "xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">" +
            "<e:Header><w:MessageID>uuid:${UUID.randomUUID()}</w:MessageID>" +
            "<w:To e:mustUnderstand=\"true\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>" +
            "<w:Action e:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>" +
            "</e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"

    /** host -> puerto ONVIF anunciado por la cámara. */
    private fun wsDiscovery(context: Context): Map<String, Int?> {
        val result = mutableMapOf<String, Int?>()
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("micamara")
        try {
            lock?.setReferenceCounted(false)
            lock?.acquire()
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val data = probeXml().toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName("239.255.255.250"), 3702))
                val deadline = System.currentTimeMillis() + 3000
                val buf = ByteArray(16384)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val host = packet.address.hostAddress
                        if (host != null) {
                            val first = Regex("XAddrs[^>]*>([^<]+)<").find(text)
                                ?.groupValues?.get(1)?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                            val port = if (first == null) null else try {
                                val u = URL(first)
                                if (u.port > 0) u.port else 80
                            } catch (e: Exception) {
                                null
                            }
                            result[host] = port
                        }
                    } catch (e: SocketTimeoutException) {
                        // seguir esperando respuestas
                    }
                }
            }
        } catch (e: Exception) {
            // sin multicast: nos quedamos con el escaneo de puertos
        } finally {
            try {
                lock?.release()
            } catch (e: Exception) {
                // ignorar
            }
        }
        return result
    }

    /** Primeros tres números de la IP de la WiFi, por ejemplo "192.168.1.". */
    private fun localPrefix(): String? {
        val candidates = mutableListOf<Pair<String, String>>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (ni in interfaces) {
            if (!ni.isUp || ni.isLoopback) continue
            for (ia in ni.interfaceAddresses) {
                val a = ia.address
                if (a is Inet4Address && a.isSiteLocalAddress) {
                    candidates.add(ni.name to (a.hostAddress ?: continue))
                }
            }
        }
        val preferred = candidates.firstOrNull { it.first.startsWith("wlan") || it.first.startsWith("eth") }
            ?: candidates.firstOrNull()
            ?: return null
        return preferred.second.split(".").take(3).joinToString(".") + "."
    }

    private suspend fun subnetScan(): Set<String> = coroutineScope {
        val prefix = localPrefix() ?: return@coroutineScope emptySet<String>()
        val gate = Semaphore(64)
        (1..254).map { i ->
            async(Dispatchers.IO) {
                val ip = "$prefix$i"
                gate.withPermit { if (tcpOpen(ip, 554, 600)) ip else null }
            }
        }.awaitAll().filterNotNull().toSet()
    }
}
