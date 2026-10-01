package com.ppp62.livetracking.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE check_ins_new (id TEXT NOT NULL PRIMARY KEY, checkpointId TEXT NOT NULL, sessionId TEXT NOT NULL, studentName TEXT NOT NULL, team TEXT NOT NULL, temperatureC REAL, weightKg REAL, condition TEXT NOT NULL, notes TEXT NOT NULL, photoUri TEXT, latitude REAL, longitude REAL, distanceMeters REAL, createdAt INTEGER NOT NULL, syncState TEXT NOT NULL, exceptionReason TEXT, userId TEXT NOT NULL, uploadError TEXT)")
            db.execSQL("INSERT INTO check_ins_new SELECT *, '', NULL FROM check_ins")
            db.execSQL("DROP TABLE check_ins")
            db.execSQL("ALTER TABLE check_ins_new RENAME TO check_ins")
            db.execSQL("CREATE TABLE position_outbox (sessionId TEXT NOT NULL, userId TEXT NOT NULL, displayName TEXT NOT NULL, team TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL NOT NULL, recordedAt INTEGER NOT NULL, trackingState TEXT NOT NULL, eventAt INTEGER NOT NULL, PRIMARY KEY(sessionId,userId))")
            db.execSQL("CREATE TABLE locations_new (participantId TEXT NOT NULL, sessionId TEXT NOT NULL, participantName TEXT NOT NULL, team TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, speedMps REAL NOT NULL, heading REAL NOT NULL, recordedAt INTEGER NOT NULL, trackingState TEXT NOT NULL, batteryPercent INTEGER NOT NULL, PRIMARY KEY(sessionId, participantId))")
            db.execSQL("INSERT INTO locations_new SELECT * FROM locations")
            db.execSQL("DROP TABLE locations")
            db.execSQL("ALTER TABLE locations_new RENAME TO locations")
        }
    }
}
