package com.hoopsandkicks.tournament.data

import kotlin.random.Random

/** Pure Kotlin scheduling and progression logic (no Android dependencies, unit-testable). */
object Scheduler {

    // ---------- team split ----------

    fun randomSplit(playerIds: List<String>, teamIds: List<String>, rnd: Random = Random.Default): Map<String, List<String>> {
        val res = LinkedHashMap<String, MutableList<String>>()
        teamIds.forEach { res[it] = mutableListOf() }
        if (teamIds.isEmpty()) return res
        playerIds.shuffled(rnd).forEachIndexed { i, p -> res[teamIds[i % teamIds.size]]!!.add(p) }
        return res
    }

    /** Snake draft on skill rating so teams end up with similar strength. */
    fun balancedSplit(players: List<Player>, teamIds: List<String>, rnd: Random = Random.Default): Map<String, List<String>> {
        val res = LinkedHashMap<String, MutableList<String>>()
        teamIds.forEach { res[it] = mutableListOf() }
        val n = teamIds.size
        if (n == 0) return res
        val sorted = players.shuffled(rnd).sortedByDescending { it.skill }
        sorted.forEachIndexed { i, p ->
            val round = i / n
            val pos = i % n
            val idx = if (round % 2 == 0) pos else n - 1 - pos
            res[teamIds[idx]]!!.add(p.id)
        }
        return res
    }

    // ---------- helpers ----------

