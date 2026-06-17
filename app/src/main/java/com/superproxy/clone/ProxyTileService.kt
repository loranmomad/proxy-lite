package com.superproxy.clone

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class ProxyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val active = ProxyManager.isVpnActive(this)
        if (active) {
            // Stop
            val intent = Intent(this, ProxyVpnService::class.java).apply {
                action = ProxyVpnService.ACTION_STOP
            }
            startService(intent)
            ProxyManager.setVpnActive(this, false)
            refreshTile()
        } else {
            val profile = ProxyManager.getActiveProfile(this)
            if (profile == null) {
                // No active profile; open the app so user can choose one.
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityAndCollapse(launch)
                }
                refreshTile()
                return
            }
            // Need VPN permission
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                // Cannot show UI from tile directly without activity; open app.
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityAndCollapse(launch)
                }
            } else {
                val intent = Intent(this, ProxyVpnService::class.java).apply {
                    action = ProxyVpnService.ACTION_START
                    putExtra(ProxyVpnService.EXTRA_PROFILE_ID, profile.id)
                }
                startService(intent)
                ProxyManager.setVpnActive(this, true)
                refreshTile()
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val active = ProxyManager.isVpnActive(this)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SuperProxy"
        tile.contentDescription = if (active) "Proxy connected" else "Proxy disconnected"
        tile.updateTile()
    }
}
