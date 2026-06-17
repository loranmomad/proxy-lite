# SuperProxyLite

A lightweight Super Proxy-style VPN client for Android.

## Features
- Master switch to start/stop the proxy VPN
- Add/Edit/Delete proxy profiles (HTTP / SOCKS5)
- Quick Settings Tile for fast toggling
- Foreground service with persistent notification
- Profiles persisted via SharedPreferences + Gson

## Build
Run: gradle assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk

## Notes
This project intentionally ships a simulated routing path inside
ProxyVpnService. To do real per-app packet forwarding through the chosen
upstream proxy you'd typically integrate something like tun2socks/HevSock5Tunnel
into the TUN file descriptor returned by VpnService.Builder.establish().