    private fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p *= 2
        return p
    }

    private fun bracketOrder(n: Int): List<Int> {
        if (n <= 1) return listOf(1)
        val prev = bracketOrder(n / 2)
        return prev.flatMap { listOf(it, n + 1 - it) }
    }

    fun roundName(teamsInRound: Int): String = when (teamsInRound) {
        2 -> "Final"
        4 -> "Semi-finals"
        8 -> "Quarter-finals"
        else -> "Round of $teamsInRound"
    }

    private fun koLabel(teamsInRound: Int, idx: Int): String = when (teamsInRound) {
        2 -> "Final"
        4 -> "Semi-final $idx"
        8 -> "Quarter-final $idx"
        else -> "Round of $teamsInRound · $idx"
    }

    private fun mk(
        stageIndex: Int, stage: String, type: StageType, group: String, round: Int, label: String,
        a: String?, b: String?, isFinal: Boolean = false, bye: Boolean = false, winner: String? = null
    ): Match = Match(
        id = newId(), number = 0, stageIndex = stageIndex, stage = stage, stageType = type, group = group,
        round = round, label = label, teamAId = a, teamBId = b, isFinal = isFinal, bye = bye,
        status = if (bye) MatchStatus.FINISHED else MatchStatus.SCHEDULED, winnerId = winner
    )

    private fun numbered(existing: List<Match>, added: List<Match>): List<Match> {
        var n = existing.filter { !it.bye }.maxOfOrNull { it.number } ?: 0
        return added.map { if (it.bye) it else { n += 1; it.copy(number = n) } }
    }

    /** Circle-method round robin. Returns list of rounds, each a list of pairs. */
    fun roundRobinRounds(ids: List<String>): List<List<Pair<String, String>>> {
        val list = ids.toMutableList<String?>()
        if (list.size % 2 == 1) list.add(null)
        val n = list.size
        val rounds = ArrayList<List<Pair<String, String>>>()
        if (n < 2) return rounds
        for (r in 0 until n - 1) {
            val pairs = ArrayList<Pair<String, String>>()
            for (i in 0 until n / 2) {
                val a = list[i]
                val b = list[n - 1 - i]
                if (a != null && b != null) pairs.add(a to b)
            }
            rounds.add(pairs)
            val last = list.removeAt(n - 1)
            list.add(1, last)
        }
        return rounds
    }

    fun groupName(i: Int): String = "Group ${'A' + i}"

    private fun clampedGroups(t: Tournament): Int = t.groups.coerceIn(1, t.maxGroups())

    /** Teams that advance from each group (bounded by smallest group). */
    fun clampedAdvance(t: Tournament): Int {
        val g = clampedGroups(t)
        val minSize = maxOf(1, t.teams.size / g)
        var adv = t.advance.coerceIn(1, minSize)
        if (g * adv < 2) adv = minOf(2, minSize).coerceAtLeast(1)
        return adv
    }

    /** Round Robin semi-finals (top 4) are on: chosen, and enough teams for them. */
    fun rrSemisOn(t: Tournament): Boolean = t.rrSemis && t.teams.size > 4

    /** Round Robin final is on: chosen (or implied by semi-finals, which need a final to name a champion) with 3+ teams. */
    fun rrFinalOn(t: Tournament): Boolean = t.teams.size > 2 && (t.rrFinal || rrSemisOn(t))

    /** First knockout round for [seeds]; empty when there are fewer than 2 (no bracket to draw, like [roundRobinRounds]). */
    private fun firstRound(seeds: List<String>, stageIndex: Int): List<Match> {
        // With 0 or 1 seeds the bracket would have size 1, and pairing order[i] with order[i + 1] runs off the end.
        if (seeds.size < 2) return emptyList()
        val size = nextPow2(seeds.size)
        val order = bracketOrder(size)
        val out = ArrayList<Match>()
        var idx = 0
        var i = 0
        while (i < size) {
            val a = seeds.getOrNull(order[i] - 1)
            val b = seeds.getOrNull(order[i + 1] - 1)
            idx += 1
            val name = roundName(size)
            if (a != null && b != null) {
                out.add(mk(stageIndex, name, StageType.KNOCKOUT, "", stageIndex, koLabel(size, idx), a, b, isFinal = size == 2))
            } else {
                val w = a ?: b
                out.add(mk(stageIndex, name, StageType.KNOCKOUT, "", stageIndex, "Bye", w, null, bye = true, winner = w))
            }
            i += 2
        }
        return out
    }

    private fun nextKoRound(winners: List<String>, stageIndex: Int): List<Match> {
        val size = winners.size
        val out = ArrayList<Match>()
        var idx = 0
        var i = 0
        while (i + 1 < size) {
            idx += 1
            out.add(mk(stageIndex, roundName(size), StageType.KNOCKOUT, "", stageIndex, koLabel(size, idx), winners[i], winners[i + 1], isFinal = size == 2))
            i += 2
        }
        return out
    }

    private fun pairAvoiding(ids: List<String>, played: Set<Set<String>>): Pair<List<Pair<String, String>>, List<String>> {
        val rem = ids.toMutableList()
        val out = ArrayList<Pair<String, String>>()
        while (rem.size >= 2) {
            val a = rem.removeAt(0)
            var bi = rem.indexOfFirst { setOf(a, it) !in played }
            if (bi < 0) bi = 0
            val b = rem.removeAt(bi)
            out.add(a to b)
        }
        return out to rem
    }

    private fun playedPairs(ms: List<Match>): Set<Set<String>> =
        ms.filter { !it.bye && it.teamAId != null && it.teamBId != null }.map { setOf(it.teamAId!!, it.teamBId!!) }.toSet()

    // ---------- initial generation ----------

    fun generate(t: Tournament, rnd: Random = Random.Default): List<Match> {
        val ids = t.teams.map { it.id }
        val ms: List<Match> = when (t.algorithm) {
            Algorithm.GROUP_KO -> groupStage(ids, clampedGroups(t), rnd)
            Algorithm.ROUND_ROBIN -> {
                val rounds = roundRobinRounds(ids.shuffled(rnd))
                val out = ArrayList<Match>()
                rounds.forEachIndexed { r, pairs ->
                    pairs.forEach { (a, b) -> out.add(mk(0, "Round Robin", StageType.LEAGUE, "", r + 1, "Round ${r + 1}", a, b)) }
                }
                out
            }
            Algorithm.SINGLE_ELIM -> firstRound(ids.shuffled(rnd), 0)
            Algorithm.DOUBLE_ELIM -> doubleElimRound(ids.shuffled(rnd), emptyList(), 1)
            Algorithm.SWISS -> swissRound(ids.shuffled(rnd), emptyList(), 1)
        }
        return numbered(emptyList(), ms)
    }

    private fun groupStage(ids: List<String>, g: Int, rnd: Random): List<Match> {
        val shuffled = ids.shuffled(rnd)
        val buckets = List(g) { mutableListOf<String>() }
        shuffled.forEachIndexed { i, id -> buckets[i % g].add(id) }
        val tmp = ArrayList<Triple<Int, Int, Match>>() // round, groupIdx, match
        buckets.forEachIndexed { gi, teamIds ->
            val rounds = roundRobinRounds(teamIds)
            rounds.forEachIndexed { r, pairs ->
                pairs.forEach { (a, b) ->
                    tmp.add(Triple(r + 1, gi, mk(0, "Group Stage", StageType.GROUP, groupName(gi), r + 1, "Match", a, b)))
                }
            }
        }
        return tmp.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    private fun swissRounds(teamCount: Int): Int {
        var r = 0
        var p = 1
        while (p < teamCount) { p *= 2; r += 1 }
        return maxOf(1, r)
    }

    private fun swissRound(orderedIds: List<String>, prior: List<Match>, round: Int): List<Match> {
        var pool = orderedIds
        val out = ArrayList<Match>()
        if (pool.size % 2 == 1) {
            val hadBye = prior.filter { it.bye }.mapNotNull { it.teamAId }.toSet()
            val byeTeam = pool.reversed().firstOrNull { it !in hadBye } ?: pool.last()
            pool = pool.filter { it != byeTeam }
            out.add(mk(0, "Swiss", StageType.LEAGUE, "", round, "Round $round · Bye", byeTeam, null, bye = true, winner = byeTeam))
        }
        val (pairs, _) = pairAvoiding(pool, playedPairs(prior))
        pairs.forEach { (a, b) -> out.add(mk(0, "Swiss", StageType.LEAGUE, "", round, "Round $round", a, b)) }
        return out
    }

    private fun lossCounts(ids: List<String>, ms: List<Match>): Map<String, Int> {
        val res = HashMap<String, Int>()
        ids.forEach { res[it] = 0 }
        ms.filter { !it.bye && it.status == MatchStatus.FINISHED && it.winnerId != null }.forEach { m ->
            val loser = if (m.winnerId == m.teamAId) m.teamBId else m.teamAId
            if (loser != null) res[loser] = (res[loser] ?: 0) + 1
        }
        return res
    }

    private fun doubleElimRound(ids: List<String>, prior: List<Match>, round: Int): List<Match> {
        val losses = lossCounts(ids, prior)
        val alive = ids.filter { (losses[it] ?: 0) < 2 }
        val p0 = alive.filter { (losses[it] ?: 0) == 0 }
        val p1 = alive.filter { (losses[it] ?: 0) == 1 }
        val played = playedPairs(prior)
        val (pairs0, left0) = pairAvoiding(p0, played)
        val (pairs1, left1) = pairAvoiding(p1, played)
        val isGF = alive.size == 2
        val out = ArrayList<Match>()
        fun add(a: String, b: String, label: String) {
            out.add(mk(0, "Double Elimination", StageType.KNOCKOUT, "", round, if (isGF) "Grand Final" else label, a, b, isFinal = isGF))
        }
        pairs0.forEach { add(it.first, it.second, "Winners side") }
        pairs1.forEach { add(it.first, it.second, "Elimination") }
        if (left0.isNotEmpty() && left1.isNotEmpty()) {
            add(left0[0], left1[0], "Cross match")
        } else {
            val single = left0.firstOrNull() ?: left1.firstOrNull()
            if (single != null) {
                out.add(mk(0, "Double Elimination", StageType.KNOCKOUT, "", round, "Bye", single, null, bye = true, winner = single))
            }
        }
        return out
    }

    // ---------- progression ----------

    fun expectedStages(t: Tournament): List<String> {
        val ids = t.teams.size
        return when (t.algorithm) {
            Algorithm.GROUP_KO -> {
                val q = clampedGroups(t) * clampedAdvance(t)
                listOf("Group Stage") + koNames(q)
            }
            Algorithm.SINGLE_ELIM -> koNames(ids)
            // Semi-finals (top 4) and the Final (top 2 / semi winners) are optional; without them the round-robin
            // standings decide the champion.
            Algorithm.ROUND_ROBIN ->
                listOf("Round Robin") + (if (rrSemisOn(t)) listOf(roundName(4)) else emptyList()) +
                    (if (rrFinalOn(t)) listOf(roundName(2)) else emptyList())
            Algorithm.DOUBLE_ELIM -> listOf("Double Elimination")
            Algorithm.SWISS -> listOf("Swiss")
        }
    }

    private fun koNames(qualifiers: Int): List<String> {
        val out = ArrayList<String>()
        var size = nextPow2(maxOf(2, qualifiers))
        while (size >= 2) {
            out.add(roundName(size))
            size /= 2
        }
        return out
    }

    /** Runs progression to a fixed point, then re-slots times/courts so any newly drawn round is scheduled too. */
    fun advance(t: Tournament, rnd: Random = Random.Default): Tournament {
        var cur = t
        var guard = 0
        while (guard < 60) {
            guard += 1
            val n = step(cur, rnd) ?: break
            cur = n
        }
        return scheduleTimes(cur)
    }

    // ---------- time/court slotting ----------

    private fun durationMs(t: Tournament, m: Match): Long =
        (t.format.forMatch(m).estimatedDurationMin() + t.matchBufferMin.coerceAtLeast(0)) * 60_000L

    /**
     * Matches sharing a key form one "stage" for slotting: nothing in a later stage may start before every
     * match of the earlier stages has ended. That is the stage index, except for Swiss and double elimination,
     * where everything is stage 0 but each round is drawn from the previous round's results, so each round
     * gates the next. (Rounds inside a group stage or round robin don't depend on each other and aren't gated.)
     */
    private fun gateKey(t: Tournament, m: Match): Int =
        if (t.algorithm == Algorithm.SWISS || t.algorithm == Algorithm.DOUBLE_ELIM) m.frontier() else m.stageIndex * 1000

    /**
     * A match that has started (live, at a break, in a tie-break), finished, or was manually pinned by
     * [reorderCourt]: its slot is settled and [scheduleTimes] never recomputes it on its own.
     */
    private fun isFixed(m: Match): Boolean = m.status != MatchStatus.SCHEDULED || m.pinned

    /**
     * Assigns [Match.scheduledAt] and [Match.court] to every non-bye match that hasn't started yet, in
     * (stageIndex, round, number) order: each match goes on the court that is available earliest (lowest
     * index on ties). A court is available at the later of when it frees up and when the previous stage has
     * completely ended (latest start + estimated length + changeover across all of that stage's matches, see
     * [gateKey]), so a stage never starts on one court while the previous stage is still running on another.
     *
     * Matches that have already started or finished ([isFixed]) keep their existing time and court: they are
     * history, so adding a court or changing the format can't move them. They still count: their court is busy
     * until their slot's end (start + estimated length + changeover), they count towards their stage's end for
     * gating, and nothing still to be played is planned to start before the latest of their start times (that
     * match has begun, so the time before it is gone).
     *
     * Deterministic for the same matches and settings. No-op when [Tournament.startAt] is unset.
     * Byes keep null time/court. Uses estimated durations, so shootout tie-breaks are not accounted for.
     */
    fun scheduleTimes(t: Tournament): Tournament {
        if (t.startAt <= 0L) return t
        val fixed = t.matches.filter { !it.bye && isFixed(it) && it.scheduledAt != null }
        val notBefore = maxOf(t.startAt, fixed.maxOfOrNull { it.scheduledAt!! } ?: t.startAt)
        val courtFreeAt = LongArray(t.courts.coerceAtLeast(1)) { notBefore }
        for (m in fixed) {
            // A court beyond the current count (courts were reduced) no longer takes new matches, so it's skipped.
            val c = (m.court ?: continue) - 1
            if (c in courtFreeAt.indices) courtFreeAt[c] = maxOf(courtFreeAt[c], m.scheduledAt!! + durationMs(t, m))
        }
        val slots = HashMap<String, Pair<Long, Int>>()
        val order = t.matches.filter { !it.bye }
            .sortedWith(compareBy<Match>({ it.stageIndex }, { it.round }, { it.number }))
        var previousStageEndsAt = t.startAt
        // groupBy keeps first-appearance order, so stages come out in the sorted (stageIndex, round) order.
        for (stage in order.groupBy { gateKey(t, it) }.values) {
            var stageEndsAt = previousStageEndsAt
            for (m in stage) {
                if (isFixed(m)) {
                    // Keeps its slot (court already marked busy above); only its end feeds the stage gate.
                    val s = m.scheduledAt ?: continue
                    stageEndsAt = maxOf(stageEndsAt, s + durationMs(t, m))
                    continue
                }
                var idx = 0
                for (i in 1 until courtFreeAt.size) {
                    if (maxOf(courtFreeAt[i], previousStageEndsAt) < maxOf(courtFreeAt[idx], previousStageEndsAt)) idx = i
                }
                val startAt = maxOf(courtFreeAt[idx], previousStageEndsAt)
                val endsAt = startAt + durationMs(t, m)
                slots[m.id] = startAt to (idx + 1)
                courtFreeAt[idx] = endsAt
                if (endsAt > stageEndsAt) stageEndsAt = endsAt
            }
            previousStageEndsAt = stageEndsAt
        }
        return t.copy(matches = t.matches.map { m ->
            val s = slots[m.id]
            if (m.bye || s == null) m else m.copy(scheduledAt = s.first, court = s.second)
        })
    }

    /**
     * Admin drag-and-drop reorder (Fixtures screen, per court): re-sequences the still-[MatchStatus.SCHEDULED]
     * matches on [court] to follow [newOrderIds] and re-slots the whole tournament around that new order.
     *
     * The reordered matches are placed back-to-back on [court] (respecting [Tournament.matchBufferMin]),
     * starting right after whatever on that court already can't move (a match that's live, at a break, in a
     * tie-break, already finished, or pinned by an earlier reorder) — those keep their existing slot and are
     * left out of [newOrderIds] by the caller, same as the design's "locked" rows. The reordered matches are
     * then marked [Match.pinned] so the follow-up [scheduleTimes] call (which re-times every other match
     * around them) never quietly slides them back to their old order, and so does anything later that calls
     * [scheduleTimes] again (adding a court, extending the end time, an earlier match finishing early, …).
     *
     * A no-op if the tournament has no start time yet, [court] is out of range, or [newOrderIds] contains no
     * still-movable match on that court (e.g. the admin tried to reorder matches that have already started).
     *
     * A match already reordered by an earlier call ([Match.pinned]) is still movable here — pinning only
     * stops [scheduleTimes] from re-slotting it on its own; it doesn't lock the admin out of moving it again.
     * Only a match that has actually started, is at a break/tie-break, or has finished is off-limits.
     */
    fun reorderCourt(t: Tournament, court: Int, newOrderIds: List<String>): Tournament {
        if (t.startAt <= 0L || court < 1 || court > t.courts) return t
        val byId = t.matches.associateBy { it.id }
        val movable = newOrderIds.mapNotNull { byId[it] }
            .filter { it.court == court && !it.bye && it.status == MatchStatus.SCHEDULED }
        if (movable.isEmpty()) return t
        // Anything on this court that's already under way or finished can't be pushed around by a reorder, so
        // the movable matches start right after the latest of those — or the tournament start if there's
        // nothing under way on this court yet.
        val settledEnds = t.matches
            .filter { it.court == court && !it.bye && it.status != MatchStatus.SCHEDULED && it.scheduledAt != null }
            .maxOfOrNull { it.scheduledAt!! + durationMs(t, it) }
        var cursor = maxOf(t.startAt, settledEnds ?: t.startAt)
        val moved = HashMap<String, Match>()
        for (m in movable) {
            moved[m.id] = m.copy(scheduledAt = cursor, court = court, pinned = true)
            cursor += durationMs(t, m)
        }
        return scheduleTimes(t.copy(matches = t.matches.map { moved[it.id] ?: it }))
    }

    /**
     * Minutes by which the latest estimated match end ([estimatedEndAt]: start + estimated duration, excluding
     * the trailing changeover buffer) passes [Tournament.endAt], rounded up; 0 if it fits or start/end aren't set.
     */
    fun scheduleOverrunMinutes(t: Tournament): Long {
        if (t.startAt <= 0L || t.endAt <= 0L) return 0L
        val lastEnd = t.matches.mapNotNull { it.estimatedEndAt(t) }.maxOrNull() ?: return 0L
        val over = lastEnd - t.endAt
        return if (over <= 0L) 0L else (over + 59_999L) / 60_000L
    }

    /**
     * True when every slotted match is planned to end by [Tournament.endAt] (equivalently, no match
     * [exceedsWindow]). Enforced only when a plan is made (a new tournament's first schedule, or a format change, is
     * refused unless the whole [projectedSchedule] fits); once matches exist the plan is a display estimate and never
     * stops a match from being started.
     */
    fun fits(t: Tournament): Boolean = scheduleOverrunMinutes(t) <= 0L

    /**
     * The whole tournament as it would be slotted if it were played out: every open match is given a
     * placeholder result (team A wins) and [advance] draws the next stage/round, until the tournament completes.
     * Used to check up front that *all* stages (not just the first one, which is all that exists before any
     * match is played) fit the time window. Only structure and times are meaningful (results and double
     * elimination paths are made up), so the returned tournament must never be saved.
     */
    fun projectedSchedule(t: Tournament, rnd: Random = Random.Default): Tournament {
        var cur = scheduleTimes(t)
        var guard = 0
        while (cur.status == TStatus.ACTIVE && guard < 64) {
            guard += 1
            if (cur.matches.all { it.status == MatchStatus.FINISHED }) break
            cur = advance(cur.copy(matches = cur.matches.map {
                if (it.status == MatchStatus.FINISHED || it.teamAId == null) it
                else it.copy(status = MatchStatus.FINISHED, scoreA = 1, scoreB = 0, winnerId = it.teamAId)
            }), rnd)
        }
        return cur
    }

    /**
     * [scheduleOverrunMinutes] of the whole tournament played out ([projectedSchedule]), not just the matches drawn
     * so far: what the hub banner and its "Extend end" fix use, so extending once also covers the stages still to be
     * drawn (otherwise the next knockout round could lock again as soon as it's drawn). 0 when everything fits.
     */
    fun projectedOverrunMinutes(t: Tournament): Long = scheduleOverrunMinutes(projectedSchedule(t))

    private fun complete(t: Tournament, champion: String?): Tournament =
        t.copy(status = TStatus.COMPLETED, championId = champion)

    private fun addMatches(t: Tournament, added: List<Match>): Tournament =
        t.copy(matches = t.matches + numbered(t.matches, added))

    private fun step(t: Tournament, rnd: Random): Tournament? {
        if (t.status != TStatus.ACTIVE) return null
        val ms = t.matches
        if (ms.isEmpty()) return null
        val ids = t.teams.map { it.id }
        when (t.algorithm) {
            Algorithm.ROUND_ROBIN -> {
                // Once a Final has been drawn (top 2 by round-robin standings), the tournament is
                // decided by that match, not by the round-robin standings anymore.
                val final = ms.firstOrNull { it.isFinal }
                if (final != null) {
                    if (final.status != MatchStatus.FINISHED) return null
                    return complete(t, final.winnerId)
                }
                val ls = ms.maxOf { it.stageIndex }
                val semis = ms.filter { it.stageType == StageType.KNOCKOUT && it.stage == roundName(4) }
                if (semis.isNotEmpty()) {
                    if (semis.any { it.status != MatchStatus.FINISHED }) return null
                    val winners = semis.sortedBy { it.number }.mapNotNull { it.winnerId }
                    if (winners.size < 2) return complete(t, winners.firstOrNull())
                    return addMatches(
                        t,
                        listOf(mk(ls + 1, "Final", StageType.LEAGUE, "", 1, "Final", winners[0], winners[1], isFinal = true))
                    )
                }
                if (ms.all { it.status == MatchStatus.FINISHED }) {
                    val top = Standings.compute(ids, ms).map { it.teamId }
                    if (rrSemisOn(t) && top.size >= 4) {
                        return addMatches(
                            t,
                            listOf(
                                mk(ls + 1, roundName(4), StageType.KNOCKOUT, "", 1, koLabel(4, 1), top[0], top[3]),
                                mk(ls + 1, roundName(4), StageType.KNOCKOUT, "", 1, koLabel(4, 2), top[1], top[2])
                            )
                        )
                    }
                    // No final (or only 2 teams, where the single match is the decider): standings decide.
                    if (!rrFinalOn(t) || top.size < 2) return complete(t, top.firstOrNull())
                    return addMatches(
                        t,
                        listOf(mk(ls + 1, "Final", StageType.LEAGUE, "", 1, "Final", top[0], top[1], isFinal = true))
                    )
                }
                return null
            }
            Algorithm.GROUP_KO, Algorithm.SINGLE_ELIM -> {
                val ls = ms.maxOf { it.stageIndex }
                val stage = ms.filter { it.stageIndex == ls }
                if (stage.any { it.status != MatchStatus.FINISHED }) return null
                if (stage.first().stageType == StageType.GROUP) {
                    val groupNames = stage.map { it.group }.distinct().sorted()
                    val adv = clampedAdvance(t)
                    val perGroup = groupNames.map { g ->
                        val gm = stage.filter { it.group == g }
                        val gids = gm.flatMap { listOfNotNull(it.teamAId, it.teamBId) }.distinct()
                        Standings.compute(gids, gm).map { it.teamId }
                    }
                    val seeds = ArrayList<String>()
                    for (rank in 0 until adv) {
                        perGroup.forEach { list -> list.getOrNull(rank)?.let { seeds.add(it) } }
                    }
                    if (seeds.size < 2) return complete(t, seeds.firstOrNull())
                    return addMatches(t, firstRound(seeds, ls + 1))
                }
                val winners = stage.mapNotNull { it.winnerId }
                if (winners.size <= 1) return complete(t, winners.firstOrNull())
                return addMatches(t, nextKoRound(winners, ls + 1))
            }
            Algorithm.DOUBLE_ELIM -> {
                val lr = ms.maxOf { it.round }
                if (ms.filter { it.round == lr }.any { it.status != MatchStatus.FINISHED }) return null
                val losses = lossCounts(ids, ms)
                val alive = ids.filter { (losses[it] ?: 0) < 2 }
                if (alive.size <= 1) return complete(t, alive.firstOrNull())
                return addMatches(t, doubleElimRound(ids, ms, lr + 1))
            }
            Algorithm.SWISS -> {
                val lr = ms.maxOf { it.round }
                if (ms.filter { it.round == lr }.any { it.status != MatchStatus.FINISHED }) return null
                val rows = Standings.compute(ids, ms)
                if (lr >= swissRounds(ids.size)) return complete(t, rows.firstOrNull()?.teamId)
                return addMatches(t, swissRound(rows.map { it.teamId }, ms, lr + 1))
            }
        }
        return null
    }
}

