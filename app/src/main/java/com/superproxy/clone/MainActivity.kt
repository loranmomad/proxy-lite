package com.superproxy.clone

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.superproxy.clone.databinding.ActivityMainBinding
import com.superproxy.clone.databinding.DialogAddProxyBinding

class MainActivity : AppCompatActivity(), ProfileAdapter.OnProfileClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProfileAdapter

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startVpn()
            } else {
                binding.masterSwitch.isChecked = false
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ProfileAdapter(ProxyManager.loadProfiles(this).toMutableList(), this)
        binding.recyclerProfiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerProfiles.adapter = adapter

        // Detach listener before setting state to prevent unwanted triggers
        binding.masterSwitch.setOnCheckedChangeListener(null)
        binding.masterSwitch.isChecked = ProxyManager.isVpnActive(this)
        updateStatusCard()

        binding.masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val profile = ProxyManager.getActiveProfile(this)
                if (profile == null) {
                    Toast.makeText(this, "Select a profile first", Toast.LENGTH_SHORT).show()
                    binding.masterSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                requestVpnPermission()
            } else {
                stopVpn()
            }
        }

        binding.fabAdd.setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        binding.masterSwitch.setOnCheckedChangeListener(null)
        binding.masterSwitch.isChecked = ProxyManager.isVpnActive(this)
        binding.masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val profile = ProxyManager.getActiveProfile(this)
                if (profile == null) {
                    Toast.makeText(this, "Select a profile first", Toast.LENGTH_SHORT).show()
                    binding.masterSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                requestVpnPermission()
            } else {
                stopVpn()
            }
        }
        updateStatusCard()
        adapter.update(ProxyManager.loadProfiles(this))
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        val profile = ProxyManager.getActiveProfile(this) ?: return
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
            putExtra(ProxyVpnService.EXTRA_PROFILE_ID, profile.id)
        }
        startService(intent)
        ProxyManager.setVpnActive(this, true)
        updateStatusCard()
        TileSyncer.requestUpdate(this)
    }

    private fun stopVpn() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        startService(intent)
        ProxyManager.setVpnActive(this, false)
        updateStatusCard()
        TileSyncer.requestUpdate(this)
    }

    private fun updateStatusCard() {
        val active = ProxyManager.isVpnActive(this)
        if (active) {
            val p = ProxyManager.getActiveProfile(this)
            binding.statusText.text = "Connected"
            binding.statusDetail.text = p?.display() ?: "Active"
            binding.statusCard.setCardBackgroundColor(0xFF2E7D32.toInt())
        } else {
            binding.statusText.text = "Disconnected"
            binding.statusDetail.text = "Proxy is off"
            binding.statusCard.setCardBackgroundColor(0xFF424242.toInt())
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddProxyBinding.inflate(LayoutInflater.from(this))
        val types = arrayOf("HTTP", "SOCKS5")
        dialogBinding.spinnerType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        AlertDialog.Builder(this)
            .setTitle("Add Proxy")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name = dialogBinding.editName.text.toString().trim()
                val host = dialogBinding.editHost.text.toString().trim()
                val portStr = dialogBinding.editPort.text.toString().trim()
                val type = dialogBinding.spinnerType.selectedItem.toString()

                if (name.isEmpty() || host.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val port = portStr.toIntOrNull()
                if (port == null || port !in 1..65535) {
                    Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profile = ProxyProfile(ProxyManager.genId(), name, host, port, type)
                ProxyManager.addProfile(this, profile)
                adapter.update(ProxyManager.loadProfiles(this))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivate(profile: ProxyProfile) {
        ProxyManager.setActiveProfileId(this, profile.id)
        adapter.update(ProxyManager.loadProfiles(this))
        Toast.makeText(this, "Selected: ${profile.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDelete(profile: ProxyProfile) {
        ProxyManager.removeProfile(this, profile.id)
        adapter.update(ProxyManager.loadProfiles(this))
    }
}
