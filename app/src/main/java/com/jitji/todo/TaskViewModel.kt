package com.jitji.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository(app)
    private val prefs = app.getSharedPreferences("jitji_ui", android.content.Context.MODE_PRIVATE)
    private val PREF_SELECTED_CATEGORY = "selected_category_id"

    /** null = 전체 카테고리 */
    private val selectedCategoryId = MutableLiveData<Long?>(
        prefs.getLong(PREF_SELECTED_CATEGORY, -1L).takeIf { it != -1L }
    )
    val selectedCategory: LiveData<Long?> = selectedCategoryId

    val tasks: LiveData<List<Task>> = selectedCategoryId.switchMap { id ->
        if (id == null) repo.observeAll() else repo.observeByCategory(id)
    }
    val deletedTasks: LiveData<List<Task>> = repo.observeDeleted()
    val categories: LiveData<List<Category>> = repo.observeCategories()

    fun currentCategoryId(): Long? = selectedCategoryId.value
    fun selectCategory(id: Long?) {
        if (selectedCategoryId.value != id) {
            selectedCategoryId.value = id
            prefs.edit().putLong(PREF_SELECTED_CATEGORY, id ?: -1L).apply()
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.addCategory(name) }
    }

    fun renameCategory(c: Category, newName: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.renameCategory(c, newName) }
    }

    fun deleteCategory(c: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCategory(c.id)
            if (selectedCategoryId.value == c.id) {
                selectedCategoryId.postValue(null)
                prefs.edit().putLong(PREF_SELECTED_CATEGORY, -1L).apply()
            }
        }
    }

    fun save(task: Task, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = if (task.id == 0L) repo.insert(task) else {
                repo.update(task); task.id
            }
            val saved = task.copy(id = id)
            if (saved.dueAt != null && !saved.isDone) {
                ReminderScheduler.schedule(getApplication(), saved)
            } else {
                ReminderScheduler.cancel(getApplication(), id)
            }
            onSaved(id)
        }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(isDone = !task.isDone)
            repo.update(updated)
            if (updated.isDone) {
                ReminderScheduler.cancel(getApplication(), updated.id)
            } else if (updated.dueAt != null) {
                ReminderScheduler.schedule(getApplication(), updated)
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.softDelete(task)
            ReminderScheduler.cancel(getApplication(), task.id)
        }
    }

    fun restore(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.restore(task)
            if (task.dueAt != null && !task.isDone) {
                ReminderScheduler.schedule(getApplication(), task.copy(deletedAt = null))
            }
        }
    }

    fun deleteForever(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteForever(task) }
    }

    fun deleteCompleted() {
        viewModelScope.launch(Dispatchers.IO) { repo.softDeleteCompleted() }
    }

    fun purgeAllDeleted() {
        viewModelScope.launch(Dispatchers.IO) { repo.purgeAllDeleted() }
    }

    /** 30일 경과한 휴지통 항목 자동 삭제 */
    fun cleanupOldDeleted() {
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            repo.purgeOlderThan(cutoff)
        }
    }
}