data class StandingRow(
    val teamId: String,
    val played: Int,
    val wins: Int,
    val losses: Int,
    val pf: Int,
    val pa: Int,
    val points: Int
) {
    val pd: Int get() = pf - pa
}

object Standings {
    const val WIN_POINTS = 2

    /**
     * Ranks teams by points, then head-to-head (only when exactly two teams are tied),
     * then point difference, then points scored.
     */
    fun compute(teamIds: List<String>, matches: List<Match>): List<StandingRow> {
        val played = HashMap<String, Int>()
        val wins = HashMap<String, Int>()
        val losses = HashMap<String, Int>()
        val pf = HashMap<String, Int>()
        val pa = HashMap<String, Int>()
        teamIds.forEach { played[it] = 0; wins[it] = 0; losses[it] = 0; pf[it] = 0; pa[it] = 0 }

        val finished = matches.filter { it.status == MatchStatus.FINISHED }
        for (m in finished) {
            val a = m.teamAId ?: continue
            if (a !in played) continue
            if (m.bye) {
                played[a] = played[a]!! + 1
                wins[a] = wins[a]!! + 1
                continue
            }
            val b = m.teamBId ?: continue
            if (b !in played) continue
            played[a] = played[a]!! + 1
            played[b] = played[b]!! + 1
            pf[a] = pf[a]!! + m.scoreA
            pa[a] = pa[a]!! + m.scoreB
            pf[b] = pf[b]!! + m.scoreB
            pa[b] = pa[b]!! + m.scoreA
            val w = m.winnerId
            if (w == a) { wins[a] = wins[a]!! + 1; losses[b] = losses[b]!! + 1 }
            else if (w == b) { wins[b] = wins[b]!! + 1; losses[a] = losses[a]!! + 1 }
        }

        val rows = teamIds.map {
            StandingRow(it, played[it]!!, wins[it]!!, losses[it]!!, pf[it]!!, pa[it]!!, wins[it]!! * WIN_POINTS)
        }
        val order = teamIds.withIndex().associate { it.value to it.index }
        val sorted = rows.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.pd }
                .thenByDescending { it.pf }
                .thenBy { order[it.teamId] ?: 0 }
        ).toMutableList()

        // Head-to-head fix-up for runs of exactly two teams level on points.
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1].points == sorted[i].points) j += 1
            if (j - i + 1 == 2) {
                val x = sorted[i]
                val y = sorted[j]
                val h2h = finished.firstOrNull {
                    !it.bye && ((it.teamAId == x.teamId && it.teamBId == y.teamId) || (it.teamAId == y.teamId && it.teamBId == x.teamId))
                }
                if (h2h != null && h2h.winnerId == y.teamId) {
                    sorted[i] = y
                    sorted[j] = x
                }
            }
            i = j + 1
        }
        return sorted
    }
}

