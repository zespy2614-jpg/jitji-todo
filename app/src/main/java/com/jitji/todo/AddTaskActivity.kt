package com.jitji.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jitji.todo.databinding.ActivityAddTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTaskBinding
    private val viewModel: TaskViewModel by viewModels()
    private var dueAt: Long? = null
    private val dueFormatter = SimpleDateFormat("MM/dd(E) HH:mm", Locale.KOREAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        refreshDueLabel()
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
        viewModel.save(Task(title = title, dueAt = dueAt))
        finish()
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.editInput.post {
            imm.showSoftInput(binding.editInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
