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
    private var deleteMode: Boolean = false
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
                val active = !s.isNullOrBlank()
                binding.buttonAdd.isEnabled = active
                binding.iconAdd.alpha = if (active) 1.0f else 0.35f
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
        renderQuickWords()
        refreshDueLabel()
        viewModel.categories.observe(this) { cats -> renderCategoryBar(cats) }
    }

    private fun loadWords(): MutableList<String> {
        val prefs = getSharedPreferences("jitji_words", MODE_PRIVATE)
        val raw = prefs.getString("words", "") ?: ""
        return if (raw.isEmpty()) mutableListOf()
        else raw.split("").filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveWords(words: List<String>) {
        val prefs = getSharedPreferences("jitji_words", MODE_PRIVATE)
        prefs.edit().putString("words", words.joinToString("")).apply()
    }

    private fun makeWordChip(label: String, density: Float): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.typeface = android.graphics.Typeface.SANS_SERIF
        tv.setTextColor(getColor(R.color.white))
        tv.textSize = 12.5f
        tv.includeFontPadding = false
        tv.setBackgroundResource(R.drawable.bg_chip)
        val padH = (16 * density).toInt()
        val padV = (9 * density).toInt()
        tv.setPadding(padH, padV, padH, padV)
        tv.gravity = Gravity.CENTER
        val lp = android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (8 * density).toInt()
        lp.bottomMargin = (8 * density).toInt()
        tv.layoutParams = lp
        return tv
    }

    private fun makeRemovableWordChip(word: String, index: Int, density: Float): android.view.View {
        // 가로 LinearLayout 컨테이너 (배경 chip drawable) — 단어 + (삭제모드일 때만) × 버튼
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setBackgroundResource(R.drawable.bg_chip)
        val padL = (16 * density).toInt()
        val padR = if (deleteMode) (6 * density).toInt() else (16 * density).toInt()
        val padV = (9 * density).toInt()
        container.setPadding(padL, padV, padR, padV)

        val word_tv = TextView(this)
        word_tv.text = word
        word_tv.typeface = android.graphics.Typeface.SANS_SERIF
        word_tv.setTextColor(getColor(R.color.white))
        word_tv.textSize = 12.5f
        word_tv.includeFontPadding = false
        container.addView(word_tv)

        if (deleteMode) {
            val delete_tv = TextView(this)
            delete_tv.text = "×"
            delete_tv.typeface = android.graphics.Typeface.SANS_SERIF
            delete_tv.setTextColor(getColor(R.color.red))
            delete_tv.textSize = 17f
            delete_tv.includeFontPadding = false
            val deleteLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            deleteLp.marginStart = (8 * density).toInt()
            delete_tv.layoutParams = deleteLp
            delete_tv.setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            delete_tv.setOnClickListener {
                val list = loadWords().also { if (it.size > index) it.removeAt(index) }
                saveWords(list); renderQuickWords()
            }
            container.addView(delete_tv)
        }

        val lp = android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (8 * density).toInt()
        lp.bottomMargin = (8 * density).toInt()
        container.layoutParams = lp

        // 단어 부분 탭 → 삭제모드 OFF면 제목에 삽입 / ON이면 무반응
        word_tv.setOnClickListener {
            if (!deleteMode) insertWordIntoTitle(word)
        }

        // 길게 누르기 → 드래그 시작 (위치 변경) — 삭제모드 OFF에서만
        container.tag = index
        container.setOnLongClickListener { v ->
            if (deleteMode) return@setOnLongClickListener false
            val data = android.content.ClipData.newPlainText("word_index", index.toString())
            val shadow = android.view.View.DragShadowBuilder(v)
            v.startDragAndDrop(data, shadow, v, 0)
            true
        }
        container.setOnDragListener(dragListener)
        return container
    }

    private val dragListener = android.view.View.OnDragListener { target, event ->
        when (event.action) {
            android.view.DragEvent.ACTION_DRAG_STARTED -> true
            android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                target.alpha = 0.5f; true
            }
            android.view.DragEvent.ACTION_DRAG_EXITED -> {
                target.alpha = 1.0f; true
            }
            android.view.DragEvent.ACTION_DROP -> {
                target.alpha = 1.0f
                val srcIdx = event.clipData?.getItemAt(0)?.text?.toString()?.toIntOrNull()
                val dstIdx = target.tag as? Int
                if (srcIdx != null && dstIdx != null && srcIdx != dstIdx) {
                    val list = loadWords()
                    if (srcIdx in list.indices && dstIdx in list.indices) {
                        val w = list.removeAt(srcIdx)
                        list.add(dstIdx, w)
                        saveWords(list); renderQuickWords()
                    }
                }
                true
            }
            android.view.DragEvent.ACTION_DRAG_ENDED -> {
                target.alpha = 1.0f; true
            }
            else -> true
        }
    }

    private fun renderQuickWords() {
        binding.quickWordsBar.removeAllViews()
        val density = resources.displayMetrics.density
        val words = loadWords()
        words.forEachIndexed { idx, word ->
            binding.quickWordsBar.addView(makeRemovableWordChip(word, idx, density))
        }
        // 삭제 모드 토글 칩
        if (words.isNotEmpty()) {
            val delToggle = makeWordChip(if (deleteMode) "완료" else "삭제", density)
            delToggle.setTextColor(getColor(if (deleteMode) R.color.text_primary else R.color.red))
            delToggle.setOnClickListener {
                deleteMode = !deleteMode
                renderQuickWords()
            }
            binding.quickWordsBar.addView(delToggle)
        }
        // + 단어 추가 칩 (빠른 알림과 동일한 스타일) — 삭제모드 아닐 때만
        if (!deleteMode) {
            val add = makeWordChip("+ 단어 추가", density)
            add.setOnClickListener { showAddWordDialog() }
            binding.quickWordsBar.addView(add)
        }
    }

    private fun insertWordIntoTitle(word: String) {
        val edit = binding.editInput
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(0)
        edit.text.replace(minOf(start, end), maxOf(start, end), word)
        edit.requestFocus()
        edit.setSelection(minOf(start, end) + word.length)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showAddWordDialog() {
        val input = android.widget.EditText(this)
        input.hint = "단어 입력"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        container.setPadding(pad, pad / 2, pad, 0)
        container.addView(input)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("자주 쓰는 단어 추가")
            .setView(container)
            .setPositiveButton("추가") { _, _ ->
                val w = input.text?.toString()?.trim().orEmpty()
                if (w.isNotEmpty()) {
                    val list = loadWords()
                    if (!list.contains(w)) {
                        list.add(w)
                        saveWords(list)
                        renderQuickWords()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteWord(word: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("단어 삭제")
            .setMessage("'$word' 을(를) 단어 목록에서 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                val list = loadWords().also { it.remove(word) }
                saveWords(list)
                renderQuickWords()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun renderCategoryBar(cats: List<Category>) {
        binding.categoryBar.removeAllViews()
        val density = resources.displayMetrics.density
        binding.categoryBar.addView(makeCategoryChip("전체", null, density))
        cats.forEach { c ->
            binding.categoryBar.addView(makeCategoryChip(c.name, c.id, density))
        }
    }

    private fun makeCategoryChip(label: String, id: Long?, density: Float): TextView {
        val tv = TextView(this)
        tv.text = label
        val selected = categoryId == id
        tv.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
        tv.setTextColor(getColor(if (selected) R.color.input_text else R.color.white))
        tv.textSize = 12.5f
        val padH = (14 * density).toInt()
        val padV = (7 * density).toInt()
        tv.setPadding(padH, padV, padH, padV)
        tv.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (6 * density).toInt()
        tv.layoutParams = lp
        tv.setOnClickListener {
            categoryId = id
            viewModel.categories.value?.let { renderCategoryBar(it) }
        }
        return tv
    }

    private fun renderQuickAlarms() {
        binding.quickAlarmBar.removeAllViews()
        val density = resources.displayMetrics.density
        quickAlarms.forEach { (label, minutes) ->
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(getColor(R.color.white))
            tv.textSize = 12.5f
            tv.setBackgroundResource(R.drawable.bg_chip)
            val padH = (16 * density).toInt()
            val padV = (9 * density).toInt()
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
