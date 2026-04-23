package com.jitji.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jitji.todo.databinding.ActivityAddTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddTaskActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY_ID = "categoryId"
    }

    private lateinit var binding: ActivityAddTaskBinding
    private val viewModel: TaskViewModel by viewModels()
    private var dueAt: Long? = null
    private var categoryId: Long? = null
    private val dueFormatter = SimpleDateFormat("MM/dd(E) HH:mm", Locale.KOREAN)

    private val quickAlarms = listOf(
        "1분 후" to 1L,
        "3분 후" to 3L,
        "5분 후" to 5L,
        "10분 후" to 10L,
        "30분 후" to 30L,
        "1시간 후" to 60L,
        "2시간 후" to 120L,
        "3시간 후" to 180L,
        "4시간 후" to 240L
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L).takeIf { it != -1L }

        binding.editInput.requestFocus()
        showKeyboard()

        binding.editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.buttonAdd.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }

        binding.buttonAdd.setOnClickListener { submit() }
        binding.buttonPickDue.setOnClickListener { pickDateTime() }
        binding.buttonClearDue.setOnClickListener {
            dueAt = null
            refreshDueLabel()
        }
        renderQuickAlarms()
        refreshDueLabel()
    }

    private fun renderQuickAlarms() {
        binding.quickAlarmBar.removeAllViews()
        val density = resources.displayMetrics.density
        quickAlarms.forEach { (label, minutes) ->
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(getColor(R.color.white))
            tv.textSize = 11f
            tv.setBackgroundResource(R.drawable.bg_chip)
            val padH = (14 * density).toInt()
            val padV = (8 * density).toInt()
            tv.setPadding(padH, padV, padH, padV)
            tv.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (8 * density).toInt()
            tv.layoutParams = lp
            tv.setOnClickListener {
                val c = Calendar.getInstance()
                c.add(Calendar.MINUTE, minutes.toInt())
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                dueAt = c.timeInMillis
                refreshDueLabel()
            }
            binding.quickAlarmBar.addView(tv)
        }
    }

    private fun pickDateTime() {
        val cal = Calendar.getInstance()
        dueAt?.let { cal.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val timeCal = Calendar.getInstance()
                dueAt?.let { timeCal.timeInMillis = it }
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val result = Calendar.getInstance()
                        result.set(y, m, d, hour, minute, 0)
                        result.set(Calendar.MILLISECOND, 0)
                        dueAt = result.timeInMillis
                        refreshDueLabel()
                    },
                    timeCal.get(Calendar.HOUR_OF_DAY),
                    timeCal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun refreshDueLabel() {
        val due = dueAt
        if (due == null) {
            binding.textDue.text = getString(R.string.no_reminder)
            binding.buttonClearDue.visibility = View.GONE
        } else {
            binding.textDue.text = "⏰ " + dueFormatter.format(Date(due))
            binding.buttonClearDue.visibility = View.VISIBLE
        }
    }

    private fun submit() {
        val title = binding.editInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) return
        viewModel.save(Task(title = title, dueAt = dueAt, categoryId = categoryId))
        finish()
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.editInput.post {
            imm.showSoftInput(binding.editInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
