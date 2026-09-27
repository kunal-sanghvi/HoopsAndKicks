package com.hoopsandkicks.tournament.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hoopsandkicks.tournament.data.FormatType
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.shootout
import com.hoopsandkicks.tournament.data.shootoutResult

private fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Ink else Color.White

private fun pointsByPlayer(m: Match): Map<String, Int> {
    val res = LinkedHashMap<String, Int>()
    m.events.forEach { e -> e.playerId?.let { res[it] = (res[it] ?: 0) + e.points } }
    return res
}

private fun teamColorOf(t: Tournament, id: String?): Color = t.team(id)?.let { teamColor(it.color) } ?: Mute

// ======================= ROUTER =======================

@Composable
fun MatchRoute(tid: String, mid: String, onExit: () -> Unit) {
    val vm: LiveViewModel = viewModel(
        key = "live-$mid",
        factory = viewModelFactory { initializer { LiveViewModel(tid, mid) } }
    )
    val t = observeTournament(tid)
    val m = t?.match(mid)
    LaunchedEffect(m == null) { if (m == null) onExit() }
    if (t == null || m == null) return
    when (m.status) {
        MatchStatus.SCHEDULED -> Screen {
            TopBar("Match ${m.number}", onExit)
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                HText("This match has not been started yet. Open it from Fixtures and tap Start.", 14.sp, FontWeight.Medium, Mute, align = TextAlign.Center)
            }
        }
        MatchStatus.LIVE -> LiveContent(t, m, vm, onExit)
        MatchStatus.BREAK -> HalftimeContent(t, m, vm, onExit)
        MatchStatus.TIEBREAK -> TieBreakerContent(t, m, vm)
        MatchStatus.FINISHED -> SummaryContent(t, m, vm, onExit)
    }
}

// ======================= LIVE =======================

