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

            // Configure the VPN interface
            val builder = Builder()
                .setSession("SuperProxyLite")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(false) // Non-blocking to prevent ANRs

            pfd = builder.establish()
            Log.i(TAG, "VPN established; pfd=$pfd; proxy=${profile.host}:${profile.port} (${profile.type})")

            // Simulated proxy routing loop
            // NOTE: To actually route traffic, you must plug a tun2socks engine here 
            // to read raw IP packets from `pfd` and forward them via the proxy.
            workerThread = Thread {
                try {
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
        Thread {
            try {
                val socket = Socket()
                
                // CRITICAL: Protect the socket so it doesn't route through our own VPN
                // This prevents an infinite loop and allows the proxy connection to work.
                protect(socket)

                val proxy = if (profile.type == "SOCKS5")
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress(profile.host, profile.port))
                else
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(profile.host, profile.port))
                    
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
