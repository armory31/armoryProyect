/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherCoreTest {
    @Test
    fun protocol_serialization_roundTrip() {
        val hello =
            ClientHello(
                protocolVersion = TogetherProtocolVersion,
                sessionId = "sid",
                sessionKey = "key",
                clientId = "cid",
                displayName = "Alice",
            )
        val json = TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello)
        val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json)
        assertTrue(decoded is ClientHello)
        assertEquals(hello, decoded)
    }

    @Test
    fun protocol_seekToTrack_roundTrip() {
        val message: TogetherMessage =
            ControlRequest(
                sessionId = "sid",
                participantId = "pid",
                action = ControlAction.SeekToTrack(trackId = "track123", positionMs = 456L),
            )
        val json = TogetherJson.json.encodeToString(TogetherMessage.serializer(), message)
        val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json)
        assertTrue(decoded is ControlRequest)
        val req = decoded as ControlRequest
        assertTrue(req.action is ControlAction.SeekToTrack)
        val action = req.action as ControlAction.SeekToTrack
        assertEquals("track123", action.trackId)
        assertEquals(456L, action.positionMs)
    }
}
