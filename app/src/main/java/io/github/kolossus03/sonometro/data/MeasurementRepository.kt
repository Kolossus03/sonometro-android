package io.github.kolossus03.sonometro.data

import io.github.kolossus03.sonometro.data.db.MeasurementDao
import io.github.kolossus03.sonometro.data.db.MeasurementEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class MeasurementRepository(private val dao: MeasurementDao) {

    suspend fun insert(measurement: MeasurementEntity): Long = dao.insert(measurement)

    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<MeasurementEntity>> =
        dao.observeBetween(fromMs, toMs)

    fun observeDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<MeasurementEntity>> {
        val (from, to) = date.dayBounds(zone)
        return dao.observeBetween(from, to)
    }

    suspend fun getDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<MeasurementEntity> {
        val (from, to) = date.dayBounds(zone)
        return dao.getBetween(from, to)
    }

    /** [days] días hacia atrás incluyendo [endDate]. */
    fun observeRange(
        endDate: LocalDate,
        days: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<MeasurementEntity>> {
        val from = endDate.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return dao.observeBetween(from, to)
    }

    suspend fun getRange(
        endDate: LocalDate,
        days: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MeasurementEntity> {
        val from = endDate.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return dao.getBetween(from, to)
    }

    suspend fun getAll(): List<MeasurementEntity> = dao.getAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeLabels(): Flow<List<String>> = dao.observeLabels()

    suspend fun firstTimestamp(): Long? = dao.firstTimestamp()

    suspend fun lastTimestamp(): Long? = dao.lastTimestamp()

    suspend fun deleteAll() = dao.deleteAll()
}

private fun LocalDate.dayBounds(zone: ZoneId): Pair<Long, Long> {
    val from = atStartOfDay(zone).toInstant().toEpochMilli()
    val to = plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return from to to
}
