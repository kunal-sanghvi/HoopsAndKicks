package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.ShootoutAttempt
import com.hoopsandkicks.tournament.data.ShootoutState
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.drawPointsText
import com.hoopsandkicks.tournament.data.resultNote
import com.hoopsandkicks.tournament.data.shooterNames
import com.hoopsandkicks.tournament.data.shootout
import com.hoopsandkicks.tournament.data.shootoutResult
import com.hoopsandkicks.tournament.data.wonLine

// Shootout (tie-breaker) and result views shared by the match screens, the hub, fixtures and the leaderboard.
// Everything here takes plain data (no view model, no repository), so it works for viewers and in previews.

private val ShotMissBg = Color(0xFFF7DEDC)
private val ShotMissFg = Color(0xFFB3261E)

private fun textOn(c: Color): Color = if (c.luminance() > 0.5f) Ink else Color.White

private fun sideColor(t: Tournament, id: String?): Color = t.team(id)?.let { teamColor(it.color) } ?: Mute

/** One team's shootout line: colour dot, name, one circle per attempt slot, and "made/taken". */
@Composable
internal fun ShootoutRow(name: String, color: Color, results: List<Boolean>, slots: Int, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 12.dp)
        Spacer(Modifier.width(10.dp))
        HText(name, 14.sp, FontWeight.Bold, if (dark) OnDark else Ink, Modifier.width(62.dp), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 0 until slots) ShotDot(results.getOrNull(i), dark)
        }
        Spacer(Modifier.width(10.dp))
        HText("${results.count { it }}/${results.size}", 14.sp, FontWeight.Bold, if (dark) MuteDark else Mute)
    }
}

/** true = made (green, white check), false = missed (red X), null = still to take (dashed outline). */
@Composable
internal fun ShotDot(made: Boolean?, dark: Boolean = false) {
    when (made) {
        true -> Box(Modifier.size(26.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, "Made", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        false -> Box(
            Modifier.size(26.dp).clip(CircleShape).background(ShotMissBg).border(BorderStroke(1.dp, ShotMissFg), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Missed", tint = ShotMissFg, modifier = Modifier.size(12.dp)) }
        null -> Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (dark) Navy2 else Surface).drawBehind {
                val w = 1.dp.toPx()
                drawCircle(
                    color = if (dark) MuteDark else Line,
                    radius = (size.minDimension - w) / 2f,
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                )
            }
        )
    }
}

/** Both teams' attempt rows. With [names], who took each attempt is listed under the team's dots. */
@Composable
internal fun ShootoutRows(t: Tournament, m: Match, so: ShootoutState, dark: Boolean = false, names: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        TeamShots(t, m.teamAId, so.attemptsA, so.slots, dark, names)
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (dark) Navy3 else Line))
        TeamShots(t, m.teamBId, so.attemptsB, so.slots, dark, names)
    }
}

@Composable
private fun TeamShots(t: Tournament, teamId: String?, attempts: List<ShootoutAttempt>, slots: Int, dark: Boolean, names: Boolean) {
    ShootoutRow(t.teamName(teamId), sideColor(t, teamId), attempts.map { it.made }, slots, dark)
    val line = if (names) shooterNames(t, attempts) else null
    if (line != null) {
        HText(line, 12.sp, FontWeight.Medium, if (dark) MuteDark else Mute, Modifier.padding(start = 22.dp, bottom = 6.dp))
    }
}

/**
 * Read-only view of a shootout in progress, for live viewers: attempt dots per team, who shoots next, sudden death,
 * and the last attempt. [dark] matches the navy hub card.
 */
@Composable
internal fun ShootoutLivePanel(t: Tournament, m: Match, dark: Boolean = false) {
    val so = m.shootout(t.sport)
    val mute = if (dark) MuteDark else Mute
    val winner = so.winnerId
    val nextTeam = if (so.nextIsA) m.teamAId else m.teamBId
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (dark) Navy2 else Surface)
            .border(BorderStroke(1.dp, if (dark) Navy3 else Line), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HText("${t.sport.shootoutName} · ${so.perTeam} each, alternating".uppercase(), 11.sp, FontWeight.Bold, mute)
        ShootoutRows(t, m, so, dark)
        val status = when {
            winner != null -> {
                val aWon = winner == m.teamAId
                val w = if (aWon) so.madeA else so.madeB
                val l = if (aWon) so.madeB else so.madeA
                "${t.teamName(winner)} won $w–$l on ${t.sport.shootoutAttemptPlural}"
            }
            so.suddenDeath -> "${t.teamName(nextTeam)} to shoot · sudden death"
            else -> "${t.teamName(nextTeam)} to shoot · attempt ${so.nextAttemptNumber} of ${so.perTeam}"
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(sideColor(t, winner ?: nextTeam), 10.dp)
            Spacer(Modifier.width(8.dp))
            HText(status, 14.sp, FontWeight.Bold, if (dark) AccentDark else Accent)
        }
        val last = so.all.lastOrNull()
        if (last != null) {
            val who = t.player(last.playerId)?.name ?: t.teamName(last.teamId)
            val what = if (last.made) t.sport.shootoutMadeLabel.lowercase() else "missed"
            HText("Last: $who ($what)", 12.sp, FontWeight.Medium, mute)
        }
        if (so.suddenDeath && winner == null) {
            HText(
                "Still level after ${so.perTeam} each: one ${t.sport.shootoutAttemptSingular} at a time until one side is ahead.",
                12.sp, FontWeight.Normal, mute
            )
        }
    }
}

