package com.kerim.seskayit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAll(): List<Recording>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): Recording?

    @Insert
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Delete
    suspend fun delete(recording: Recording)
}

@Dao
interface AutoContactDao {
    @Query("SELECT * FROM auto_contacts ORDER BY name")
    fun observeAll(): Flow<List<AutoContact>>

    @Query("SELECT * FROM auto_contacts")
    suspend fun getAll(): List<AutoContact>

    @Insert
    suspend fun insert(contact: AutoContact): Long

    @Delete
    suspend fun delete(contact: AutoContact)
}
