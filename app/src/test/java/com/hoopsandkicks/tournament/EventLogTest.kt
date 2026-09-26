package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {

    // ---------------------------------------------------------------- computeDisplaySeconds

    @Test fun pausedClockShowsStoredValue() {
        assertEquals(300, computeDisplaySeconds(300, changedAtEpochMs = 1_000L, running = false, nowEpochMs = 999_999L))
    }

    @Test fun runningClockCountsDownWholeSeconds() {
        // 42.9 s elapsed -> 42 whole seconds.
        assertEquals(258, computeDisplaySeconds(300, changedAtEpochMs = 10_000L, running = true, nowEpochMs = 52_900L))
        assertEquals(300, computeDisplaySeconds(300, changedAtEpochMs = 10_000L, running = true, nowEpochMs = 10_000L))
    }

    @Test fun elapsedBeyondRemainingClampsToZero() {
        assertEquals(0, computeDisplaySeconds(30, changedAtEpochMs = 0L, running = true, nowEpochMs = 3_600_000L))
    }

    @Test fun viewerClockBehindHostNeverCountsUp() {
        assertEquals(120, computeDisplaySeconds(120, changedAtEpochMs = 50_000L, running = true, nowEpochMs = 45_000L))
    }

    // ---------------------------------------------------------------- full local match

    private val format = GameFormat(type = FormatType.HALVES, periodMin = 10, breakMin = 2)

    private fun baseMatch() = Match(
        id = "m1", number = 1, stageIndex = 0, stage = "League", stageType = StageType.LEAGUE,
        teamAId = "A", teamBId = "B", lineupA = listOf("a1", "a2"), lineupB = listOf("b1")
    )

    /**
     * Mirrors the order in which the live screen appends events (MatchReadyScreen start, then
     * LiveViewModel actions), using the same MatchLog.append helper they use.
     */
    private fun playMatch(): Match {
        var t = 1_000L
        fun next(): Long { t += 1_000L; return t }
        var m = baseMatch().copy(status = MatchStatus.LIVE, remainingSec = 600)
        m = MatchLog.append(m, MatchEventType.MATCH_START, next(), clockSec = 600, clockRunning = false)
        m = MatchLog.append(m, MatchEventType.RESUME, next(), clockSec = 600, clockRunning = true)
        m = MatchLog.append(m, MatchEventType.SCORE, next(), teamId = "A", playerId = "a1", points = 2, note = "1H 09:58", clockSec = 598, clockRunning = true)
        m = MatchLog.append(m, MatchEventType.SCORE, next(), teamId = "B", playerId = "b1", points = 3, note = "1H 09:50", clockSec = 590, clockRunning = true)
        val wrong = MatchLog.append(m, MatchEventType.SCORE, next(), teamId = "A", playerId = "a2", points = 1, note = "1H 09:40", clockSec = 580, clockRunning = true)
        m = wrong
        m = MatchLog.append(m, MatchEventType.VOID, next(), teamId = "A", playerId = "a2", points = 1, voidsSeq = wrong.log.last().seq)
        m = MatchLog.append(m, MatchEventType.PAUSE, next(), clockSec = 575, clockRunning = false)
        m = MatchLog.append(m, MatchEventType.RESUME, next(), clockSec = 575, clockRunning = true)
        m = MatchLog.append(m, MatchEventType.SUB, next(), teamId = "A", outPlayerId = "a2", inPlayerId = "a3")
        m = MatchLog.append(m, MatchEventType.BREAK_START, next(), clockSec = 120, clockRunning = true)
        m = MatchLog.append(m, MatchEventType.BREAK_END, next())
        m = MatchLog.append(m, MatchEventType.PERIOD_START, next(), clockSec = 600, clockRunning = true)
        m = MatchLog.append(m, MatchEventType.SCORE, next(), teamId = "A", playerId = "a3", points = 3, note = "2H 07:12", clockSec = 432, clockRunning = true)
        m = MatchLog.append(m, MatchEventType.MATCH_END, next(), teamId = "A", note = "", clockRunning = false)
        return m
    }

    @Test fun logIsOrderedWithStrictlyIncreasingSeqAndUniqueIds() {
        val log = playMatch().log
        assertEquals(
            listOf(
                MatchEventType.MATCH_START, MatchEventType.RESUME, MatchEventType.SCORE, MatchEventType.SCORE,
                MatchEventType.SCORE, MatchEventType.VOID, MatchEventType.PAUSE,
                MatchEventType.RESUME, MatchEventType.SUB, MatchEventType.BREAK_START, MatchEventType.BREAK_END,
                MatchEventType.PERIOD_START, MatchEventType.SCORE, MatchEventType.MATCH_END
            ),
            log.map { it.type }
        )
        assertEquals(MatchEventType.MATCH_START, log.first().type)
        assertEquals(MatchEventType.MATCH_END, log.last().type)
        assertEquals(1, log.count { it.type == MatchEventType.MATCH_START })
        log.zipWithNext().forEach { (a, b) -> assertTrue("seq must increase: ${a.seq} -> ${b.seq}", b.seq > a.seq) }
        log.zipWithNext().forEach { (a, b) -> assertTrue(b.epochMs >= a.epochMs) }
        assertEquals(log.size, log.map { it.id }.toSet().size)
    }

    @Test fun voidReferencesTheScoreItCancelsAndNothingIsDeleted() {
        val log = playMatch().log
        val void = log.single { it.type == MatchEventType.VOID }
        val target = log.firstOrNull { it.seq == void.voidsSeq }
        assertNotNull(target)
        assertEquals(MatchEventType.SCORE, target!!.type)
        assertEquals("a2", target.playerId)
        assertEquals(4, log.count { it.type == MatchEventType.SCORE }) // voided score is still in the log
    }

    @Test fun replayRebuildsFinalState() {
        val played = playMatch()
        val r = MatchLog.replay(baseMatch(), played.log, format)
        assertEquals(MatchStatus.FINISHED, r.status)
        assertEquals(5, r.scoreA) // 2 + 3 (the voided 1 is gone)
        assertEquals(3, r.scoreB)
        assertEquals("A", r.winnerId)
        assertEquals(2, r.period)
        assertEquals(listOf("a1", "a3"), r.lineupA)
        assertEquals(3, r.events.size)
        assertFalse(r.clockRunning)
    }

    @Test fun replayMidMatchGivesRunningClock() {
        val played = playMatch()
        val upToSecondHalf = played.log.takeWhile { it.type != MatchEventType.SCORE || it.note?.startsWith("2H") != true }
        val r = MatchLog.replay(baseMatch(), upToSecondHalf, format)
        assertEquals(MatchStatus.LIVE, r.status)
        assertTrue(r.clockRunning)
        assertEquals(600, r.remainingSec)
        val periodStart = upToSecondHalf.last { it.type == MatchEventType.PERIOD_START }
        assertEquals(periodStart.epochMs, r.clockEpochMs)
        assertEquals(570, computeDisplaySeconds(r.remainingSec, r.clockEpochMs, r.clockRunning, r.clockEpochMs + 30_000L))
    }

    @Test fun applyRemoteIgnoresMatchesWithoutStart() {
        val t = Tournament(id = "t", name = "T", matches = listOf(baseMatch().copy(scoreA = 7)), format = format)
        val same = MatchLog.applyRemote(t, emptyMap())
        assertEquals(7, same.matches.first().scoreA)
        val rebuilt = MatchLog.applyRemote(t, mapOf("m1" to playMatch().log))
        assertEquals(5, rebuilt.matches.first().scoreA)
    }
}
