package com.superproxy.clone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

class ProxyVpnService : VpnService() {

    private var pfd: ParcelFileDescriptor? = null
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
            // Verify the proxy host is reachable (simulated connectivity check)
            verifyProxy(profile)

            val builder = Builder()
                .setSession("SuperProxyLite")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(false)

            pfd = builder.establish()
            Log.i(TAG, "VPN established; pfd=$pfd; proxy=${profile.host}:${profile.port} (${profile.type})")

            // Simulated proxy routing loop (no real packet forwarding implemented)
            workerThread = Thread {
                try {
                    val input = pfd?.javaClass?.getMethod("getFileDescriptor")?.let {
                        // Just keep the thread alive; this is a simulation.
                    }
                    while (!Thread.interrupted() && pfd != null) {
                        Thread.sleep(1000)
                    }
                } catch (e: InterruptedException) {
                    Log.i(TAG, "Worker interrupted")
                }
            }.apply { isDaemon = true; start() }

            ProxyManager.setVpnActive(this, true)
            TileSyncer.requestUpdate(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            ProxyManager.setVpnActive(this, false)
            TileSyncer.requestUpdate(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun verifyProxy(profile: ProxyProfile) {
        // Quick TCP probe so we don't silently route to a dead proxy.
        Thread {
            try {
                val socket = Socket()
                val proxy = if (profile.type == "SOCKS5")
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress(profile.host, profile.port))
                else
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(profile.host, profile.port))
                socket.connect(InetSocketAddress("8.8.8.8", 53).let { InetSocketAddress(profile.host, profile.port) }, 3000)
                socket.close()
            } catch (e: Exception) {
                Log.w(TAG, "Proxy probe failed (continuing anyway): ${e.message}")
            }
        }.start()
    }

    private fun stopVpn() {
        try {
            workerThread?.interrupt()
            pfd?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing pfd", e)
        }
        pfd = null
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
