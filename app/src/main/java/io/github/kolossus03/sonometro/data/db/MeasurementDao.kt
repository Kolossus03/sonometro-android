package io.github.kolossus03.sonometro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: MeasurementEntity): Long

    @Insert
    suspend fun insertAll(measurements: List<MeasurementEntity>)

    /**
     * No hay ninguna consulta que haga AVG(leqDbfs) a propósito. La media aritmética
     * de decibelios no significa nada: hay que promediar la energía. Las agregaciones
     * se hacen en Kotlin con [io.github.kolossus03.sonometro.core.LeqAccumulator].
     */
    @Query("SELECT * FROM measurements WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    suspend fun getBetween(fromMs: Long, toMs: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurements ORDER BY timestampMs ASC")
    suspend fun getAll(): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurements")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(timestampMs) FROM measurements")
    suspend fun firstTimestamp(): Long?

    @Query("SELECT MAX(timestampMs) FROM measurements")
    suspend fun lastTimestamp(): Long?

    @Query("SELECT DISTINCT label FROM measurements WHERE label != '' ORDER BY label")
    fun observeLabels(): Flow<List<String>>

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()

    @Query("DELETE FROM measurements WHERE timestampMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}
