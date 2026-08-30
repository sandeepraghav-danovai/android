package com.sandeepraghav.passvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorHex: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean
)

@Entity(tableName = "password_entries")
data class PasswordEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val username: String,
    /** Base64 IV:ciphertext, AES-256-GCM under the vault's data encryption key. */
    val encryptedPassword: String,
    val encryptedNotes: String?,
    val url: String?,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
