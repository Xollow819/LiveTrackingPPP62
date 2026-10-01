package com.ppp62.livetracking

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ppp62.livetracking.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun migratesInstalledDatabaseWithoutLosingEvidence() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="migration-check.db"
        context.deleteDatabase(name)
        val schema=instrumentation.context.assets.open("com.ppp62.livetracking.data.AppDatabase/1.json").bufferedReader().use{JSONObject(it.readText()).getJSONObject("database")}
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use{db->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()){val row=entities.getJSONObject(i);db.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",row.getString("tableName")))}
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42,?)",arrayOf(schema.getString("identityHash")))
            db.execSQL("INSERT INTO check_ins VALUES ('legacy-record','cp','legacy','Ayu','Team A',25,4,'GOOD','',NULL,NULL,NULL,NULL,100,'FLAGGED','GPS unavailable')")
            db.version=1
        }
        val migrated=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(DatabaseMigrations.FROM_1_TO_2).build()
        try {
            val records=runBlocking{migrated.dao().pendingCheckIns()}
            assertEquals(1,records.size);assertEquals("legacy-record",records[0].id)
            assertEquals("",records[0].userId);assertEquals("GPS unavailable",records[0].exceptionReason)
            assertNull(records[0].uploadError)
        } finally {migrated.close();context.deleteDatabase(name)}
    }
}
