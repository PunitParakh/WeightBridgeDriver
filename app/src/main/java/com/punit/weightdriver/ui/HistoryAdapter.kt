package com.punit.weightdriver.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.punit.weightdriver.R
import com.punit.weightdriver.data.WeightEntity
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter : ListAdapter<WeightEntity, HistoryAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WeightEntity>() {
            override fun areItemsTheSame(oldItem: WeightEntity, newItem: WeightEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: WeightEntity, newItem: WeightEntity) = oldItem == newItem
        }

        private val sdf = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_weight, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvValue.text = item.value
        holder.tvTime.text = sdf.format(Date(item.timestamp))
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvValue: TextView = view.findViewById(R.id.tvValue)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }
}
