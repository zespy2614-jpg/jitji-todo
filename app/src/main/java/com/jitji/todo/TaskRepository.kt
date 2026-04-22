package com.jitji.todo

import android.content.Context
import androidx.lifecycle.LiveData

class TaskRepository(context: Context) {
    private val dao = TaskDatabase.get(context).taskDao()

    fun observeAll(): LiveData<List<Task>> = dao.observeAll()
    fun observeDeleted(): LiveData<List<Task>> = dao.observeDeleted()

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
}