data class OverallRow(
    val teamId: String,
    val statusText: String,
    val groupPts: Int,
    val koPts: Int,
    val finalPts: Int,
    val total: Int,
    val pd: Int
)

data class PlayerStat(
    val playerId: String,
    val teamId: String?,
    val points: Int,
    val threes: Int,
    val games: Int
) {
    val ppg: Double get() = if (games == 0) 0.0 else points.toDouble() / games
}

object Leaderboards {

    fun overall(t: Tournament): List<OverallRow> {
        val ids = t.teams.map { it.id }
        val finished = t.matches.filter { it.status == MatchStatus.FINISHED && !it.bye }
        val hasKo = t.matches.any { it.stageType == StageType.KNOCKOUT }
        val lastKoRound = t.matches.filter { it.stageType == StageType.KNOCKOUT }.maxOfOrNull { it.frontier() }

        data class Tmp(val row: OverallRow, val depth: Int, val champ: Boolean)

        val rows = ids.map { id ->
            val mine = finished.filter { it.teamAId == id || it.teamBId == id }
            var g = 0; var k = 0; var f = 0; var pd = 0
            mine.forEach { m ->
                val won = m.winnerId == id
                val pts = if (won) Standings.WIN_POINTS else 0
                when {
                    m.isFinal -> f += pts
                    m.stageType == StageType.KNOCKOUT -> k += pts
                    else -> g += pts
                }
                pd += if (m.teamAId == id) m.scoreA - m.scoreB else m.scoreB - m.scoreA
            }
            val allMine = t.matches.filter { it.teamAId == id || it.teamBId == id }
            val depth = allMine.maxOfOrNull { it.frontier() } ?: 0
            val champ = t.championId == id
            val playedFinal = allMine.any { it.isFinal && !it.bye && it.status == MatchStatus.FINISHED }
            val koMatches = allMine.filter { it.stageType == StageType.KNOCKOUT && !it.bye }
            val lastKo = koMatches.maxByOrNull { it.frontier() }
            val status = when {
                champ -> "Champion"
                playedFinal && t.status == TStatus.COMPLETED -> "Finalist"
                t.algorithm == Algorithm.DOUBLE_ELIM -> if (lastKoLosses(id, t) >= 2) "Eliminated" else "Still in"
                lastKo != null -> {
                    if (lastKo.status == MatchStatus.FINISHED && lastKo.winnerId != id) "Out in ${lastKo.stage}"
                    else if (t.status == TStatus.COMPLETED) "Out in ${lastKo.stage}" else "Still in"
                }
                hasKo && lastKoRound != null -> "Out in groups"
                t.status == TStatus.COMPLETED -> "Completed"
                else -> "In progress"
            }
            Tmp(OverallRow(id, status, g, k, f, g + k + f, pd), depth, champ)
        }
        return rows.sortedWith(
            compareByDescending<Tmp> { it.champ }
                .thenByDescending { it.depth }
                .thenByDescending { it.row.total }
                .thenByDescending { it.row.pd }
        ).map { it.row }
    }