@Composable
private fun LiveContent(t: Tournament, m: Match, vm: LiveViewModel, onExit: () -> Unit) {
    val f = t.format.forMatch(m)
    val halves = f.type == FormatType.HALVES
    var showExit by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    BackHandler { showExit = true }

    val periodFullMs = f.periodMin * 60_000L
    val endLabel = if (halves && m.period < f.periods()) "End half" else "End game"
    val options = f.pointOptions()

    Screen(Navy) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Filled.Close, "Exit match", { showExit = true }, dark = true)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        HText(m.title(t).uppercase(), 12.sp, FontWeight.Bold, MuteDark, align = TextAlign.Center, maxLines = 1)
                    }
                    Box {
                        IconCircle(
                            if (t.sport == Sport.FOOTBALL) Icons.Filled.MoreVert else Icons.Filled.Tune,
                            "Match options", { menu = true }, dark = true
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("End match now") }, onClick = { menu = false; showEnd = true })
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Navy3).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        HText(m.periodName(halves), 13.sp, FontWeight.Bold, OnDark)
                    }
                    DisplayText(formatClock(vm.clockMs), 96.sp, if (vm.timeUp) AccentDark else OnDark)
                    if (vm.timeUp) HText("Time! End the period to continue.", 13.sp, FontWeight.Bold, AccentDark)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            if (vm.running) "Pause" else if (vm.clockMs < periodFullMs) "Resume" else "Start",
                            { vm.togglePause() },
                            Modifier.height(44.dp),
                            icon = if (vm.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            dark = true,
                            enabled = vm.clockMs > 0L
                        )
                        SecondaryButton(
                            endLabel, { vm.endPeriod() }, Modifier.height(44.dp), icon = Icons.Filled.ChevronRight, dark = true,
                            enabled = vm.running || vm.timeUp
                        )
                    }
                }
                // Paused (but not "time up", which still needs End half/End game to be tappable) locks
                // everything except Sub: an admin who steps away mid-play shouldn't be able to add a
                // basket or end the period from a paused clock — only swap a player.
                val scoringLocked = !vm.running && !vm.timeUp
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamScorePanel(t, m, true, options, vm, Modifier.weight(1f), scoringLocked)
                    TeamScorePanel(t, m, false, options, vm, Modifier.weight(1f), scoringLocked)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Navy2).padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    HText("Recent ${t.sport.scoreEventPlural}", 12.sp, FontWeight.Bold, MuteDark, Modifier.padding(top = 4.dp, bottom = 4.dp))
                    if (m.events.isEmpty()) HText("No ${t.sport.scoreEventPlural} yet.", 13.sp, FontWeight.Normal, MuteDark)
                    m.events.takeLast(3).reversed().forEach { e ->
                        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(teamColorOf(t, e.teamId), 10.dp)
                            Spacer(Modifier.width(10.dp))
                            HText(
                                "${t.player(e.playerId)?.name ?: t.teamName(e.teamId)} " + if (t.sport == Sport.FOOTBALL) "⚽" else "+${e.points}",
                                14.sp, FontWeight.Medium, OnDark, Modifier.weight(1f), maxLines = 1
                            )
                            PeriodBadge(e.period)
                            Spacer(Modifier.width(6.dp))
                            HText(e.clock, 13.sp, FontWeight.Medium, MuteDark)
                        }
                    }
                }
                BottomBar(dark = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Undo last", { vm.undo() }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.Undo, dark = true, enabled = m.events.isNotEmpty())
                        SecondaryButton("End match", { showEnd = true }, Modifier.weight(1f), icon = Icons.Filled.Flag, dark = true)
                    }
                }
            }

            val sheetForA = vm.sheetForA
            if (sheetForA != null) {
                ScorerSheet(t, m, sheetForA, vm.sheetPoints, vm)
            }
            val subForA = vm.subForA
            if (subForA != null) {
                SubSheet(t, m, subForA, vm)
            }
        }
    }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("Leave match?") },
            text = { Text("The clock pauses and everything is saved. You can resume from Fixtures.") },
            confirmButton = { TextButton(onClick = { showExit = false; vm.pause(); onExit() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { showExit = false }) { Text("Stay") } }
        )
    }
    if (showEnd) {
        AlertDialog(
            onDismissRequest = { showEnd = false },
            title = { Text("End match now?") },
            text = {
                Text(
                    when {
                        m.scoreA != m.scoreB -> "The leading team will be recorded as the winner."
                        m.awardsPoints -> "Scores are level, so the match will be recorded as a draw."
                        else -> "Scores are level, so it goes straight to a ${t.sport.shootoutNameLower}."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { showEnd = false; vm.endMatchNow() }) { Text("End match") } },
            dismissButton = { TextButton(onClick = { showEnd = false }) { Text("Keep playing") } }
        )
    }
}

