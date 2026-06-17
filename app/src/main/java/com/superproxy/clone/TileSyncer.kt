package com.superproxy.clone

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

object TileSyncer {
    @RequiresApi(Build.VERSION_CODES.N)
    fun requestUpdate(context: Context) {
        try {
            val cn = ComponentName(context, ProxyTileService::class.java)
            TileService.requestListeningState(context, cn)
        } catch (_: Throwable) {
            // ignore — device may not support quick settings tile
        }
    }
}
