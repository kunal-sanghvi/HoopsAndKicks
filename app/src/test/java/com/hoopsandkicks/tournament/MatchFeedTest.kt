package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchFeedTest {

    private fun cup(sport: Sport = Sport.BASKETBALL) = Tournament(
        id = "t", name = "Cup", sport = sport,
        players = listOf(Player("j", "Jordan"), Player("k", "Kai"), Player("m", "Maya"), Player("l", "Leo")),
        teams = listOf(Team("A", "Wolves", 0, listOf("j", "k")), Team("B", "Bears", 1, listOf("m", "l")))
    )

    private fun match() = Match(
        id = "m1", number = 1, stageIndex = 0, stage = "Final", stageType = StageType.KNOCKOUT,
        teamAId = "A", teamBId = "B", status = MatchStatus.LIVE
    )

    /** Appends log entries the way LiveViewModel does, with increasing timestamps. */
    private class Log(var m: Match) {
        private var now = 1_000L
        fun add(type: MatchEventType, teamId: String? = null, playerId: String? = null, points: Int? = null,
                outId: String? = null, inId: String? = null, voidsSeq: Int? = null, note: String? = null): Log {
            now += 1_000L
            m = MatchLog.append(m, type, now, teamId, playerId, points, outId, inId, voidsSeq, note)
            return this
        }
        fun score(teamId: String, playerId: String?, points: Int, note: String = "1 01:00") =
            add(MatchEventType.SCORE, teamId, playerId, points, note = note)
        fun lastSeq(): Int = m.log.last().seq
    }

    private fun Log.feed(t: Tournament = cup()) = buildMatchFeed(t, m)

    @Test fun scoresInterleaveWithOtherEventsInLogOrder() {
        val log = Log(match()).add(MatchEventType.MATCH_START)
            .score("A", "j", 3, "1 02:10")
            .add(MatchEventType.SUB, "B", outId = "m", inId = "l")
            .add(MatchEventType.PAUSE).add(MatchEventType.RESUME)
            .add(MatchEventType.BREAK_START)
            .add(MatchEventType.BREAK_END).add(MatchEventType.PERIOD_START)
            .score("B", "l", 2, "2 00:30")
        val feed = log.feed()
        assertEquals(
            listOf(FeedItem.Score::class, FeedItem.Substitution::class, FeedItem.HalfTime::class,
                FeedItem.SecondHalf::class, FeedItem.Score::class),
            feed.map { it::class }
        )
        assertEquals(listOf("Jordan +3", "Leo on for Maya", "Half-time", "Second half", "Leo +2"), feed.map { it.title })
    }

    @Test fun runningScoreAndBasketballWording() {
        val feed = Log(match()).add(MatchEventType.MATCH_START)
            .score("A", "j", 3, "1 03:12").score("A", "k", 2).score("B", "m", 1)
            .feed().filterIsInstance<FeedItem.Score>()
        assertEquals(listOf("3–0", "5–0", "5–1"), feed.map { it.runningScore })
        assertEquals("Jordan +3", feed[0].title)
        assertEquals("Wolves · 3–0", feed[0].subtitle)
        assertEquals("1 03:12", feed[0].time)
        assertEquals("Bears · 5–1", feed[2].subtitle)
    }

    @Test fun voidedScoreIsRemovedAndRunningScoreSkipsIt() {
        val log = Log(match()).add(MatchEventType.MATCH_START).score("A", "j", 2)
        log.score("B", "m", 3)
        log.add(MatchEventType.VOID, "B", "m", 3, voidsSeq = log.lastSeq())
        log.score("A", "k", 1)
        val feed = log.feed().filterIsInstance<FeedItem.Score>()
        assertEquals(listOf("Jordan +2", "Kai +1"), feed.map { it.title })
        assertEquals("3–0", feed.last().runningScore)
    }

    @Test fun fallsBackToMatchEventsWhenTheLogHasNoScores() {
        val m = match().copy(
            scoreA = 3, scoreB = 2,
            events = listOf(
                ScoreEvent("e1", "A", "j", 3, "1", "01:00", 10L),
                ScoreEvent("e2", "B", null, 2, "1", "", 20L)
            )
        )
        val feed = buildMatchFeed(cup(), m).filterIsInstance<FeedItem.Score>()
        assertEquals(listOf("Jordan +3", "Bears +2"), feed.map { it.title })
        assertEquals(listOf("3–0", "3–2"), feed.map { it.runningScore })
        assertEquals("1 01:00", feed[0].time)
        assertEquals("1", feed[1].time)
    }

    @Test fun finishedOlderMatchWithoutLogStillEndsWithTheResult() {
        val m = match().copy(
            status = MatchStatus.FINISHED, scoreA = 3, winnerId = "A",
            events = listOf(ScoreEvent("e1", "A", "j", 3, "1", "01:00", 10L))
        )
        val last = buildMatchFeed(cup(), m).last() as FeedItem.FullTime
        assertEquals("Full time", last.title)
        assertEquals("Wolves won", last.subtitle)
    }

    @Test fun substitutionWording() {
        val sub = Log(match()).add(MatchEventType.MATCH_START)
            .add(MatchEventType.SUB, "A", outId = "j", inId = "k")
            .feed().single() as FeedItem.Substitution
        assertEquals("Kai on for Jordan", sub.title)
        assertEquals("Wolves", sub.subtitle)
        assertEquals("A", sub.teamId)
        assertNull(sub.time)
    }

    @Test fun fullTimeWinAndDrawResultLines() {
        val win = Log(match()).add(MatchEventType.MATCH_START).score("A", "j", 2)
        win.m = win.m.copy(scoreA = 2)
        win.add(MatchEventType.MATCH_END, "A", note = "")
        win.m = win.m.copy(status = MatchStatus.FINISHED, winnerId = "A")
        assertEquals("Wolves won", win.feed().last().subtitle)

        val draw = Log(match()).add(MatchEventType.MATCH_START).add(MatchEventType.MATCH_END, note = DRAW_NOTE)
        draw.m = draw.m.copy(status = MatchStatus.FINISHED, tieNote = DRAW_NOTE)
        val ft = draw.feed().single()
        assertEquals("Full time", ft.title)
        assertEquals("Draw", ft.subtitle)
    }

    @Test fun shootoutHeaderAttemptsAndResultLine() {
        val log = Log(match().copy(scoreA = 2, scoreB = 2)).add(MatchEventType.MATCH_START)
            .add(MatchEventType.TIEBREAK_START)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "A", "j", 1)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "B", "m", 0)
        log.add(MatchEventType.SHOOTOUT_ATTEMPT, "A", "k", 0)
        log.add(MatchEventType.VOID, "A", "k", 0, voidsSeq = log.lastSeq())
        log.add(MatchEventType.SHOOTOUT_ATTEMPT, "A", "k", 1)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "B", "l", 1)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "A", "j", 0)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "B", "m", 0)
        val note = shootoutNote(Sport.BASKETBALL, 2, 1)
        log.add(MatchEventType.MATCH_END, "A", note = note)
        log.m = log.m.copy(status = MatchStatus.FINISHED, winnerId = "A", tieNote = note)
        val feed = log.feed()
        assertEquals("Free-throw shootout", feed[0].title)
        assertEquals(
            listOf("Jordan made", "Maya missed", "Kai made", "Leo made", "Jordan missed", "Maya missed"),
            feed.filterIsInstance<FeedItem.ShootoutAttempt>().map { it.title }
        )
        assertEquals("Bears", feed[2].subtitle)
        assertEquals("Won 2–1 on free throws", feed.last().subtitle)
    }

    @Test fun abandonedShootoutAndReopenedFullTimeAreDropped() {
        val log = Log(match()).add(MatchEventType.MATCH_START).score("A", "j", 2)
        log.score("B", "m", 2)
        val tiedSeq = log.lastSeq()
        log.add(MatchEventType.TIEBREAK_START)
            .add(MatchEventType.VOID, "B", "m", 2, voidsSeq = tiedSeq)
            .add(MatchEventType.MATCH_END, "A")
            .add(MatchEventType.REOPEN)
            .score("A", "k", 1)
            .add(MatchEventType.MATCH_END, "A")
        log.m = log.m.copy(status = MatchStatus.FINISHED, winnerId = "A", scoreA = 3)
        val feed = log.feed()
        assertEquals(listOf("Jordan +2", "Kai +1", "Full time"), feed.map { it.title })
    }

    @Test fun footballWording() {
        val t = cup(Sport.FOOTBALL)
        val log = Log(match()).add(MatchEventType.MATCH_START).score("A", "j", 1, "1 12:30").score("B", null, 1)
            .add(MatchEventType.TIEBREAK_START)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "A", "k", 1)
            .add(MatchEventType.SHOOTOUT_ATTEMPT, "B", null, 0)
        val feed = log.feed(t)
        assertEquals("Goal · Jordan", feed[0].title)
        assertEquals("Wolves · 1–0", feed[0].subtitle)
        assertEquals("Goal · Bears", feed[1].title)
        assertEquals("Bears · 1–1", feed[1].subtitle)
        assertEquals("Free-kick shootout", feed[2].title)
        assertEquals("Kai scored", feed[3].title)
        assertEquals("Bears missed", feed[4].title)
    }

    @Test fun unknownOrMissingPlayerFallsBackToTheTeam() {
        val feed = Log(match()).add(MatchEventType.MATCH_START)
            .score("A", null, 3).score("B", "ghost", 2)
            .feed().filterIsInstance<FeedItem.Score>()
        assertNull(feed[0].playerName)
        assertEquals("Wolves +3", feed[0].title)
        assertNull(feed[1].playerName)
        assertEquals("Bears +2", feed[1].title)
    }

    @Test fun emptyForNotStartedMatchesAndByes() {
        assertTrue(buildMatchFeed(cup(), match().copy(status = MatchStatus.SCHEDULED)).isEmpty())
        assertTrue(buildMatchFeed(cup(), match().copy(bye = true)).isEmpty())
        assertTrue(Log(match()).add(MatchEventType.MATCH_START).add(MatchEventType.PAUSE).feed().isEmpty())
    }
}
