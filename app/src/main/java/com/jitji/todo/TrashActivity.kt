package com.jitji.todo

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.jitji.todo.databinding.ActivityTrashBinding

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = TrashAdapter(
            onRestore = { viewModel.restore(it) },
            onDeleteForever = { confirmDelete(it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(this, R.drawable.list_divider)?.let { divider.setDrawable(it) }
        binding.recycler.addItemDecoration(divider)

        viewModel.deletedTasks.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // 들어올 때마다 오래된 항목 정리
        viewModel.cleanupOldDeleted()
    }

    private fun confirmDelete(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage("'${task.title}'을(를) 영구 삭제할까요?\n이 작업은 취소할 수 없어요.")
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteForever(task) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_trash, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_empty_trash -> { confirmEmpty(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmEmpty() {
        AlertDialog.Builder(this)
            .setTitle(R.string.empty_trash)
            .setMessage("휴지통의 모든 항목을 영구 삭제할까요?")
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.purgeAllDeleted() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
