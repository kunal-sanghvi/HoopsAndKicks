package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.roomShareText
import com.hoopsandkicks.tournament.data.roomShareUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLinkTest {
    @Test fun urlHasTheRoomCodeAsAQueryParameter() {
        assertEquals("https://kunal-sanghvi.github.io/HoopsAndKicks/?room=K7M2QX", roomShareUrl("K7M2QX"))
    }

    @Test fun shareTextHasTheUrlAndKeepsTheCodeAsAFallback() {
        val text = roomShareText("City Cup", "K7M2QX")
        assertTrue(text.contains("https://kunal-sanghvi.github.io/HoopsAndKicks/?room=K7M2QX"))
        assertTrue(text.contains("City Cup"))
        assertTrue(text.endsWith("enter K7M2QX"))
    }
}
