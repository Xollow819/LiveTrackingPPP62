package com.ppp62.livetracking.data

import com.ppp62.livetracking.data.remote.CheckpointRow
import com.ppp62.livetracking.ui.toEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CheckpointContractTest {
    @Test fun preservesServerOrderingInstructionsAndRequirements() {
        val row=Json.decodeFromString<CheckpointRow>("""{"id":"a","session_id":"session","name":"Dock","lat":-6.2,"lng":106.8,"radius_m":75.0,"order_index":4,"instructions":"Check oxygen","requires_photo":false,"requires_temperature":true,"requires_weight":false}""")
        val entity=row.toEntity(0)
        assertEquals(4,entity.orderIndex)
        assertEquals("Check oxygen",entity.instructions)
        assertFalse(entity.requiresPhoto);assertFalse(entity.requiresWeight);assertTrue(entity.requiresTemperature)
        assertEquals("session",entity.sessionId)
    }
}
