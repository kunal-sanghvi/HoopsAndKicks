package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopsandkicks.tournament.data.Algorithm
import com.hoopsandkicks.tournament.data.Leaderboards
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.StageType
import com.hoopsandkicks.tournament.data.StandingRow
import com.hoopsandkicks.tournament.data.Standings
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Tournament

private fun col(t: Tournament, id: String?): Color = t.team(id)?.let { teamColor(it.color) } ?: Mute

@Composable
private fun StageChips(names: List<String>, selected: Int, onSelect: (Int) -> Unit, firstLabel: String) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip(firstLabel, selected == 0, { onSelect(0) })
        names.forEachIndexed { i, n -> Chip(n, selected == i + 1, { onSelect(i + 1) }) }
    }
}

// ======================= STANDINGS =======================

@Composable
fun StandingsTab(t: Tournament) {
    val stages = t.stages()
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val cur = sel.coerceIn(0, stages.size)
    Column(Modifier.fillMaxSize()) {
        TopBar("Leaderboard", null)
        StageChips(stages.map { it.second }, cur, { sel = it }, "Overall")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (cur == 0) OverallContent(t) else StageContent(t, stages[cur - 1].first)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OverallContent(t: Tournament) {
    if (t.matches.isEmpty()) {
        HText("Standings appear once matches are played.", 13.sp, FontWeight.Medium, Mute)
        return
    }
    HText("Wins earn ${Standings.WIN_POINTS} points in each stage and are added up. Ranked by furthest stage reached, then total, then point difference.", 12.sp, FontWeight.Normal, Mute)
    val rows = Leaderboards.overall(t)
    HCard(padding = 10.dp) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Cell("#", 22.dp, header = true)
            Cell("Team", null, header = true, align = TextAlign.Start)
            Cell("Grp", 34.dp, header = true)
            Cell("KO", 34.dp, header = true)
            Cell("Final", 38.dp, header = true)
            Cell("Tot", 40.dp, header = true)
        }
        rows.forEachIndexed { i, r ->
            val hi = i == 0 && t.status == TStatus.COMPLETED
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(if (hi) GreenSoft else Color.Transparent).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cell("${i + 1}", 22.dp, bold = true)
                Column(Modifier.weight(1f)) {
                    TeamPill(t.teamName(r.teamId), col(t, r.teamId))
                    HText(r.statusText, 11.sp, FontWeight.Medium, Mute, Modifier.padding(start = 20.dp), maxLines = 1)
                }
                Cell("${r.groupPts}", 34.dp)
                Cell("${r.koPts}", 34.dp)
                Cell("${r.finalPts}", 38.dp)
                Cell("${r.total}", 40.dp, bold = true)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    width: Dp?,
    header: Boolean = false,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Center
) {
    val m = if (width != null) Modifier.width(width) else Modifier.weight(1f)
    HText(
        text, if (header) 12.sp else 15.sp,
        if (header || bold) FontWeight.Bold else FontWeight.Normal,
        if (header) Mute else Ink, m, align, 1
    )
}

@Composable
private fun StageContent(t: Tournament, stageIndex: Int) {
    val ms = t.matches.filter { it.stageIndex == stageIndex }
    val real = ms.filter { !it.bye }
    val type = ms.firstOrNull()?.stageType
    when (type) {
        StageType.GROUP -> {
            val adv = Scheduler.clampedAdvance(t)
            real.map { it.group }.distinct().sorted().forEach { g ->
                val gm = real.filter { it.group == g }
                val ids = gm.flatMap { listOfNotNull(it.teamAId, it.teamBId) }.distinct()
                HCard(padding = 12.dp) {
                    DisplayText(g, 22.sp, Ink, Modifier.padding(start = 4.dp, bottom = 2.dp))
                    StandingsTable(t, Standings.compute(ids, gm), if (t.algorithm == Algorithm.GROUP_KO) adv else 0)
                }
            }
            if (t.algorithm == Algorithm.GROUP_KO) {
                HText("Top $adv from each group advance · ties split by head-to-head, then point difference.", 12.sp, FontWeight.Normal, Mute)
            }
        }
        StageType.LEAGUE -> {
            HCard(padding = 12.dp) {
                StandingsTable(t, Standings.compute(t.teams.map { it.id }, ms), 0)
            }
            HText("Ties split by head-to-head, then point difference.", 12.sp, FontWeight.Normal, Mute)
        }
        StageType.KNOCKOUT -> {
            val rounds = real.map { it.round }.distinct().sorted()
            rounds.forEach { r ->
                if (rounds.size > 1) HText("Round $r", 13.sp, FontWeight.Bold, Mute)
                real.filter { it.round == r }.forEach { m -> ResultCard(t, m) }
            }
            ms.filter { it.bye }.forEach { m -> HText("${t.teamName(m.teamAId)} advances on a bye.", 12.sp, FontWeight.Medium, Mute) }
            if (t.status == TStatus.COMPLETED && real.any { it.isFinal }) {
                HCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = Gold, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            HText("Champion", 12.sp, FontWeight.Medium, Mute)
                            HText(t.teamName(t.championId), 16.sp, FontWeight.Bold)
                        }
                    }
                }
            }
        }
        null -> HText("Nothing here yet.", 13.sp, FontWeight.Medium, Mute)
    }
}

