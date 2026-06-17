package com.superproxy.clone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.superproxy.clone.databinding.ItemProxyBinding

class ProfileAdapter(
    private var items: MutableList<ProxyProfile>,
    private val listener: OnProfileClickListener
) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    interface OnProfileClickListener {
        fun onActivate(profile: ProxyProfile)
        fun onDelete(profile: ProxyProfile)
    }

    fun update(list: List<ProxyProfile>) {
        items = list.toMutableList()
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemProxyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProxyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val activeId = ProxyManager.getActiveProfileId(holder.itemView.context)
        holder.binding.textName.text = p.name
        holder.binding.textHost.text = "${p.type} - ${p.host}:${p.port}"
        holder.binding.radioButton.isChecked = (p.id == activeId)

        holder.binding.radioButton.setOnClickListener { listener.onActivate(p) }
        holder.binding.root.setOnClickListener { listener.onActivate(p) }
        holder.binding.btnDelete.setOnClickListener { listener.onDelete(p) }
    }

    override fun getItemCount(): Int = items.size
}
