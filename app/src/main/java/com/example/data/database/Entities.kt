package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val filename: String,
    val filepath: String,
    val mimeType: String,
    val category: String, // "Video", "Audio", "Images", "Other"
    val status: String,   // "QUEUED", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speed: Long = 0, // bytes per second
    val timestamp: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false, // for Hidden Vault
    val errorMessage: String? = null,
    val threads: Int = 4,
    val quality: String? = "Auto",
    val integrityStatus: String? = "PENDING", // "OK", "MISSING", "CORRUPTED", "PENDING"
    val connectionHealth: String? = "PENDING", // "EXCELLENT", "GOOD", "POOR", "UNREACHABLE", "PENDING"
    val lastCheckedTime: Long = 0L
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
