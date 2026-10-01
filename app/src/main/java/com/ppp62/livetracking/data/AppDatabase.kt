package com.ppp62.livetracking.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

class DbConverters {
    @TypeConverter fun sessionStatus(value: SessionStatus) = value.name
    @TypeConverter fun sessionStatus(value: String) = SessionStatus.valueOf(value)
    @TypeConverter fun trackingState(value: TrackingState) = value.name
    @TypeConverter fun trackingState(value: String) = TrackingState.valueOf(value)
    @TypeConverter fun syncState(value: SyncState) = value.name
    @TypeConverter fun syncState(value: String) = SyncState.valueOf(value)
    @TypeConverter fun fishCondition(value: FishCondition) = value.name
    @TypeConverter fun fishCondition(value: String) = FishCondition.valueOf(value)
}

@Dao
interface PPPDao {
    @Query("SELECT * FROM sessions ORDER BY startsAt DESC") fun observeSessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE joinCode = :code LIMIT 1") suspend fun sessionByCode(code: String): SessionEntity?
    @Query("SELECT COUNT(*) FROM sessions") suspend fun sessionCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(value: SessionEntity)

    @Query("SELECT * FROM checkpoints WHERE sessionId = :sessionId ORDER BY orderIndex") fun observeCheckpoints(sessionId: String): Flow<List<CheckpointEntity>>
    @Query("SELECT * FROM checkpoints WHERE id = :id LIMIT 1") suspend fun checkpoint(id: String): CheckpointEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckpoints(values: List<CheckpointEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckpoint(value: CheckpointEntity)
    @Delete suspend fun deleteCheckpoint(value: CheckpointEntity)

    @Query("SELECT * FROM check_ins WHERE sessionId = :sessionId ORDER BY createdAt DESC") fun observeCheckIns(sessionId: String): Flow<List<CheckInEntity>>
    @Query("SELECT * FROM check_ins WHERE syncState = 'PENDING' ORDER BY createdAt") suspend fun pendingCheckIns(): List<CheckInEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckIn(value: CheckInEntity)
    @Query("UPDATE check_ins SET syncState = :state WHERE id = :id") suspend fun setCheckInSyncState(id: String, state: SyncState)

    @Query("SELECT * FROM locations WHERE sessionId = :sessionId ORDER BY recordedAt DESC") fun observeLocations(sessionId: String): Flow<List<LocationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLocation(value: LocationEntity)
    @Query("UPDATE locations SET trackingState = :state, recordedAt = :timestamp WHERE participantId = :participantId") suspend fun setTrackingState(participantId: String, state: TrackingState, timestamp: Long)
}

@Database(entities = [SessionEntity::class, CheckpointEntity::class, CheckInEntity::class, LocationEntity::class], version = 1, exportSchema = true)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): PPPDao }
