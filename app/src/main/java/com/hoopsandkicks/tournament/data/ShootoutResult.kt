package com.hoopsandkicks.tournament.data

/** How a finished match's shootout ended, from the winner's side (e.g. 3–2). */
data class ShootoutResult(val winnerId: String, val winnerMade: Int, val loserMade: Int) {
    /** Attempts made by [teamId] (either side of the match). */
    fun madeBy(teamId: String?): Int = if (teamId == winnerId) winnerMade else loserMade
}

private val TallyRegex = Regex("""(\d+)\s*[–-]\s*(\d+)""")

/**
 * The shootout result of a finished match that was level at full time and won on the tie-breaker, else null.
 * Read from the log (the same way for the organizer and for live viewers); a match whose log is missing (saved
 * before logging existed) falls back to the tally in its "Won 2–1 on free throws" note.
 */
fun Match.shootoutResult(sport: Sport): ShootoutResult? {
    if (status != MatchStatus.FINISHED || bye || scoreA != scoreB) return null
    val winner = winnerId ?: return null
    val so = shootout(sport)
    if (so.winnerId == winner) {
        val aWon = winner == teamAId
        return ShootoutResult(winner, if (aWon) so.madeA else so.madeB, if (aWon) so.madeB else so.madeA)
    }
    val tally = TallyRegex.find(tieNote) ?: return null
    val w = tally.groupValues[1].toIntOrNull() ?: return null
    val l = tally.groupValues[2].toIntOrNull() ?: return null
    return ShootoutResult(winner, w, l)
}

/** e.g. "Won 3–2 on free throws". */
fun ShootoutResult.wonLine(sport: Sport): String = "Won $winnerMade–$loserMade on ${sport.shootoutAttemptPlural}"

/**
 * One-line explanation of a finished match's result for fixtures and results lists: "Red Hawks won 3–2 on free
 * throws" after a shootout, "Draw" for a level group/league match, any other stored note as is, else null.
 */
fun Match.resultNote(t: Tournament): String? {
    if (status != MatchStatus.FINISHED || bye) return null
    if (isDraw) return DRAW_NOTE
    val so = shootoutResult(t.sport)
    if (so != null) return "${t.teamName(so.winnerId)} won ${so.winnerMade}–${so.loserMade} on ${t.sport.shootoutAttemptPlural}"
    return tieNote.ifEmpty { null }
}

/** "1 point each" for a drawn group/league match under the tournament's points rule. */
fun Tournament.drawPointsText(): String = "$tiePoints point${if (tiePoints == 1) "" else "s"} each"

/** "Maya ✓ · Leo ✗ · Sam ✓" for one team's attempts; null when no attempt names a shooter. */
fun shooterNames(t: Tournament, attempts: List<ShootoutAttempt>): String? {
    if (attempts.none { it.playerId != null }) return null
    return attempts.joinToString(" · ") { (t.player(it.playerId)?.name ?: "Team") + if (it.made) " ✓" else " ✗" }
}
