package com.jitji.todo

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.jitji.todo.databinding.ActivityCalendarBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter

    private val dayFormatter = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)
    private var selectedDateMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarCalendar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.calendar)

        adapter = TaskAdapter(
            onToggle = { viewModel.toggleDone(it) },
            onClick = { /* no-op in calendar view */ }
        )
        binding.calendarTaskList.layoutManager = LinearLayoutManager(this)
        binding.calendarTaskList.adapter = adapter

        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(this, R.drawable.list_divider)?.let { divider.setDrawable(it) }
        binding.calendarTaskList.addItemDecoration(divider)

        // Default to today
        val todayCal = Calendar.getInstance()
        selectedDateMs = startOfDay(todayCal)
        binding.calendarView.date = todayCal.timeInMillis

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDateMs = startOfDay(cal)
            updateTaskList()
        }

        viewModel.tasks.observe(this) {
            updateTaskList()
        }

        ThemeManager.apply(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(startOfDayMs: Long): Long = startOfDayMs + 24L * 60 * 60 * 1000 - 1

    private fun updateTaskList() {
        val dayStart = selectedDateMs
        val dayEnd = endOfDay(dayStart)

        binding.calendarDateLabel.text = dayFormatter.format(Date(dayStart))

        val allTasks = viewModel.tasks.value ?: emptyList()
        val filtered = allTasks.filter { task ->
            val due = task.dueAt ?: return@filter false
            due in dayStart..dayEnd
        }

        adapter.submitList(filtered)
        binding.calendarEmptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.calendarTaskList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }
}
