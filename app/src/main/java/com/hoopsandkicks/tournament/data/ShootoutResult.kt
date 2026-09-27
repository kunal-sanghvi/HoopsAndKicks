package com.hoopsandkicks.tournament.data

/**
 * How a finished match's shootout ended, from the winner's side (e.g. 3–2). A group / league shootout that stayed
 * level is a draw: [winnerId] is null and both counts are the level tally (e.g. 2–2).
 */
data class ShootoutResult(val winnerId: String?, val winnerMade: Int, val loserMade: Int) {
    /** Attempts made by [teamId] (either side of the match). */
    fun madeBy(teamId: String?): Int = if (teamId == winnerId) winnerMade else loserMade
}

private val TallyRegex = Regex("""(\d+)\s*[–-]\s*(\d+)""")

/**
 * The shootout result of a finished match that was level at full time and went to the tie-breaker, else null.
 * Read from the log (the same way for the organizer and for live viewers); a match whose log is missing (saved
 * before logging existed) falls back to the tally in its "Won 2–1 on free throws" / "Draw · 2–2 on free throws" note.
 */
fun Match.shootoutResult(sport: Sport): ShootoutResult? {
    if (status != MatchStatus.FINISHED || bye || scoreA != scoreB) return null
    val winner = winnerId
    val so = shootout(sport)
    if (winner == null) {
        // Only the note recorded when a shootout ends level carries a tally; a plain "Draw" (e.g. ended level again
        // after "Edit scores") never went to the tie-breaker, even if an older shootout is still in the log.
        if (!tieNote.startsWith(DRAW_NOTE) || TallyRegex.find(tieNote) == null) return null
        if (so.endedLevel) return ShootoutResult(null, so.madeA, so.madeB)
    } else if (so.winnerId == winner) {
        val aWon = winner == teamAId
        return ShootoutResult(winner, if (aWon) so.madeA else so.madeB, if (aWon) so.madeB else so.madeA)
    }
    val tally = TallyRegex.find(tieNote) ?: return null
    val w = tally.groupValues[1].toIntOrNull() ?: return null
    val l = tally.groupValues[2].toIntOrNull() ?: return null
    return ShootoutResult(winner, w, l)
}

/** e.g. "Won 3–2 on free throws", or "Draw · 2–2 on free throws" when the shootout ended level. */
fun ShootoutResult.tallyLine(sport: Sport): String =
    if (winnerId == null) shootoutDrawNote(sport, winnerMade, loserMade)
    else "Won $winnerMade–$loserMade on ${sport.shootoutAttemptPlural}"

/**
 * One-line explanation of a finished match's result for fixtures and results lists: "Red Hawks won 3–2 on free
 * throws" or "Draw · 2–2 on free throws" after a shootout, "Draw" for a level group/league match, any other stored
 * note as is, else null.
 */
fun Match.resultNote(t: Tournament): String? {
    if (status != MatchStatus.FINISHED || bye) return null
    val so = shootoutResult(t.sport)
    if (so != null) {
        val w = so.winnerId ?: return so.tallyLine(t.sport)
        return "${t.teamName(w)} won ${so.winnerMade}–${so.loserMade} on ${t.sport.shootoutAttemptPlural}"
    }
    if (isDraw) return DRAW_NOTE
    return tieNote.ifEmpty { null }
}

/** "1 point each" for a drawn group/league match under the tournament's points rule. */
fun Tournament.drawPointsText(): String = "$tiePoints point${if (tiePoints == 1) "" else "s"} each"

/** "Maya ✓ · Leo ✗ · Sam ✓" for one team's attempts; null when no attempt names a shooter. */
fun shooterNames(t: Tournament, attempts: List<ShootoutAttempt>): String? {
    if (attempts.none { it.playerId != null }) return null
    return attempts.joinToString(" · ") { (t.player(it.playerId)?.name ?: "Team") + if (it.made) " ✓" else " ✗" }
}