@Composable
private fun TeamScorePanel(t: Tournament, m: Match, isA: Boolean, options: List<Int>, vm: LiveViewModel, modifier: Modifier, locked: Boolean) {
    val teamId = if (isA) m.teamAId else m.teamBId
    val color = teamColorOf(t, teamId)
    val score = if (isA) m.scoreA else m.scoreB
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Navy2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(color))
            Spacer(Modifier.height(8.dp))
            HText(t.teamName(teamId).uppercase(), 15.sp, FontWeight.Bold, OnDark, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
            DisplayText("$score", 76.sp, OnDark)
            // Filled dark "Sub" pill with a leading icon (LiveMatch / LiveMatchFootball designs).
            Row(
                Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp)
                    .height(36.dp).clip(RoundedCornerShape(18.dp)).background(Navy3)
                    .clickable { vm.openSub(isA) }.padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (t.sport == Sport.FOOTBALL) Icons.Filled.Shuffle else Icons.Filled.ChevronRight,
                    null, tint = OnDark, modifier = Modifier.size(14.dp)
                )
                HText("Sub", 12.sp, FontWeight.Bold, OnDark, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { p ->
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (locked) color.copy(alpha = 0.35f) else color)
                        .clickable(enabled = !locked) { vm.openSheet(isA, p) },
                    contentAlignment = Alignment.Center
                ) {
                    HText(
                        if (t.sport == Sport.FOOTBALL && p == 1) "Goal" else "+$p",
                        20.sp, FontWeight.Bold, if (locked) onColor(color).copy(alpha = 0.5f) else onColor(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScorerSheet(t: Tournament, m: Match, forA: Boolean, points: Int, vm: LiveViewModel) {
    val teamId = if (forA) m.teamAId else m.teamBId
    val team = t.team(teamId)
    val color = teamColorOf(t, teamId)
    val lineup = if (forA) m.lineupA else m.lineupB
    val roster = team?.playerIds ?: emptyList()
    val bench = roster.filter { it !in lineup }
    val pts = pointsByPlayer(m)
    BackHandler { vm.closeSheet() }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { vm.closeSheet() })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Bg)
                .clickable(enabled = false) {}.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(if (t.sport == Sport.FOOTBALL) "⚽" else "+$points", color, onColor(color))
                Spacer(Modifier.width(10.dp))
                DisplayText("Who scored for ${t.teamName(teamId)}?", 24.sp, Ink)
            }
            Column(
                Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (roster.isEmpty()) HText("This team has no players. Credit the ${t.sport.scoreEventSingular} to the team.", 13.sp, FontWeight.Normal, Mute)
                (lineup + bench).forEach { pid ->
                    val onBench = pid in bench
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
                            .clickable { vm.score(forA, pid, points) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val name = t.player(pid)?.name ?: "?"
                        Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                            HText(name.take(1).uppercase(), 14.sp, FontWeight.Bold, onColor(color))
                        }
                        Spacer(Modifier.width(12.dp))
                        HText(name + if (onBench) " (bench)" else "", 16.sp, FontWeight.Bold, Ink, Modifier.weight(1f), maxLines = 1)
                        HText("${pts[pid] ?: 0} ${if (t.sport == Sport.FOOTBALL) "goals" else "pts"}", 13.sp, FontWeight.Medium, Mute)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Team ${t.sport.scoreEventSingular} (no player)", { vm.score(forA, null, points) }, Modifier.weight(1f))
                SecondaryButton("Cancel", { vm.closeSheet() })
            }
        }
    }
}

// ======================= SUBSTITUTION SHEET =======================

@Composable
private fun SubSheet(t: Tournament, m: Match, forA: Boolean, vm: LiveViewModel) {
    val teamId = if (forA) m.teamAId else m.teamBId
    val color = teamColorOf(t, teamId)
    val lineup = if (forA) m.lineupA else m.lineupB
    val roster = t.team(teamId)?.playerIds ?: emptyList()
    val bench = roster.filter { it !in lineup }
    var outId by remember(forA) { mutableStateOf<String?>(null) }
    var inId by remember(forA) { mutableStateOf<String?>(null) }
    BackHandler { vm.closeSub() }

    @Composable
    fun pickRow(pid: String, selected: Boolean, onPick: () -> Unit) {
        val name = t.player(pid)?.name ?: "?"
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) AccentSoft else Surface)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Line), RoundedCornerShape(14.dp))
                .clickable(onClick = onPick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                HText(name.take(1).uppercase(), 13.sp, FontWeight.Bold, onColor(color))
            }
            Spacer(Modifier.width(12.dp))
            HText(name, 16.sp, FontWeight.Bold, Ink, Modifier.weight(1f), maxLines = 1)
            if (selected) Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { vm.closeSub() })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Bg)
                .clickable(enabled = false) {}.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge("Sub", color, onColor(color))
                Spacer(Modifier.width(10.dp))
                DisplayText("Substitution · ${t.teamName(teamId)}", 24.sp, Ink)
            }
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HText("Coming off", 13.sp, FontWeight.Bold, Mute)
                if (lineup.isEmpty()) HText("Nobody from this team is on court.", 13.sp, FontWeight.Normal, Mute)
                lineup.forEach { pid -> pickRow(pid, outId == pid) { outId = pid } }
                Spacer(Modifier.height(4.dp))
                HText("Coming on", 13.sp, FontWeight.Bold, Mute)
                if (bench.isEmpty()) HText("Nobody left on the bench.", 13.sp, FontWeight.Normal, Mute)
                bench.forEach { pid -> pickRow(pid, inId == pid) { inId = pid } }
            }
            val o = outId
            val i = inId
            val ready = o != null && i != null && o in lineup && i in bench
            PrimaryButton(
                if (o != null && i != null) "Confirm: ${t.player(i)?.name ?: "?"} on for ${t.player(o)?.name ?: "?"}" else "Pick who comes off and on",
                { if (o != null && i != null) vm.substitute(forA, o, i) },
                icon = Icons.Filled.SwapHoriz,
                enabled = ready
            )
            SecondaryButton("Cancel", { vm.closeSub() }, Modifier.fillMaxWidth())
        }
    }
}

