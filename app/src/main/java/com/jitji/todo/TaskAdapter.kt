package com.jitji.todo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jitji.todo.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onToggle: (Task) -> Unit,
    private val onClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val items: MutableList<Task> = mutableListOf()
    private val formatter = SimpleDateFormat("MM/dd(E) HH:mm", Locale.KOREAN)

    val currentList: List<Task> get() = items.toList()

    fun submitList(list: List<Task>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTaskBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.textTitle.text = task.title

            if (task.memo.isNotBlank()) {
                binding.textMemo.visibility = View.VISIBLE
                binding.textMemo.text = task.memo
            } else {
                binding.textMemo.visibility = View.GONE
            }

            val due = task.dueAt
            if (due != null) {
                binding.textDue.visibility = View.VISIBLE
                binding.textDue.text = "⏰ " + formatter.format(Date(due))
                val overdue = due < System.currentTimeMillis()
                binding.textDue.setTextColor(
                    binding.root.context.getColor(
                        if (overdue) R.color.red else R.color.muted
                    )
                )
            } else {
                binding.textDue.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(task) }
            binding.root.setOnLongClickListener {
                // 롱프레스로 드래그 시작 - ItemTouchHelper가 처리
                false
            }
        }
    }
}
