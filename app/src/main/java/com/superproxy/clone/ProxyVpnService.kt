package com.superproxy.clone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class ProxyVpnService : VpnService() {

    private var pfd: ParcelFileDescriptor? = null
    private var tun2socksProcess: Process? = null
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                val profile = ProxyManager.loadProfiles(this)
                    .firstOrNull { it.id == profileId } ?: return START_NOT_STICKY
                ProxyManager.setActiveProfileId(this, profile.id)
                startForeground(NOTIF_ID, buildNotification(profile))
                startVpn(profile)
            }
            ACTION_STOP -> {
                stopVpn()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(profile: ProxyProfile) {
        try {
            // Verify the proxy host is reachable
            verifyProxy(profile)

            // Configure the VPN interface to capture ALL traffic and prevent leaks
            val builder = Builder()
                .setSession("SuperProxyLite")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route all IPv4 traffic
                .addRoute("::", 0)      // Route all IPv6 traffic (forces no leaks)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(true)      // IMPORTANT: Use blocking mode to prevent busy-loop

            pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopVpn()
                return
            }

            Log.i(TAG, "VPN established. Capturing all traffic.")

            // Check if the tun2socks binary exists in assets
            val binaryFile = File(filesDir, "tun2socks")
            if (!binaryFile.exists()) {
                try {
                    assets.open("tun2socks").use { input ->
                        FileOutputStream(binaryFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    binaryFile.setExecutable(true)
                    Log.i(TAG, "tun2socks binary copied and made executable")
                } catch (e: Exception) {
                    Log.e(TAG, "tun2socks binary MISSING from assets folder! Traffic will be captured but dropped.", e)
                }
            }

            if (binaryFile.exists() && binaryFile.canExecute()) {
                startTun2Socks(profile, binaryFile)
            } else {
                // Fallback: Read and drop packets to prove the VPN is capturing traffic
                startPacketDropper()
            }

            ProxyManager.setVpnActive(this, true)
            TileSyncer.requestUpdate(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun startTun2Socks(profile: ProxyProfile, binaryFile: File) {
        try {
            val proxyUrl = if (profile.type == "SOCKS5") {
                "socks5://${profile.host}:${profile.port}"
            } else {
                "http://${profile.host}:${profile.port}"
            }

            val processBuilder = ProcessBuilder(
                binaryFile.absolutePath,
                "--fd", pfd!!.fd.toString(),
                "--proxy", proxyUrl,
                "--loglevel", "warning"
            )
            
            tun2socksProcess = processBuilder.redirectErrorStream(true).start()
            
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(tun2socksProcess!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.i("Tun2Socks", line ?: "")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "tun2socks output reader error", e)
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks process.", e)
            startPacketDropper()
        }
    }

    private fun startPacketDropper() {
        workerThread = Thread {
            val inputStream = FileInputStream(pfd!!.fileDescriptor)
            val buffer = ByteArray(32767)
            while (!Thread.interrupted()) {
                try {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        // We are capturing packets, but dropping them because there is no tun2socks engine.
                        // This will result in NO INTERNET, proving the VPN is capturing traffic.
                        Log.d(TAG, "Captured and dropped $length bytes of raw IP data")
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun verifyProxy(profile: ProxyProfile) {
        Thread {
            try {
                val socket = Socket()
                protect(socket) // Prevent loop
                socket.connect(InetSocketAddress(profile.host, profile.port), 3000)
                socket.close()
                Log.i(TAG, "Proxy probe succeeded for ${profile.host}")
            } catch (e: Exception) {
                Log.w(TAG, "Proxy probe failed (continuing anyway): ${e.message}")
            }
        }.start()
    }

    private fun stopVpn() {
        try {
            workerThread?.interrupt()
            tun2socksProcess?.destroy()
            pfd?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing pfd/process", e)
        }
        pfd = null
        tun2socksProcess = null
        ProxyManager.setVpnActive(this, false)
        TileSyncer.requestUpdate(this)
    }

    private fun buildNotification(profile: ProxyProfile): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Proxy VPN", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SuperProxyLite Active")
            .setContentText(profile.display())
            .setSmallIcon(R.drawable.ic_tile_proxy)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    companion object {
        private const val TAG = "ProxyVpnService"
        private const val NOTIF_ID = 0xC0DE
        private const val CHANNEL_ID = "proxy_vpn_channel"
        const val ACTION_START = "com.superproxy.clone.action.START"
        const val ACTION_STOP = "com.superproxy.clone.action.STOP"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}
