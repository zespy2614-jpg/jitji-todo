package com.jitji.todo

import android.content.Context
import androidx.lifecycle.LiveData

class TaskRepository(context: Context) {
    private val dao = TaskDatabase.get(context).taskDao()
    private val catDao = TaskDatabase.get(context).categoryDao()

    fun observeAll(): LiveData<List<Task>> = dao.observeAll()
    fun observeByCategory(id: Long): LiveData<List<Task>> = dao.observeByCategory(id)
    fun observeDeleted(): LiveData<List<Task>> = dao.observeDeleted()
    fun observeCategories(): LiveData<List<Category>> = catDao.observeAll()

    suspend fun find(id: Long): Task? = dao.findById(id)
    suspend fun allPending(): List<Task> = dao.allPending()
    suspend fun insert(task: Task): Long = dao.insert(task)
    suspend fun update(task: Task) = dao.update(task)
    suspend fun softDelete(task: Task) = dao.softDelete(task.id, System.currentTimeMillis())
    suspend fun restore(task: Task) = dao.restore(task.id)
    suspend fun deleteForever(task: Task) = dao.delete(task)
    suspend fun softDeleteCompleted() = dao.softDeleteCompleted(System.currentTimeMillis())
    suspend fun purgeOlderThan(cutoff: Long) = dao.purgeOlderThan(cutoff)
    suspend fun purgeAllDeleted() = dao.purgeAllDeleted()

    suspend fun addCategory(name: String): Long = catDao.insert(Category(name = name))
    suspend fun renameCategory(c: Category, newName: String) = catDao.update(c.copy(name = newName))
    suspend fun deleteCategory(id: Long) {
        catDao.unlinkTasks(id)
        catDao.delete(id)
    }
}
