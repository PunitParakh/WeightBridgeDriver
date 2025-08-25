package com.punit.weightdriver.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.punit.weightdriver.R
import com.punit.weightdriver.core.DeviceKey
import com.punit.weightdriver.data.DeviceProfile

class DevicesAdapter(private val listener: OnDeviceClick)
    : ListAdapter<DeviceProfile, DevicesAdapter.VH>(DIFF) {

    private var connected: Set<DeviceKey> = emptySet()

    interface OnDeviceClick {
        fun onDeviceClick(profile: DeviceProfile)
    }

    fun submit(profiles: List<DeviceProfile>, connectedSet: Set<DeviceKey>) {
        this.connected = connectedSet
        submitList(profiles)
    }

    fun refreshConnected(connectedSet: Set<DeviceKey>) {
        this.connected = connectedSet
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item, connected)
        holder.itemView.setOnClickListener { listener.onDeviceClick(item) }
    }

    class VH(v: View): RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.txtTitle)
        private val subtitle: TextView = v.findViewById(R.id.txtSubtitle)
        private val badge: TextView = v.findViewById(R.id.txtBadge)

        fun bind(p: DeviceProfile, connected: Set<DeviceKey>) {
            val name = p.displayName ?: "VID:${p.vid} PID:${p.pid}" + (p.serial?.let { " ($it)" } ?: "")
            title.text = name
            subtitle.text = "Baud ${p.baudRate}, ${p.dataBits}N${p.stopBits}, parity ${p.parity}, port ${p.portIndex}"

            val isConnected = connected.any { it.vid == p.vid && it.pid == p.pid && (it.serial == p.serial) }
            badge.text = if (isConnected) "Connected" else "Disconnected"
            badge.setBackgroundResource(if (isConnected) R.drawable.badge_connected else R.drawable.badge_disconnected)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DeviceProfile>() {
            override fun areItemsTheSame(oldItem: DeviceProfile, newItem: DeviceProfile) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: DeviceProfile, newItem: DeviceProfile) = oldItem == newItem
        }
    }
}
