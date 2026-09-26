package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScheduleTimesTest {

    private val start = 1_790_000_000_000L
    private val min = 60_000L

    /** 2 × 10 min halves + 5 min break = 25 min estimated; plus a 5 min buffer = 30 min per slot. */
    private val format = GameFormat(type = FormatType.HALVES, periodMin = 10, breakMin = 5)

    private fun match(n: Int, round: Int = 1, stageIndex: Int = 0, bye: Boolean = false) = Match(
        id = "m$n", number = if (bye) 0 else n, stageIndex = stageIndex, stage = "S", stageType = StageType.LEAGUE,
        round = round, teamAId = "a$n", teamBId = if (bye) null else "b$n", bye = bye
    )

    private fun tournament(matches: List<Match>, courts: Int = 1, startAt: Long = start, endAt: Long = 0L) = Tournament(
        id = "x", name = "T", startAt = startAt, endAt = endAt, courts = courts, matchBufferMin = 5,
        format = format, matches = matches, status = TStatus.ACTIVE
    )

    private fun makespan(t: Tournament): Long =
        t.matches.filter { !it.bye }.maxOf { it.scheduledAt!! + format.estimatedDurationMin() * min } - start

    @Test fun estimatedDurationIgnoresTieBreaks() {
        assertEquals(25, format.estimatedDurationMin())
        assertEquals(20, format.copy(type = FormatType.CONTINUOUS, periodMin = 20).estimatedDurationMin())
    }

    @Test fun singleCourtSlotsAreSequentialAndNonOverlapping() {
        // Deliberately shuffled input: slotting follows (stageIndex, round, number), not list order.
        val t = Scheduler.scheduleTimes(tournament(listOf(match(3, round = 2), match(1), match(2))))
        val byNumber = t.matches.sortedBy { it.number }
        byNumber.forEachIndexed { i, m ->
            assertEquals(1, m.court)
            assertEquals(start + i * 30 * min, m.scheduledAt)
        }
        byNumber.zipWithNext().forEach { (a, b) ->
            assertTrue(a.scheduledAt!! + format.estimatedDurationMin() * min <= b.scheduledAt!!)
        }
    }

    @Test fun multipleCourtsPickEarliestFreeCourt() {
        val ms = (1..6).map { match(it) }
        val one = Scheduler.scheduleTimes(tournament(ms, courts = 1))
        val three = Scheduler.scheduleTimes(tournament(ms, courts = 3))
        val slots = three.matches.sortedBy { it.number }.map { it.court to it.scheduledAt }
        assertEquals(
            listOf(
                1 to start, 2 to start, 3 to start,
                1 to start + 30 * min, 2 to start + 30 * min, 3 to start + 30 * min
            ),
            slots
        )
        assertTrue(makespan(three) < makespan(one))
        // No two matches overlap on the same court.
        three.matches.groupBy { it.court }.values.forEach { onCourt ->
            onCourt.sortedBy { it.scheduledAt }.zipWithNext().forEach { (a, b) ->
                assertTrue(a.scheduledAt!! + format.estimatedDurationMin() * min <= b.scheduledAt!!)
            }
        }
    }

    @Test fun finalsUseTheirOwnLength() {
        val f = format.copy(finalsDifferent = true, finalsPeriodMin = 20) // final = 45 min + 5 buffer
        val ms = listOf(match(1).copy(isFinal = true), match(2, round = 2))
        val t = Scheduler.scheduleTimes(tournament(ms).copy(format = f))
        assertEquals(start + 50 * min, t.match("m2")!!.scheduledAt)
    }

    @Test fun byesAreNeverSlotted() {
        val ms = listOf(match(1), match(2, bye = true), match(3))
        val t = Scheduler.scheduleTimes(tournament(ms, courts = 2))
        val bye = t.match("m2")!!
        assertNull(bye.scheduledAt)
        assertNull(bye.court)
        assertTrue(t.matches.filter { !it.bye }.all { it.scheduledAt != null && it.court != null })
    }

    @Test fun unsetStartIsNoOp() {
        val base = tournament(listOf(match(1), match(2)), startAt = 0L)
        val t = Scheduler.scheduleTimes(base)
        assertEquals(base, t)
        assertTrue(t.matches.all { it.scheduledAt == null && it.court == null })
    }

    @Test fun rescheduleIsDeterministic() {
        val t1 = Scheduler.scheduleTimes(tournament((1..5).map { match(it) }, courts = 2))
        assertEquals(t1, Scheduler.scheduleTimes(t1))
    }

    @Test fun overrunZeroWhenScheduleFits() {
        // 4 matches on 1 court: last starts at +90, ends at +115 min.
        val t = Scheduler.scheduleTimes(tournament((1..4).map { match(it) }, endAt = start + 120 * min))
        assertEquals(0L, Scheduler.scheduleOverrunMinutes(t))
        // Unset end time never reports an overrun.
        assertEquals(0L, Scheduler.scheduleOverrunMinutes(t.copy(endAt = 0L)))
    }

    @Test fun overrunReportsMinutesPastEnd() {
        val t = Scheduler.scheduleTimes(tournament((1..4).map { match(it) }, endAt = start + 100 * min))
        assertEquals(15L, Scheduler.scheduleOverrunMinutes(t))
    }

    @Test fun nextStageWaitsForWholePreviousStageOnEveryCourt() {
        // Stage 0: 3 matches on 2 courts -> m1 c1 @0, m2 c2 @0, m3 c1 @30 (ends @60 incl. buffer).
        // Court 2 is free from @30, but stage 1 must wait until the whole of stage 0 is over.
        val ms = listOf(match(1), match(2), match(3), match(4, stageIndex = 1))
        val t = Scheduler.scheduleTimes(tournament(ms, courts = 2))
        assertEquals(start + 30 * min, t.match("m3")!!.scheduledAt)
        val next = t.match("m4")!!
        assertEquals(start + 60 * min, next.scheduledAt)
        assertEquals(1, next.court) // both courts are available at the gate; lowest index wins the tie
    }

    @Test fun roundsInsideAStageAreNotGatedButSwissRoundsAre() {
        // Group/round-robin rounds don't depend on each other: round 2 fills the free court at @30.
        val ms = listOf(match(1), match(2), match(3), match(4, round = 2))
        assertEquals(start + 30 * min, Scheduler.scheduleTimes(tournament(ms, courts = 2)).match("m4")!!.scheduledAt)
        // Swiss (and double elimination) draw each round from the previous one, so each round is a stage.
        val swiss = Scheduler.scheduleTimes(tournament(ms, courts = 2).copy(algorithm = Algorithm.SWISS))
        assertEquals(start + 60 * min, swiss.match("m4")!!.scheduledAt)
    }

    @Test fun knockoutNeverStartsBeforeGroupStageEnds() {
        // 6 teams, 2 groups of 3 -> 6 group matches; 4 courts, so the old greedy slotting would have put the
        // semi-finals on courts 3-4 at @30 while group matches were still running on courts 1-2.
        val teams = (1..6).map { Team("t$it", "Team $it", it) }
        var t = Tournament(
            id = "x", name = "T", teams = teams, algorithm = Algorithm.GROUP_KO, groups = 2, advance = 2,
            status = TStatus.ACTIVE, startAt = start, courts = 4, matchBufferMin = 5, format = format
        )
        t = Scheduler.advance(t.copy(matches = Scheduler.generate(t, Random(1))))
        val rnd = Random(2)
        var guard = 0
        while (t.status == TStatus.ACTIVE && guard++ < 10) {
            t = Scheduler.advance(t.copy(matches = t.matches.map {
                if (it.status == MatchStatus.FINISHED) it
                else it.copy(status = MatchStatus.FINISHED, scoreA = 10, scoreB = 5, winnerId = it.teamAId)
            }), rnd)
        }
        assertEquals(TStatus.COMPLETED, t.status)
        val real = t.realMatches()
        val stages = real.map { it.stageIndex }.distinct().sorted()
        assertTrue(stages.size >= 3) // group stage, semi-finals, final
        for (s in stages.drop(1)) {
            // Stage end = latest start + estimated length + changeover, across all of the earlier stages.
            val earlierEnd = real.filter { it.stageIndex < s }.maxOf { it.scheduledAt!! + 30 * min }
            real.filter { it.stageIndex == s }.forEach { assertTrue(it.scheduledAt!! >= earlierEnd) }
        }
        val groupEnd = real.filter { it.stageType == StageType.GROUP }.maxOf { it.scheduledAt!! + format.estimatedDurationMin() * min }
        real.filter { it.stageType == StageType.KNOCKOUT }.forEach { assertTrue(it.scheduledAt!! >= groupEnd) }
        assertEquals(start + 60 * min, real.filter { it.stageType == StageType.KNOCKOUT }.minOf { it.scheduledAt!! })
    }

    @Test fun overrunAccountsForStageGating() {
        // Same shape as above: with gating the stage-1 match runs @60-@85, so an @80 end no longer fits.
        val ms = listOf(match(1), match(2), match(3), match(4, stageIndex = 1))
        val t = Scheduler.scheduleTimes(tournament(ms, courts = 2, endAt = start + 80 * min))
        assertEquals(5L, Scheduler.scheduleOverrunMinutes(t))
        assertFalse(Scheduler.fits(t))
        assertTrue(Scheduler.fits(Scheduler.scheduleTimes(t.copy(endAt = start + 85 * min))))
    }

    /** 1 court, end @100: m1 @0 (finished), m2 @30 (live), m3 @60, m4 @90 (ends @115, over the end). */
    private fun halfPlayed(): Tournament {
        val t = Scheduler.scheduleTimes(tournament((1..4).map { match(it) }, endAt = start + 100 * min))
        return t.copy(matches = t.matches.map {
            when (it.id) {
                "m1" -> it.copy(status = MatchStatus.FINISHED, scoreA = 10, scoreB = 5, winnerId = it.teamAId)
                "m2" -> it.copy(status = MatchStatus.LIVE)
                else -> it
            }
        })
    }

    @Test fun startedAndFinishedMatchesKeepTheirSlotsWhenACourtIsAdded() {
        val t = halfPlayed()
        assertFalse(Scheduler.fits(t))
        val two = Scheduler.scheduleTimes(t.copy(courts = 2))
        // History doesn't move (re-slotting from scratch would have put m2 on court 2 at @0).
        assertEquals(start, two.match("m1")!!.scheduledAt)
        assertEquals(1, two.match("m1")!!.court)
        assertEquals(start + 30 * min, two.match("m2")!!.scheduledAt)
        assertEquals(1, two.match("m2")!!.court)
        // What's left uses the new court, but not before the live match's start (that time has passed),
        // and court 1 stays busy until the live match's slot ends (@60).
        assertEquals(start + 30 * min, two.match("m3")!!.scheduledAt)
        assertEquals(2, two.match("m3")!!.court)
        assertEquals(start + 60 * min, two.match("m4")!!.scheduledAt)
        assertEquals(1, two.match("m4")!!.court)
        assertTrue(Scheduler.fits(two)) // m4 now ends @85
    }

    @Test fun startedAndFinishedMatchesKeepTheirSlotsOnReloadAndFormatChange() {
        val t = halfPlayed()
        // Re-slotting again (as Repository does on every load) changes nothing.
        assertEquals(t, Scheduler.scheduleTimes(t))
        val two = Scheduler.scheduleTimes(t.copy(courts = 2))
        assertEquals(two, Scheduler.scheduleTimes(two))
        // A longer format re-slots only what hasn't started: 2 × 20 + 5 = 45 min + 5 buffer = 50 min per slot.
        val longer = Scheduler.scheduleTimes(t.copy(format = format.copy(periodMin = 20)))
        assertEquals(start, longer.match("m1")!!.scheduledAt)
        assertEquals(start + 30 * min, longer.match("m2")!!.scheduledAt)
        assertEquals(start + 80 * min, longer.match("m3")!!.scheduledAt) // after m2's (new-length) slot
        assertEquals(start + 130 * min, longer.match("m4")!!.scheduledAt)
    }

    @Test fun nextStageWaitsForStartedMatchesOfThePreviousStage() {
        // Stage 0 on 2 courts: m1 c1 @0 (finished), m2 c2 @0 (live), m3 c1 @30 (not started). Stage 1 still waits
        // for all of stage 0, including the fixed matches, and nothing is planned before the live match's start.
        val ms = listOf(match(1), match(2), match(3), match(4, stageIndex = 1))
        var t = Scheduler.scheduleTimes(tournament(ms, courts = 2))
        t = t.copy(matches = t.matches.map {
            when (it.id) {
                "m1" -> it.copy(status = MatchStatus.FINISHED, winnerId = it.teamAId)
                "m2" -> it.copy(status = MatchStatus.LIVE)
                else -> it
            }
        })
        val three = Scheduler.scheduleTimes(t.copy(courts = 3))
        assertEquals(start to 2, three.match("m2")!!.scheduledAt to three.match("m2")!!.court)
        assertEquals(start to 3, three.match("m3")!!.scheduledAt to three.match("m3")!!.court) // new court, free @0
        assertEquals(start + 30 * min, three.match("m4")!!.scheduledAt) // stage 0 ends @30 on every court
    }

    @Test fun advanceSlotsNewlyDrawnRounds() {
        val teams = (1..4).map { Team("t$it", "Team $it", it) }
        var t = Tournament(
            id = "x", name = "T", teams = teams, algorithm = Algorithm.SINGLE_ELIM, status = TStatus.ACTIVE,
            startAt = start, courts = 2, matchBufferMin = 5, format = format
        )
        t = Scheduler.advance(t.copy(matches = Scheduler.generate(t, Random(1))))
        assertTrue(t.realMatches().all { it.scheduledAt != null })
        t = t.copy(matches = t.matches.map { it.copy(status = MatchStatus.FINISHED, scoreA = 1, winnerId = it.teamAId) })
        t = Scheduler.advance(t, Random(2))
        val fin = t.realMatches().single { it.isFinal }
        assertEquals(start + 30 * min, fin.scheduledAt)
        assertEquals(1, fin.court)
    }
}