// ======================= HALFTIME =======================

@Composable
private fun HalftimeContent(t: Tournament, m: Match, vm: LiveViewModel, onExit: () -> Unit) {
    BackHandler { vm.pause(); onExit() }
    val top = pointsByPlayer(m).entries.sortedByDescending { it.value }.take(3)
    Screen(Navy) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            HText("HALFTIME", 14.sp, FontWeight.Bold, AccentDark)
            DisplayText(formatClock(vm.clockMs), 120.sp, OnDark)
            HText(if (vm.timeUp) "Break over" else "Break remaining", 14.sp, FontWeight.Medium, MuteDark)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(t.teamName(m.teamAId).uppercase(), 14.sp, FontWeight.Bold, MuteDark)
                    DisplayText("${m.scoreA}", 72.sp, OnDark)
                }
                HText("–", 28.sp, FontWeight.Medium, MuteDark)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(t.teamName(m.teamBId).uppercase(), 14.sp, FontWeight.Bold, MuteDark)
                    DisplayText("${m.scoreB}", 72.sp, OnDark)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (top.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Navy2).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HText("Top scorers · 1st half", 12.sp, FontWeight.Bold, MuteDark, Modifier.padding(vertical = 6.dp))
                    top.forEach { (pid, pts) ->
                        val team = t.teams.firstOrNull { pid in it.playerIds }
                        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(teamColorOf(t, team?.id), 10.dp)
                            Spacer(Modifier.width(12.dp))
                            HText(t.player(pid)?.name ?: "?", 15.sp, FontWeight.Bold, OnDark, Modifier.weight(1f), maxLines = 1)
                            HText("$pts pts", 14.sp, FontWeight.Medium, MuteDark)
                        }
                    }
                }
            }
        }
        BottomBar(dark = true) {
            PrimaryButton("Start ${if (m.period == 1) "2nd half" else "next period"} now", { vm.startNextPeriod() }, icon = Icons.Filled.PlayArrow)
            SecondaryButton("Add 1 min to break", { vm.addBreakMinute() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Schedule, dark = true)
        }
    }
}

// ======================= TIE-BREAKER =======================

/**
 * Fixed per sport, no organizer choice: a free-throw shootout (basketball, 3 each) or free-kick shootout (football,
 * 5 each). Teams alternate attempt by attempt; still level after the allotment goes to sudden death, one attempt each.
 * Every attempt is a SHOOTOUT_ATTEMPT in the match log and the state shown here is derived from it (Match.shootout).
 */
