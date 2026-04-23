package com.jitji.todo

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.content.ClipData
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.jitji.todo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableShowOnLockscreen()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adapter = TaskAdapter(
            onToggle = { viewModel.toggleDone(it) },
            onClick = { openEdit(it.id) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        attachSwipeToDelete()
        // 리스트 항목 간 연한 회색 구분선
        val divider = androidx.recyclerview.widget.DividerItemDecoration(
            this,
            androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
        )
        ContextCompat.getDrawable(this, R.drawable.list_divider)?.let { divider.setDrawable(it) }
        binding.recycler.addItemDecoration(divider)

        viewModel.tasks.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.categories.observe(this) { cats ->
            renderCategoryBar(cats)
        }
        viewModel.selectedCategory.observe(this) {
            viewModel.categories.value?.let { renderCategoryBar(it) }
        }

        // 30일 지난 휴지통 항목 자동 정리
        viewModel.cleanupOldDeleted()

        requestNotificationPermissionIfNeeded()
        ensureExactAlarmPermission()
        LockscreenService.start(this)
        ServiceWatchdog.scheduleHeartbeat(this)
        promptBatteryOptimizationIfNeeded()
        promptOverlayPermissionIfNeeded()
    }

    private fun promptOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(this)) return
        AlertDialog.Builder(this)
            .setTitle("다른 앱 위에 표시 권한")
            .setMessage(
                "전원 버튼으로 화면을 켤 때 앱이 자동으로 뜨려면 " +
                    "'다른 앱 위에 표시' 권한이 필요해요. 설정에서 허용해주세요."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    private fun promptRevertHomeIfNeeded() {
        val prefs = getSharedPreferences("jitji", MODE_PRIVATE)
        if (prefs.getBoolean("revert_home_prompted", false)) return
        prefs.edit().putBoolean("revert_home_prompted", true).apply()
        AlertDialog.Builder(this)
            .setTitle("홈 앱 되돌리기")
            .setMessage(
                "이전 버전에서 이 앱을 기본 홈 앱으로 설정했었습니다. " +
                    "이제 홈 런처 기능을 제거했으니 원래 쓰던 런처로 되돌려주세요.\n\n" +
                    "'설정 열기'를 누르면 홈 앱 선택 화면이 나옵니다."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    .onFailure {
                        runCatching {
                            val i = Intent(Intent.ACTION_MAIN)
                            i.addCategory(Intent.CATEGORY_HOME)
                            startActivity(Intent.createChooser(i, "홈 앱 선택"))
                        }
                    }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        LockscreenService.start(this)
    }

    private fun enableShowOnLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                km.requestDismissKeyguard(this, null)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> { openAddTask(); true }
            R.id.action_manage_categories -> { showManageCategoriesDialog(); true }
            R.id.action_trash -> { startActivity(Intent(this, TrashActivity::class.java)); true }
            R.id.action_check_update -> { checkUpdate(); true }
            R.id.action_clear_done -> { viewModel.deleteCompleted(); true }
            R.id.action_battery_opt -> { openBatterySettings(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAddTask() {
        val intent = Intent(this, AddTaskActivity::class.java)
        viewModel.currentCategoryId()?.let {
            intent.putExtra(AddTaskActivity.EXTRA_CATEGORY_ID, it)
        }
        startActivity(intent)
    }

    private fun renderCategoryBar(cats: List<Category>) {
        val bar = binding.categoryBar
        bar.removeAllViews()
        bar.addView(makeChip(getString(R.string.category_all), null, null))
        cats.forEach { bar.addView(makeChip(it.name, it.id, it)) }
        bar.addView(makeAddChip())
    }

    private fun makeChip(label: String, id: Long?, cat: Category?): TextView {
        val d = resources.displayMetrics.density
        val tv = TextView(this)
        tv.text = label
        val selected = viewModel.currentCategoryId() == id
        tv.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
        tv.setTextColor(getColor(if (selected) R.color.input_text else R.color.white))
        tv.textSize = 12.5f
        val pH = (14 * d).toInt()
        val pV = (7 * d).toInt()
        tv.setPadding(pH, pV, pH, pV)
        tv.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (6 * d).toInt()
        tv.layoutParams = lp
        tv.setOnClickListener { viewModel.selectCategory(id) }
        if (cat != null) {
            tv.tag = cat.id
            tv.setOnLongClickListener { v ->
                val data = ClipData.newPlainText("catId", cat.id.toString())
                val shadow = View.DragShadowBuilder(v)
                v.startDragAndDrop(data, shadow, cat.id, 0)
                v.alpha = 0.3f
                true
            }
            tv.setOnDragListener { target, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        if (target.tag != null) target.scaleX = 1.1f
                        if (target.tag != null) target.scaleY = 1.1f
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        target.scaleX = 1f; target.scaleY = 1f; true
                    }
                    DragEvent.ACTION_DROP -> {
                        target.scaleX = 1f; target.scaleY = 1f
                        val sourceId = event.clipData?.getItemAt(0)?.text?.toString()?.toLongOrNull()
                        val targetId = target.tag as? Long
                        if (sourceId != null && targetId != null && sourceId != targetId) {
                            reorderByDrag(sourceId, targetId)
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        target.scaleX = 1f; target.scaleY = 1f
                        target.alpha = 1f
                        true
                    }
                    DragEvent.ACTION_DRAG_STARTED -> true
                    else -> true
                }
            }
        }
        return tv
    }

    private fun reorderByDrag(sourceId: Long, targetId: Long) {
        val list = viewModel.categories.value?.toMutableList() ?: return
        val fromIdx = list.indexOfFirst { it.id == sourceId }
        val toIdx = list.indexOfFirst { it.id == targetId }
        if (fromIdx < 0 || toIdx < 0) return
        val item = list.removeAt(fromIdx)
        list.add(toIdx, item)
        viewModel.reorderCategories(list)
    }

    private fun showManageCategoriesDialog() {
        val cats = viewModel.categories.value.orEmpty()
        if (cats.isEmpty()) {
            Toast.makeText(this, "카테고리가 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        val names = cats.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.category_manage)
            .setItems(names) { _, which ->
                showCategoryMenu(cats[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun makeAddChip(): TextView {
        val d = resources.displayMetrics.density
        val tv = TextView(this)
        tv.text = getString(R.string.category_add)
        tv.setBackgroundResource(R.drawable.bg_chip)
        tv.setTextColor(getColor(R.color.white))
        tv.textSize = 12.5f
        val pH = (14 * d).toInt()
        val pV = (7 * d).toInt()
        tv.setPadding(pH, pV, pH, pV)
        tv.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (6 * d).toInt()
        tv.layoutParams = lp
        tv.setOnClickListener { showAddCategoryDialog() }
        return tv
    }

    private fun showAddCategoryDialog() {
        val d = resources.displayMetrics.density
        val input = EditText(this)
        input.hint = getString(R.string.category_name_hint)
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        val container = FrameLayout(this)
        val pad = (20 * d).toInt()
        container.setPadding(pad, (8 * d).toInt(), pad, 0)
        container.addView(input)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.new_category)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.addCategory(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.addCategory(name)
                dialog.dismiss()
                true
            } else false
        }
        dialog.show()
    }

    private fun showCategoryMenu(cat: Category) {
        AlertDialog.Builder(this)
            .setTitle(cat.name)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> showRenameDialog(cat)
                    1 -> viewModel.deleteCategory(cat)
                }
            }
            .show()
    }

    private fun showRenameDialog(cat: Category) {
        val d = resources.displayMetrics.density
        val input = EditText(this)
        input.setText(cat.name)
        input.setSelection(cat.name.length)
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        val container = FrameLayout(this)
        val pad = (20 * d).toInt()
        container.setPadding(pad, (8 * d).toInt(), pad, 0)
        container.addView(input)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.renameCategory(cat, name)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.renameCategory(cat, name)
                dialog.dismiss()
                true
            } else false
        }
        dialog.show()
    }

    private fun promptBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openBatterySettings() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }.onFailure {
            runCatching {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun confirmDelete(task: Task, onCancel: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(task) }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    private fun attachSwipeToDelete() {
        val bg = ColorDrawable(Color.parseColor("#48484A"))
        var dragChanged = false
        val cb = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.moveItem(from, to)
                dragChanged = true
                return true
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                if (dragChanged) {
                    dragChanged = false
                    viewModel.reorderTasks(adapter.currentList)
                }
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val task = adapter.currentList[pos]
                confirmDelete(task) { adapter.notifyItemChanged(pos) }
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val v = vh.itemView
                if (dX > 0) {
                    bg.setBounds(v.left, v.top, v.left + dX.toInt(), v.bottom)
                } else if (dX < 0) {
                    bg.setBounds(v.right + dX.toInt(), v.top, v.right, v.bottom)
                } else {
                    bg.setBounds(0, 0, 0, 0)
                }
                bg.draw(c)
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(cb).attachToRecyclerView(binding.recycler)
    }

    private fun openEdit(taskId: Long) {
        val intent = Intent(this, EditTaskActivity::class.java)
        intent.putExtra(EditTaskActivity.EXTRA_TASK_ID, taskId)
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (am.canScheduleExactAlarms()) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun checkUpdate() {
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.checking_update)
            .setCancelable(false)
            .create()
        progress.show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            progress.dismiss()
            result.onSuccess { info ->
                val current = UpdateChecker.currentVersion()
                if (info.versionCode <= current) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.latest_version),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@onSuccess
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.check_update)
                    .setMessage(
                        getString(R.string.update_available, info.versionCode) +
                            "\n\n" + info.body.take(400)
                    )
                    .setPositiveButton(R.string.download) { _, _ -> beginDownload(info) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }.onFailure { e ->
                Toast.makeText(
                    this@MainActivity,
                    "업데이트 확인 실패: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun beginDownload(info: UpdateInfo) {
        if (!UpdateChecker.canInstallPackages(this)) {
            Toast.makeText(this, getString(R.string.install_permission_needed), Toast.LENGTH_LONG).show()
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            return
        }

        val progress = AlertDialog.Builder(this)
            .setMessage("v${info.versionCode} 다운로드 중…")
            .setCancelable(false)
            .create()
        progress.show()

        UpdateChecker.startDownload(
            context = this,
            info = info,
            onDownloaded = { apk ->
                progress.dismiss()
                UpdateChecker.launchInstaller(this, apk)
            },
            onError = { msg ->
                progress.dismiss()
                Toast.makeText(this, "다운로드 실패: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }
}