@Composable
private fun StandingsTable(t: Tournament, rows: List<StandingRow>, qualify: Int) {
    Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell("#", 22.dp, header = true)
        Cell("Team", null, header = true, align = TextAlign.Start)
        Cell("P", 24.dp, header = true)
        Cell("W", 24.dp, header = true)
        Cell("L", 24.dp, header = true)
        Cell("PD", 38.dp, header = true)
        Cell("Pts", 34.dp, header = true)
    }
    rows.forEachIndexed { i, r ->
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
                .background(if (i < qualify) GreenSoft else Color.Transparent).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cell("${i + 1}", 22.dp, bold = true)
            Box(Modifier.weight(1f)) { TeamPill(t.teamName(r.teamId), col(t, r.teamId)) }
            Cell("${r.played}", 24.dp)
            Cell("${r.wins}", 24.dp)
            Cell("${r.losses}", 24.dp)
            Cell(if (r.pd > 0) "+${r.pd}" else "${r.pd}", 38.dp)
            Cell("${r.points}", 34.dp, bold = true)
        }
    }
}

@Composable
private fun ResultCard(t: Tournament, m: Match) {
    val fin = m.status == MatchStatus.FINISHED
    Column {
        HText(m.label.ifEmpty { m.stage }.uppercase(), 12.sp, FontWeight.Bold, Mute, Modifier.padding(start = 4.dp, bottom = 6.dp))
        HCard(padding = 4.dp) {
            listOf(true, false).forEachIndexed { i, isA ->
                val id = if (isA) m.teamAId else m.teamBId
                val score = if (isA) m.scoreA else m.scoreB
                val won = fin && m.winnerId == id
                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(col(t, id), 10.dp)
                    Spacer(Modifier.width(8.dp))
                    HText(t.teamName(id), 14.sp, if (won) FontWeight.Bold else FontWeight.Medium, Ink, Modifier.weight(1f), maxLines = 1)
                    HText(if (m.status == MatchStatus.SCHEDULED) "–" else "$score", 15.sp, if (won) FontWeight.Bold else FontWeight.Medium)
                }
                if (i == 0) Divider1()
            }
        }
        if (fin && m.tieNote.isNotEmpty()) HText(m.tieNote, 12.sp, FontWeight.Medium, Mute, Modifier.padding(start = 4.dp, top = 4.dp))
        if (m.status != MatchStatus.SCHEDULED && !fin) HText(
            if (m.status == MatchStatus.TIEBREAK) "Tied, ${t.sport.shootoutNameLower} in progress" else "In progress",
            12.sp, FontWeight.Medium, Accent, Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

// ======================= TEAMS =======================

@Composable
fun TeamsTab(t: Tournament) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Teams", null)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            t.teams.forEach { tm ->
                HCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(teamColor(tm.color)), contentAlignment = Alignment.Center) {
                            DisplayText(tm.name.take(1).uppercase(), 18.sp, Color.White, align = TextAlign.Center)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            HText(tm.name, 17.sp, FontWeight.Bold)
                            HText("${tm.playerIds.size} players", 12.sp, FontWeight.Normal, Mute)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    tm.playerIds.forEach { pid ->
                        val p = t.player(pid)
                        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                            HText(p?.name ?: "?", 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f), maxLines = 1)
                            HText(p?.position ?: "", 12.sp, FontWeight.Normal, Mute)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ======================= PLAYER STATS =======================

@Composable
fun StatsTab(t: Tournament) {
    val stages = t.stages()
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val cur = sel.coerceIn(0, stages.size)
    val stats = Leaderboards.players(t, if (cur == 0) null else stages[cur - 1].first)
    Column(Modifier.fillMaxSize()) {
        TopBar("Top scorers", null)
        StageChips(stages.map { it.second }, cur, { sel = it }, "Overall")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stats.isEmpty() || stats.all { it.points == 0 }) {
                HText("Player stats appear once baskets are credited to players.", 13.sp, FontWeight.Medium, Mute)
            } else {
                val lead = stats.first()
                HCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            HText("Scoring leader", 12.sp, FontWeight.Medium, Mute)
                            HText("${t.player(lead.playerId)?.name ?: "?"} · ${t.teamName(lead.teamId)}", 17.sp, FontWeight.Bold, maxLines = 1)
                        }
                        DisplayText("${lead.points}", 40.sp)
                    }
                }
                HCard(padding = 10.dp) {
                    Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cell("#", 22.dp, header = true)
                        Cell("Player", null, header = true, align = TextAlign.Start)
                        Cell("G", 28.dp, header = true)
                        Cell("3PT", 32.dp, header = true)
                        Cell("PPG", 44.dp, header = true)
                        Cell("Pts", 40.dp, header = true)
                    }
                    stats.take(30).forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (i == 0) GreenSoft else Color.Transparent).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cell("${i + 1}", 22.dp, bold = true)
                            Column(Modifier.weight(1f)) {
                                HText(t.player(s.playerId)?.name ?: "?", 15.sp, FontWeight.Bold, maxLines = 1)
                                HText(t.teamName(s.teamId), 11.sp, FontWeight.Medium, Mute, maxLines = 1)
                            }
                            Cell("${s.games}", 28.dp)
                            Cell("${s.threes}", 32.dp)
                            Cell("%.1f".format(s.ppg), 44.dp)
                            Cell("${s.points}", 40.dp, bold = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
