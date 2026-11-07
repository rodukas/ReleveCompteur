package com.relevecompteur.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import java.time.LocalDate

@Entity(
    tableName = "meter_readings",
    indices = [
        Index(value = ["meterKey", "epochDay"]),
        Index(value = ["isSyncedToSheet"])
    ]
)
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateString: String,
    val epochDay: Long,
    val meterKey: String,
    val rawValue: Double,
    val normalizedValue: Double,
    val comment: String,
    val isSyncedToSheet: Boolean = true
) {
    fun toDomainOrNull(): MeterReading? {
        val type = MeterType.fromSheetLabel(meterKey) ?: return null
        return MeterReading(
            id = id,
            dateString = dateString,
            date = LocalDate.ofEpochDay(epochDay),
            meterType = type,
            rawValue = rawValue,
            normalizedValue = normalizedValue,
            comment = comment,
            isSyncedToSheet = isSyncedToSheet
        )
    }

    companion object {
        fun fromDomain(reading: MeterReading): MeterReadingEntity = MeterReadingEntity(
            id = reading.id,
            dateString = reading.dateString,
            epochDay = reading.epochDay,
            meterKey = reading.meterType.keyId,
            rawValue = reading.rawValue,
            normalizedValue = reading.normalizedValue,
            comment = reading.comment,
            isSyncedToSheet = reading.isSyncedToSheet
        )
    }
}

