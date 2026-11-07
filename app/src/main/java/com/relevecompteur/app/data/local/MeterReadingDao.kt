package com.relevecompteur.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterReadingDao {

    @Query("SELECT * FROM meter_readings ORDER BY epochDay ASC, id ASC")
    fun observeAllReadings(): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings ORDER BY epochDay ASC, id ASC")
    suspend fun getAllReadingsOnce(): List<MeterReadingEntity>

    @Query("SELECT COUNT(*) FROM meter_readings")
    suspend fun countReadings(): Int

    @Query("SELECT COUNT(*) FROM meter_readings WHERE meterKey = :meterKey")
    suspend fun countReadingsForMeter(meterKey: String): Int

    @Query("SELECT * FROM meter_readings WHERE isSyncedToSheet = 0 ORDER BY epochDay ASC, id ASC")
    suspend fun getPendingSyncReadings(): List<MeterReadingEntity>

    @Query("SELECT COUNT(*) FROM meter_readings WHERE isSyncedToSheet = 0")
    fun observePendingSyncCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(entity: MeterReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReadings(entities: List<MeterReadingEntity>)

    @Query("UPDATE meter_readings SET isSyncedToSheet = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM meter_readings WHERE isSyncedToSheet = 1")
    suspend fun deleteAllSyncedReadings()
}
