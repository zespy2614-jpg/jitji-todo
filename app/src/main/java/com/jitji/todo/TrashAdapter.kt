package com.jitji.todo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jitji.todo.databinding.ItemTrashBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class TrashAdapter(
    private val onRestore: (Task) -> Unit,
    private val onDeleteForever: (Task) -> Unit
) : ListAdapter<Task, TrashAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREAN)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTrashBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemTrashBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.textTitle.text = task.title
            val deletedAt = task.deletedAt ?: 0L
            val daysLeft = run {
                val cutoff = deletedAt + 30L * 24 * 60 * 60 * 1000
                val diff = cutoff - System.currentTimeMillis()
                max(0, (diff / (24L * 60 * 60 * 1000)).toInt())
            }
            binding.textDeleted.text = "삭제 ${fmt.format(Date(deletedAt))} · ${daysLeft}일 후 영구 삭제"
            binding.buttonRestore.setOnClickListener { onRestore(task) }
            binding.buttonDeleteForever.setOnClickListener { onDeleteForever(task) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(old: Task, new: Task) = old.id == new.id
            override fun areContentsTheSame(old: Task, new: Task) = old == new
        }
    }
}
