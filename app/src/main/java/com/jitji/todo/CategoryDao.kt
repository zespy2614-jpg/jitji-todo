package com.jitji.todo

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): LiveData<List<Category>>

    @Insert
    suspend fun insert(c: Category): Long

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Long

    @Update
    suspend fun update(c: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :id")
    suspend fun unlinkTasks(id: Long)
}