@Composable
private fun TieBreakerContent(t: Tournament, m: Match, vm: LiveViewModel) {
    val so = m.shootout(t.sport)
    val shootA = so.nextIsA
    val shooterTeamId = if (shootA) m.teamAId else m.teamBId
    val shooterColor = teamColorOf(t, shooterTeamId)
    val roster = t.team(shooterTeamId)?.playerIds ?: emptyList()
    val taken = (if (shootA) so.attemptsA else so.attemptsB).groupingBy { it.playerId }.eachCount()
    // Suggest a rotation (fewest attempts so far, roster order); the organizer can pick anyone.
    val suggested = roster.minByOrNull { taken[it] ?: 0 }
    var shooter by rememberSaveable(so.all.size, shooterTeamId) { mutableStateOf(suggested) }
    val hasAttempts = so.all.isNotEmpty()

    Screen {
        TopBar("Tie-breaker", null, right = {
            if (hasAttempts || m.events.isNotEmpty()) {
                IconCircle(
                    Icons.AutoMirrored.Filled.Undo,
                    if (hasAttempts) "Undo last attempt" else "Undo last ${t.sport.scoreEventSingular}",
                    { vm.undo() }
                )
            }
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HText("FULL TIME · SCORES LEVEL", 12.sp, FontWeight.Bold, Accent)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    DisplayText("${m.scoreA}", 72.sp)
                    HText("–", 26.sp, FontWeight.Medium, Mute)
                    DisplayText("${m.scoreB}", 72.sp)
                }
                HText("${t.teamName(m.teamAId)} vs ${t.teamName(m.teamBId)} · ${m.label.ifEmpty { m.stage }}", 14.sp, FontWeight.Medium, Mute)
            }
            Row(Modifier.padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (t.sport == Sport.FOOTBALL) Icons.Filled.SportsSoccer else Icons.Filled.SportsBasketball,
                    null, tint = Accent, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                HText("${t.sport.shootoutName} · ${so.perTeam} each, alternating turns", 13.sp, FontWeight.Bold)
            }

            // Attempt dots per team, e.g. [made][missed][pending]  1/2
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
                    .border(BorderStroke(1.dp, Line), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                ShootoutRow(t.teamName(m.teamAId), teamColorOf(t, m.teamAId), so.attemptsA.map { it.made }, so.slots)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                ShootoutRow(t.teamName(m.teamBId), teamColorOf(t, m.teamBId), so.attemptsB.map { it.made }, so.slots)
            }

            // Current shooter: pick who takes this attempt.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
                    .border(BorderStroke(2.dp, shooterColor.copy(alpha = 0.2f)), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(shooterColor, 12.dp)
                    Spacer(Modifier.width(8.dp))
                    val attemptText = if (so.suddenDeath) "Attempt ${so.nextAttemptNumber} · sudden death"
                    else "Attempt ${so.nextAttemptNumber} of ${so.perTeam}"
                    HText("${t.teamName(shooterTeamId).uppercase()} TO SHOOT · $attemptText", 12.sp, FontWeight.Bold, Mute)
                }
                if (roster.isEmpty()) {
                    HText("This team has no players. The attempt is credited to the team.", 13.sp, FontWeight.Normal, Mute)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    roster.forEach { pid ->
                        val on = shooter == pid
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) AccentSoft else Surface)
                                .border(BorderStroke(if (on) 2.dp else 1.dp, if (on) Accent else Line), RoundedCornerShape(12.dp))
                                .clickable { shooter = pid }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(if (on) Accent else Surface)
                                    .border(BorderStroke(2.dp, if (on) Accent else Line), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            Spacer(Modifier.width(10.dp))
                            HText(t.player(pid)?.name ?: "?", 15.sp, FontWeight.Bold, Ink, Modifier.weight(1f), maxLines = 1)
                        }
                    }
                }
            }
            HText(
                "Still tied after ${so.perTeam} each repeats one ${t.sport.shootoutAttemptSingular} at a time, sudden death, until decided.",
                12.sp, FontWeight.Normal, Mute, Modifier.fillMaxWidth(), TextAlign.Center
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        BottomBar {
            // A shooter must be picked when the team has players; a team without a roster shoots as the team.
            val ready = roster.isEmpty() || shooter?.let { it in roster } == true
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    "Missed", { vm.shootoutAttempt(shooter?.takeIf { it in roster }, made = false) },
                    Modifier.weight(1f), icon = Icons.Filled.Close, enabled = ready
                )
                PrimaryButton(
                    t.sport.shootoutMadeLabel, { vm.shootoutAttempt(shooter?.takeIf { it in roster }, made = true) },
                    Modifier.weight(1f), icon = Icons.Filled.Check, enabled = ready, color = Green
                )
            }
        }
    }
}

