package de.hauptmann.sunshinealarm.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One sensor sample from the wake-up light, kept locally as history */
@Entity(tableName = "sensor_reading", indices = [Index("timestamp")])
data class SensorReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // epoch millis
    val lux: Double,
    val temperatureC: Double,
    val humidityPercent: Double,
    val noiseDb: Int,
)

/** Last known state of an alarm slot, so alarms are visible without the device */
@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey val slot: Int,
    val inUse: Boolean,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val dayMask: Int,
    val powerWake: Boolean,
    val syncedAt: Long,
    // Added in schema v2 (nullable: unknown until the details were read once)
    val sunriseMinutes: Int? = null,
    val sunriseLevel: Int? = null,
    val lightTheme: Int? = null,
    val soundSource: String? = null,
    val soundChannel: Int? = null,
    val volume: Int? = null,
    // Added in schema v3
    val name: String? = null,
    /** Set for naps: when the nap ends (epoch millis). The slot is freed afterwards. */
    val napEndsAt: Long? = null,
)

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(reading: SensorReading)

    @Query("SELECT * FROM sensor_reading WHERE timestamp >= :since ORDER BY timestamp")
    fun since(since: Long): Flow<List<SensorReading>>

    @Query("SELECT * FROM sensor_reading ORDER BY timestamp DESC LIMIT 1")
    fun latest(): Flow<SensorReading?>

    @Query("SELECT * FROM sensor_reading WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun between(from: Long, to: Long): List<SensorReading>

    @Query("SELECT COUNT(*) FROM sensor_reading")
    fun count(): Flow<Int>
}

@Dao
interface AlarmDao {
    @Upsert
    suspend fun upsertAll(alarms: List<AlarmEntity>)

    @Query("SELECT * FROM alarm ORDER BY slot")
    fun all(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm WHERE slot = :slot")
    suspend fun get(slot: Int): AlarmEntity?
}

/*
 * Keeping data across updates:
 * - Bump `version` for every schema change and add an AutoMigration (or a manual
 *   Migration) so existing rows are carried over. The exported schemas in
 *   app/schemas/ are the reference for that.
 * - Never use fallbackToDestructiveMigration(): it silently wipes the history.
 */
@Database(
    entities = [SensorReading::class, AlarmEntity::class, SleepNight::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // alarm: sunrise and sound columns
        AutoMigration(from = 2, to = 3), // alarm: name and nap end
        AutoMigration(from = 3, to = 4), // sleep_night table
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao
    abstract fun alarmDao(): AlarmDao
    abstract fun sleepDao(): SleepDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "sunshine.db")
                .build()
                .also { instance = it }
        }
    }
}
