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
    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1") suspend fun sessionById(id: String): SessionEntity?
    @Query("SELECT COUNT(*) FROM sessions") suspend fun sessionCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(value: SessionEntity)

    @Query("SELECT * FROM checkpoints WHERE sessionId = :sessionId ORDER BY orderIndex") fun observeCheckpoints(sessionId: String): Flow<List<CheckpointEntity>>
    @Query("SELECT * FROM checkpoints WHERE id = :id LIMIT 1") suspend fun checkpoint(id: String): CheckpointEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckpoints(values: List<CheckpointEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckpoint(value: CheckpointEntity)
    @Query("DELETE FROM checkpoints WHERE sessionId = :sessionId") suspend fun clearCheckpoints(sessionId: String)
    @Query("SELECT * FROM checkpoints WHERE sessionId = :sessionId ORDER BY orderIndex") suspend fun checkpointsForSession(sessionId: String): List<CheckpointEntity>
    @Transaction suspend fun replaceCheckpoints(sessionId: String, values: List<CheckpointEntity>) {
        if (checkpointsForSession(sessionId) == values.sortedBy { it.orderIndex }) return
        clearCheckpoints(sessionId); upsertCheckpoints(values)
    }
    @Delete suspend fun deleteCheckpoint(value: CheckpointEntity)

    @Query("SELECT * FROM check_ins WHERE sessionId = :sessionId ORDER BY createdAt DESC") fun observeCheckIns(sessionId: String): Flow<List<CheckInEntity>>
    @Query("SELECT * FROM check_ins WHERE syncState IN ('PENDING','FLAGGED') ORDER BY createdAt") suspend fun pendingCheckIns(): List<CheckInEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckIn(value: CheckInEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCheckIn(value: CheckInEntity): Long
    @Query("SELECT * FROM check_ins WHERE id=:id LIMIT 1") suspend fun checkInById(id: String): CheckInEntity?
    @Query("UPDATE check_ins SET syncState='FAILED',uploadError=:error WHERE id=:id") suspend fun failCheckIn(id:String,error:String)
    @Query("UPDATE check_ins SET syncState='PENDING',uploadError=NULL WHERE syncState='FAILED' AND userId=:userId") suspend fun retryFailed(userId:String)
    @Query("UPDATE check_ins SET syncState = :state WHERE id = :id") suspend fun setCheckInSyncState(id: String, state: SyncState)

    @Query("SELECT * FROM locations WHERE sessionId=:sessionId AND participantId=:userId LIMIT 1") suspend fun ownLocation(sessionId:String,userId:String):LocationEntity?
    @Query("SELECT * FROM position_outbox") suspend fun pendingPositions(): List<PositionOutboxEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun queuePosition(row:PositionOutboxEntity)
    @Query("DELETE FROM position_outbox WHERE sessionId=:sessionId AND userId=:userId AND eventAt=:eventAt") suspend fun acknowledgePosition(sessionId:String,userId:String,eventAt:Long)

    @Query("SELECT * FROM locations WHERE sessionId = :sessionId ORDER BY recordedAt DESC") fun observeLocations(sessionId: String): Flow<List<LocationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLocation(value: LocationEntity)
    @Query("UPDATE locations SET trackingState = :state WHERE participantId = :participantId AND sessionId = :sessionId") suspend fun setTrackingState(participantId: String, state: TrackingState, sessionId: String)
}

@Database(entities = [SessionEntity::class, CheckpointEntity::class, CheckInEntity::class, LocationEntity::class, PositionOutboxEntity::class], version = 4, exportSchema = true)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): PPPDao }
