package com.jitji.todo

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY isDone ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, createdAt ASC")
    fun observeAll(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND categoryId = :categoryId ORDER BY isDone ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, createdAt ASC")
    fun observeByCategory(categoryId: Long): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE dueAt IS NOT NULL AND isDone = 0 AND deletedAt IS NULL")
    suspend fun allPending(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE tasks SET deletedAt = :now WHERE isDone = 1 AND deletedAt IS NULL")
    suspend fun softDeleteCompleted(now: Long)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllDeleted()
}
