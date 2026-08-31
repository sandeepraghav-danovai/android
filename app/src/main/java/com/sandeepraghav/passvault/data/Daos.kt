package com.sandeepraghav.passvault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface PasswordEntryDao {
    @Query("SELECT * FROM password_entries WHERE categoryId = :categoryId ORDER BY title ASC")
    fun observeByCategory(categoryId: Long): Flow<List<PasswordEntryEntity>>

    @Query("SELECT * FROM password_entries WHERE title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY title ASC")
    fun search(query: String): Flow<List<PasswordEntryEntity>>

    @Query("SELECT * FROM password_entries WHERE id = :id")
    suspend fun getById(id: Long): PasswordEntryEntity?

    @Query("SELECT COUNT(*) FROM password_entries WHERE categoryId = :categoryId")
    fun observeCountForCategory(categoryId: Long): Flow<Int>

    @Query("SELECT * FROM password_entries")
    suspend fun getAll(): List<PasswordEntryEntity>

    @Insert
    suspend fun insert(entry: PasswordEntryEntity): Long

    @Update
    suspend fun update(entry: PasswordEntryEntity)

    @Delete
    suspend fun delete(entry: PasswordEntryEntity)
}