// ======================= SUMMARY =======================

@Composable
private fun SummaryContent(t: Tournament, m: Match, vm: LiveViewModel, onExit: () -> Unit) {
    val pts = pointsByPlayer(m)
    val pom = pts.entries.maxByOrNull { it.value }
    // Set when the shootout decided it, e.g. "Won 2–1 on free throws · tie-breaker recorded".
    val note = m.tieNote

    @Composable
    fun column(teamId: String?, lineup: List<String>, modifier: Modifier) {
        val team = t.team(teamId)
        val subbed = m.log.filter { it.type == MatchEventType.SUB && it.teamId == teamId }
            .flatMap { listOfNotNull(it.outPlayerId, it.inPlayerId) }.toSet()
        val ids = ((team?.playerIds ?: emptyList()).filter { it in lineup || it in subbed || (pts[it] ?: 0) > 0 })
            .sortedByDescending { pts[it] ?: 0 }
        Column(modifier) {
            HText(t.teamName(teamId), 13.sp, FontWeight.Bold, Mute, Modifier.padding(bottom = 4.dp))
            ids.forEach { pid ->
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(teamColorOf(t, teamId), 10.dp)
                    Spacer(Modifier.width(10.dp))
                    HText(t.player(pid)?.name ?: "?", 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f), maxLines = 1)
                    if (pom?.key == pid) Icon(Icons.Filled.Star, "Player of the match", tint = Gold, modifier = Modifier.size(16.dp))
                    HText("${pts[pid] ?: 0}", 15.sp, FontWeight.Bold)
                }
            }
            val teamOnly = m.events.filter { it.teamId == teamId && it.playerId == null }.sumOf { it.points }
            if (teamOnly > 0) {
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    HText("Team ${t.sport.scoreEventPlural}", 13.sp, FontWeight.Medium, Mute, Modifier.weight(1f))
                    HText("$teamOnly", 15.sp, FontWeight.Bold)
                }
            }
        }
    }

    Screen {
        TopBar("Full time", null)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FullTimeScoreCard(t, m)
            ShootoutResultCard(t, m)
            if (pom != null && pom.value > 0) {
                val team = t.teams.firstOrNull { pom.key in it.playerIds }
                HCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            HText("Player of the match", 12.sp, FontWeight.Medium, Mute)
                            HText("${t.player(pom.key)?.name ?: "?"} · ${team?.name ?: ""}", 16.sp, FontWeight.Bold)
                        }
                        DisplayText("${pom.value} pts", 24.sp)
                    }
                }
            }
            HCard(padding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    column(m.teamAId, m.lineupA, Modifier.weight(1f))
                    column(m.teamBId, m.lineupB, Modifier.weight(1f))
                }
            }
            // Draws and shootouts are explained above; only older notes are still shown here.
            if (note.isNotEmpty() && !m.isDraw && m.shootoutResult(t.sport) == null) HText(note, 13.sp, FontWeight.Medium, Mute, Modifier.fillMaxWidth(), TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            PrimaryButton("Save & next match", onExit, icon = Icons.Filled.ChevronRight)
            if (vm.canReopen()) {
                SecondaryButton("Edit scores", { vm.reopen() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Edit)
            }
        }
    }
}

// ─── Previews ───────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF14171F, widthDp = 412, heightDp = 800)
@Composable
private fun PreviewMatchReady() {
    HoopsTheme {
        Screen(Navy) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Spacer(Modifier.weight(1f))
                HText("READY TO PLAY", 14.sp, FontWeight.Bold, AccentDark)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HText("TEAM A", 14.sp, FontWeight.Bold, MuteDark)
                        DisplayText("7", 72.sp, OnDark)
                    }
                    HText("–", 28.sp, FontWeight.Medium, MuteDark)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HText("TEAM B", 14.sp, FontWeight.Bold, MuteDark)
                        DisplayText("5", 72.sp, OnDark)
                    }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Start Match", {}, color = AccentDark)
            }
        }
    }
}
