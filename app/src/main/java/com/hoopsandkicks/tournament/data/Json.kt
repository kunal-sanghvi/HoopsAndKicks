package com.hoopsandkicks.tournament.data

import org.json.JSONArray
import org.json.JSONObject

/** Hand-written JSON mapping so persistence needs no annotation processing. */
object Json {

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    private fun strList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) out.add(a.getString(i))
        return out
    }

    private fun toArr(list: List<String>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(it) }
        return a
    }

    fun toJson(t: Tournament): JSONObject {
        val o = JSONObject()
        o.put("id", t.id)
        o.put("name", t.name)
        o.put("startAt", t.startAt)
        o.put("endAt", t.endAt)
        o.put("courts", t.courts)
        o.put("matchBufferMin", t.matchBufferMin)
        o.put("venue", t.venue)
        o.put("playerTarget", t.playerTarget)
        o.put("teamTarget", t.teamTarget)
        o.put("sport", t.sport.name)
        o.put("algorithm", t.algorithm.name)
        o.put("groups", t.groups)
        o.put("advance", t.advance)
        o.put("rrSemis", t.rrSemis)
        o.put("rrFinal", t.rrFinal)
        o.put("status", t.status.name)
        o.put("draftStep", t.draftStep)
        o.put("createdAt", t.createdAt)
        if (t.championId != null) o.put("championId", t.championId)
        if (t.roomCode != null) o.put("roomCode", t.roomCode)

        val pa = JSONArray()
        t.players.forEach { p ->
            pa.put(JSONObject().put("id", p.id).put("name", p.name).put("position", p.position).put("skill", p.skill))
        }
        o.put("players", pa)

        val ta = JSONArray()
        t.teams.forEach { tm ->
            ta.put(JSONObject().put("id", tm.id).put("name", tm.name).put("color", tm.color).put("playerIds", toArr(tm.playerIds)))
        }
        o.put("teams", ta)

        val f = t.format
        o.put(
            "format",
            JSONObject()
                .put("type", f.type.name)
                .put("periodMin", f.periodMin)
                .put("breakMin", f.breakMin)
                .put("allow1", f.allow1)
                .put("allow2", f.allow2)
                .put("allow3", f.allow3)
                .put("finalsDifferent", f.finalsDifferent)
                .put("finalsPeriodMin", f.finalsPeriodMin)
                .put("semisDifferent", f.semisDifferent)
                .put("semisPeriodMin", f.semisPeriodMin)
        )

        val ma = JSONArray()
        t.matches.forEach { ma.put(matchToJson(it)) }
        o.put("matches", ma)
        return o
    }

    private fun matchToJson(m: Match): JSONObject {
        val o = JSONObject()
        o.put("id", m.id)
        o.put("number", m.number)
        o.put("stageIndex", m.stageIndex)
        o.put("stage", m.stage)
        o.put("stageType", m.stageType.name)
        o.put("group", m.group)
        o.put("round", m.round)
        o.put("label", m.label)
        if (m.teamAId != null) o.put("teamAId", m.teamAId)
        if (m.teamBId != null) o.put("teamBId", m.teamBId)
        o.put("scoreA", m.scoreA)
        o.put("scoreB", m.scoreB)
        o.put("status", m.status.name)
        o.put("isFinal", m.isFinal)
        o.put("bye", m.bye)
        o.put("period", m.period)
        o.put("remainingSec", m.remainingSec)
        o.put("breakRemainingSec", m.breakRemainingSec)
        o.put("lineupA", toArr(m.lineupA))
        o.put("lineupB", toArr(m.lineupB))
        if (m.winnerId != null) o.put("winnerId", m.winnerId)
        o.put("tieNote", m.tieNote)
        val ea = JSONArray()
        m.events.forEach { e ->
            val eo = JSONObject()
            eo.put("id", e.id)
            eo.put("teamId", e.teamId)
            if (e.playerId != null) eo.put("playerId", e.playerId)
            eo.put("points", e.points)
            eo.put("period", e.period)
            eo.put("clock", e.clock)
            eo.put("createdAt", e.createdAt)
            ea.put(eo)
        }
        o.put("events", ea)
        val la = JSONArray()
        m.log.forEach { la.put(eventToJson(it)) }
        o.put("log", la)
        o.put("clockRunning", m.clockRunning)
        o.put("clockEpochMs", m.clockEpochMs)
        if (m.scheduledAt != null) o.put("scheduledAt", m.scheduledAt)
        if (m.court != null) o.put("court", m.court)
        if (m.pinned) o.put("pinned", true)
        return o
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null

    fun eventToJson(e: MatchEvent): JSONObject {
        val o = JSONObject()
        o.put("id", e.id)
        o.put("seq", e.seq)
        o.put("type", e.type.name)
        o.put("epochMs", e.epochMs)
        if (e.teamId != null) o.put("teamId", e.teamId)
        if (e.playerId != null) o.put("playerId", e.playerId)
        if (e.points != null) o.put("points", e.points)
        if (e.outPlayerId != null) o.put("outPlayerId", e.outPlayerId)
        if (e.inPlayerId != null) o.put("inPlayerId", e.inPlayerId)
        if (e.voidsSeq != null) o.put("voidsSeq", e.voidsSeq)
        if (e.note != null) o.put("note", e.note)
        if (e.clockSec != null) o.put("clockSec", e.clockSec)
        return o
    }

    /** Returns null for malformed entries or event types this build does not know. */
    fun eventFromJson(o: JSONObject): MatchEvent? = try {
        MatchEvent(
            id = o.getString("id"),
            seq = o.getInt("seq"),
            type = MatchEventType.valueOf(o.getString("type")),
            epochMs = o.optLong("epochMs", 0L),
            teamId = if (o.has("teamId")) o.str("teamId") else null,
            playerId = if (o.has("playerId")) o.str("playerId") else null,
            points = o.optIntOrNull("points"),
            outPlayerId = if (o.has("outPlayerId")) o.str("outPlayerId") else null,
            inPlayerId = if (o.has("inPlayerId")) o.str("inPlayerId") else null,
            voidsSeq = o.optIntOrNull("voidsSeq"),
            note = if (o.has("note")) o.str("note") else null,
            clockSec = o.optIntOrNull("clockSec")
        )
    } catch (e: Exception) {
        null
    }

    fun fromJson(o: JSONObject): Tournament {
        val players = ArrayList<Player>()
        val pa = o.optJSONArray("players")
        if (pa != null) for (i in 0 until pa.length()) {
            val p = pa.getJSONObject(i)
            players.add(Player(p.getString("id"), p.getString("name"), p.optString("position", "Guard"), p.optInt("skill", 3)))
        }
        val teams = ArrayList<Team>()
        val ta = o.optJSONArray("teams")
        if (ta != null) for (i in 0 until ta.length()) {
            val t = ta.getJSONObject(i)
            teams.add(Team(t.getString("id"), t.getString("name"), t.optInt("color", 0), strList(t.optJSONArray("playerIds"))))
        }
        val fo = o.optJSONObject("format")
        val format = if (fo == null) GameFormat() else GameFormat(
            type = FormatType.valueOf(fo.optString("type", "HALVES")),
            periodMin = fo.optInt("periodMin", 12),
            breakMin = fo.optInt("breakMin", 5).coerceAtLeast(1),
            allow1 = fo.optBoolean("allow1", true),
            allow2 = fo.optBoolean("allow2", true),
            allow3 = fo.optBoolean("allow3", true),
            finalsDifferent = fo.optBoolean("finalsDifferent", false),
            finalsPeriodMin = fo.optInt("finalsPeriodMin", 15),
            semisDifferent = fo.optBoolean("semisDifferent", false),
            semisPeriodMin = fo.optInt("semisPeriodMin", 15)
        )
        val matches = ArrayList<Match>()
        val ma = o.optJSONArray("matches")
        if (ma != null) for (i in 0 until ma.length()) matches.add(matchFromJson(ma.getJSONObject(i)))

        return Tournament(
            id = o.getString("id"),
            name = o.optString("name", "Tournament"),
            startAt = o.optLong("startAt", 0L),
            endAt = o.optLong("endAt", 0L),
            courts = o.optInt("courts", 1).coerceAtLeast(1),
            matchBufferMin = o.optInt("matchBufferMin", 10).coerceAtLeast(0),
            venue = o.optString("venue", ""),
            playerTarget = o.optInt("playerTarget", 8),
            teamTarget = o.optInt("teamTarget", 2),
            players = players,
            teams = teams,
            sport = try { Sport.valueOf(o.optString("sport", "BASKETBALL")) } catch (e: Exception) { Sport.BASKETBALL },
            algorithm = Algorithm.valueOf(o.optString("algorithm", "GROUP_KO")),
            groups = o.optInt("groups", 2),
            advance = o.optInt("advance", 2),
            rrSemis = o.optBoolean("rrSemis", false),
            // Tournaments saved before the option existed always played a final.
            rrFinal = o.optBoolean("rrFinal", true),
            format = format,
            matches = matches,
            status = TStatus.valueOf(o.optString("status", "DRAFT")),
            draftStep = o.optInt("draftStep", 1),
            championId = o.str("championId"),
            createdAt = o.optLong("createdAt", 0L),
            roomCode = if (o.has("roomCode")) o.str("roomCode") else null
        )
    }

    private fun matchFromJson(o: JSONObject): Match {
        val events = ArrayList<ScoreEvent>()
        val ea = o.optJSONArray("events")
        if (ea != null) for (i in 0 until ea.length()) {
            val e = ea.getJSONObject(i)
            events.add(
                ScoreEvent(
                    id = e.getString("id"),
                    teamId = e.getString("teamId"),
                    playerId = e.str("playerId"),
                    points = e.getInt("points"),
                    period = e.optString("period", ""),
                    clock = e.optString("clock", ""),
                    createdAt = e.optLong("createdAt", 0L)
                )
            )
        }
        val log = ArrayList<MatchEvent>()
        val la = o.optJSONArray("log")
        if (la != null) for (i in 0 until la.length()) {
            val lo = la.optJSONObject(i) ?: continue
            eventFromJson(lo)?.let { log.add(it) }
        }
        return Match(
            id = o.getString("id"),
            number = o.optInt("number", 0),
            stageIndex = o.optInt("stageIndex", 0),
            stage = o.optString("stage", ""),
            stageType = StageType.valueOf(o.optString("stageType", "LEAGUE")),
            group = o.optString("group", ""),
            round = o.optInt("round", 1),
            label = o.optString("label", ""),
            teamAId = o.str("teamAId"),
            teamBId = o.str("teamBId"),
            scoreA = o.optInt("scoreA", 0),
            scoreB = o.optInt("scoreB", 0),
            status = MatchStatus.valueOf(o.optString("status", "SCHEDULED")),
            isFinal = o.optBoolean("isFinal", false),
            bye = o.optBoolean("bye", false),
            period = o.optInt("period", 1),
            remainingSec = o.optInt("remainingSec", 0),
            breakRemainingSec = o.optInt("breakRemainingSec", 0),
            lineupA = strList(o.optJSONArray("lineupA")),
            lineupB = strList(o.optJSONArray("lineupB")),
            events = events,
            winnerId = o.str("winnerId"),
            tieNote = o.optString("tieNote", ""),
            log = log,
            clockRunning = o.optBoolean("clockRunning", false),
            clockEpochMs = o.optLong("clockEpochMs", 0L),
            scheduledAt = if (o.has("scheduledAt") && !o.isNull("scheduledAt")) o.getLong("scheduledAt") else null,
            court = o.optIntOrNull("court"),
            pinned = o.optBoolean("pinned", false)
        )
    }
}