/** Full-time breakdown of a match decided on the tie-breaker: tally, attempt dots and shooters. Nothing otherwise. */
@Composable
internal fun ShootoutResultCard(t: Tournament, m: Match) {
    val result = m.shootoutResult(t.sport) ?: return
    val so = m.shootout(t.sport)
    HCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (t.sport == Sport.FOOTBALL) Icons.Filled.SportsSoccer else Icons.Filled.SportsBasketball,
                null, tint = Accent, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            HText(t.sport.shootoutName, 14.sp, FontWeight.Bold, Ink, Modifier.weight(1f))
            DisplayText("${result.madeBy(m.teamAId)}–${result.madeBy(m.teamBId)}", 24.sp, Ink)
        }
        if (so.all.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ShootoutRows(t, m, so, names = true)
        }
        Spacer(Modifier.height(6.dp))
        HText(m.resultNote(t) ?: result.wonLine(t.sport), 13.sp, FontWeight.Bold, Accent)
    }
}

/** How a level full time was settled, shown under the full-time score: shootout tally, or the draw's points. */
@Composable
internal fun FullTimeTieBreakNote(t: Tournament, m: Match) {
    val result = m.shootoutResult(t.sport)
    if (result != null) {
        HText(result.wonLine(t.sport), 18.sp, FontWeight.Bold, AccentDark, align = TextAlign.Center)
        HText("Level ${m.scoreA}–${m.scoreB} at full time", 12.sp, FontWeight.Medium, MuteDark, align = TextAlign.Center)
    } else if (m.isDraw) {
        HText(
            if (m.awardsPoints) "Level at full time · ${t.drawPointsText()}" else "Level at full time",
            13.sp, FontWeight.Bold, AccentDark, align = TextAlign.Center
        )
    }
}

/** Navy full-time card: winner (or draw), the score, who played, and how a level score was settled. */
@Composable
internal fun FullTimeScoreCard(t: Tournament, m: Match) {
    val winnerColor = sideColor(t, m.winnerId)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Navy).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (m.isDraw) Badge("Draw", Navy3, OnDark)
        else Badge("Winner: ${t.teamName(m.winnerId)}", winnerColor, textOn(winnerColor))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            DisplayText("${m.scoreA}", 80.sp, if (m.isDraw || m.winnerId == m.teamAId) OnDark else MuteDark)
            HText("–", 26.sp, FontWeight.Medium, MuteDark)
            DisplayText("${m.scoreB}", 80.sp, if (m.isDraw || m.winnerId == m.teamBId) OnDark else MuteDark)
        }
        FullTimeTieBreakNote(t, m)
        HText("${t.teamName(m.teamAId)} vs ${t.teamName(m.teamBId)} · ${m.title(t)}".uppercase(), 12.sp, FontWeight.Bold, MuteDark, align = TextAlign.Center)
    }
}

/** Compact result line for fixtures and results lists ("Red Hawks won 3–2 on free throws", "Draw"). */
@Composable
internal fun ResultNoteLine(t: Tournament, m: Match, modifier: Modifier = Modifier) {
    val note = m.resultNote(t) ?: return
    val shootout = m.shootoutResult(t.sport) != null
    HText(note, 12.sp, if (shootout) FontWeight.Bold else FontWeight.Medium, if (shootout) Accent else Mute, modifier)
}

/** True when a read-only match card can expand to show a finished match's shootout attempt by attempt. */
internal fun Match.hasShootoutDetail(t: Tournament): Boolean =
    shootoutResult(t.sport) != null && shootout(t.sport).all.isNotEmpty()

/**
 * The tie-breaker part of a match card: the result line, and for viewers ([readOnly]) the live shootout while it is
 * running, or the finished shootout when [expanded] (a tap on the card toggles it).
 */
@Composable
internal fun MatchCardShootout(t: Tournament, m: Match, readOnly: Boolean, expanded: Boolean) {
    ResultNoteLine(t, m, Modifier.padding(top = 4.dp))
    if (!readOnly) return
    if (m.status == MatchStatus.TIEBREAK) {
        Spacer(Modifier.height(8.dp))
        ShootoutLivePanel(t, m)
    } else if (m.hasShootoutDetail(t)) {
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            ShootoutRows(t, m, m.shootout(t.sport), names = true)
        } else {
            HText("Tap to see the ${t.sport.shootoutNameLower}", 12.sp, FontWeight.Medium, Mute, Modifier.padding(top = 2.dp))
        }
    }
}