    private fun lastKoLosses(id: String, t: Tournament): Int =
        t.matches.count {
            !it.bye && it.status == MatchStatus.FINISHED && it.winnerId != null && it.winnerId != id &&
                (it.teamAId == id || it.teamBId == id)
        }

    /** Player stats, optionally restricted to one stage index. */
    fun players(t: Tournament, stageIndex: Int? = null): List<PlayerStat> {
        val ms = t.matches.filter {
            !it.bye && (it.status == MatchStatus.FINISHED || it.status == MatchStatus.LIVE ||
                it.status == MatchStatus.BREAK || it.status == MatchStatus.TIEBREAK) &&
                (stageIndex == null || it.stageIndex == stageIndex)
        }
        val pts = HashMap<String, Int>()
        val threes = HashMap<String, Int>()
        val games = HashMap<String, MutableSet<String>>()
        val teamOf = HashMap<String, String>()
        for (m in ms) {
            (m.lineupA).forEach { p -> games.getOrPut(p) { mutableSetOf() }.add(m.id); m.teamAId?.let { teamOf[p] = it } }
            (m.lineupB).forEach { p -> games.getOrPut(p) { mutableSetOf() }.add(m.id); m.teamBId?.let { teamOf[p] = it } }
            // Lineups are updated by substitutions, so also credit a game to everyone who was subbed on or off.
            // (Points still come from m.events below; the log is only used for appearances.)
            for (e in m.log) {
                if (e.type != MatchEventType.SUB) continue
                listOfNotNull(e.outPlayerId, e.inPlayerId).forEach { p ->
                    games.getOrPut(p) { mutableSetOf() }.add(m.id)
                    e.teamId?.let { if (!teamOf.containsKey(p)) teamOf[p] = it }
                }
            }
            for (e in m.events) {
                val p = e.playerId ?: continue
                pts[p] = (pts[p] ?: 0) + e.points
                if (e.points == 3) threes[p] = (threes[p] ?: 0) + 1
                games.getOrPut(p) { mutableSetOf() }.add(m.id)
                teamOf[p] = e.teamId
            }
        }
        return t.players.filter { games.containsKey(it.id) || pts.containsKey(it.id) }.map {
            PlayerStat(it.id, teamOf[it.id] ?: t.teams.firstOrNull { tm -> it.id in tm.playerIds }?.id, pts[it.id] ?: 0, threes[it.id] ?: 0, games[it.id]?.size ?: 0)
        }.sortedWith(compareByDescending<PlayerStat> { it.points }.thenByDescending { it.threes })
    }
}
