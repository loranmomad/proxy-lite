package com.superproxy.clone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ProxyManager {

    private const val PREFS_NAME = "super_proxy_prefs"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE_PROFILE = "active_profile"
    private const val KEY_VPN_ACTIVE = "vpn_active"

    private val gson = Gson()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfiles(ctx: Context): MutableList<ProxyProfile> {
        val json = prefs(ctx).getString(KEY_PROFILES, null) ?: return mutableListOf()
        val type = object : TypeToken<List<ProxyProfile>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun saveProfiles(ctx: Context, list: List<ProxyProfile>) {
        prefs(ctx).edit().putString(KEY_PROFILES, gson.toJson(list)).apply()
    }

    fun addProfile(ctx: Context, profile: ProxyProfile) {
        val list = loadProfiles(ctx)
        list.add(profile)
        saveProfiles(ctx, list)
    }

    fun removeProfile(ctx: Context, id: String) {
        val list = loadProfiles(ctx)
        list.removeAll { it.id == id }
        saveProfiles(ctx, list)
        if (getActiveProfileId(ctx) == id) setActiveProfileId(ctx, null)
    }

    fun getActiveProfileId(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACTIVE_PROFILE, null)

    fun setActiveProfileId(ctx: Context, id: String?) {
        prefs(ctx).edit().putString(KEY_ACTIVE_PROFILE, id).apply()
    }

    fun getActiveProfile(ctx: Context): ProxyProfile? {
        val id = getActiveProfileId(ctx) ?: return null
        return loadProfiles(ctx).firstOrNull { it.id == id }
    }

    fun isVpnActive(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_VPN_ACTIVE, false)

    fun setVpnActive(ctx: Context, active: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_VPN_ACTIVE, active).apply()
    }

    fun genId(): String =
        java.util.UUID.randomUUID().toString()
}
