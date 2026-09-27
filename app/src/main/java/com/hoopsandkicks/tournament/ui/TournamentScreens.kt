package com.hoopsandkicks.tournament.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.hoopsandkicks.tournament.HoopsApp
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.Team
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.StageType
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.canStart
import com.hoopsandkicks.tournament.data.computeDisplaySeconds
import com.hoopsandkicks.tournament.data.estimatedEndAt
import com.hoopsandkicks.tournament.data.exceedsWindow
import com.hoopsandkicks.tournament.data.roomShareText
import com.hoopsandkicks.tournament.data.remote.JoinState
import com.hoopsandkicks.tournament.data.remote.RemoteSync
import com.hoopsandkicks.tournament.data.remote.ViewerStore
import com.hoopsandkicks.tournament.data.shootout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun teamCol(t: Tournament, id: String?): Color {
    val tm = t.team(id) ?: return Mute
    return teamColor(tm.color)
}

private fun regenerate(id: String) {
    HoopsApp.repo.mutate(id) { tt ->
        if (tt.anyStarted()) tt else {
            var n = tt.copy(matches = emptyList(), championId = null, status = TStatus.ACTIVE)
            n = n.copy(matches = Scheduler.generate(n))
            Scheduler.advance(n)
        }
    }
}

// ======================= TOURNAMENT HOME (tabs) =======================

@Composable
fun TournamentScreen(
    id: String,
    onBack: () -> Unit,
    onFixtures: () -> Unit,
    onFormat: () -> Unit,
    onOpenMatch: (String, Boolean) -> Unit,
    onDeleted: () -> Unit,
    /** Viewer mode for a joined live room: every scoring, editing and match-control action is hidden. */
    readOnly: Boolean = false
) {
    val t = observeTournament(id)
    LaunchedEffect(t == null) { if (t == null) onDeleted() }
    if (t == null) return
    TournamentContent(t, onBack, onFixtures, onFormat, onOpenMatch, readOnly)
}

@Composable
internal fun TournamentContent(
    t: Tournament,
    onBack: () -> Unit,
    onFixtures: () -> Unit,
    onFormat: () -> Unit,
    onOpenMatch: (String, Boolean) -> Unit,
    readOnly: Boolean,
    initialTab: Int = 0
) {
    val id = t.id
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmStopHosting by remember { mutableStateOf(false) }
    var goingLive by remember { mutableStateOf(false) }
    var liveError by remember { mutableStateOf(false) }
    /** Room-code sheet (RoomCode design): opened from the "Share" pill or the live row's "Manage". */
    var showShare by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun goLive() {
        if (goingLive) return
        goingLive = true
        scope.launch {
            val code = RemoteSync.instance.hostTournament(t)
            goingLive = false
            if (code != null) {
                HoopsApp.repo.mutate(id) { it.copy(roomCode = code) }
            } else {
                liveError = true
            }
        }
    }

    Screen {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HubTab(
                    t, onBack, onFixtures, if (readOnly) ({}) else onFormat, onOpenMatch,
                    onGoTab = { tab = it },
                    readOnly = readOnly,
                    goingLive = goingLive,
                    onManageHosting = { showShare = true },
                    menu = {
                        if (readOnly) {
                            Badge("Watching live", AccentSoft, Accent)
                        } else {
                            SharePill {
                                if (t.roomCode == null) goLive()
                                showShare = true
                            }
                            Spacer(Modifier.width(8.dp))
                            Box {
                                IconCircle(Icons.Filled.MoreVert, "More", { menu = true })
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Game format") }, onClick = { menu = false; onFormat() })
                                    if (t.roomCode == null) {
                                        DropdownMenuItem(
                                            text = { Text(if (goingLive) "Going live…" else "Go live") },
                                            onClick = { menu = false; goLive() }
                                        )
                                    } else {
                                        DropdownMenuItem(text = { Text("Stop hosting") }, onClick = { menu = false; confirmStopHosting = true })
                                    }
                                    DropdownMenuItem(text = { Text("Delete tournament") }, onClick = { menu = false; confirmDelete = true })
                                }
                            }
                        }
                    }
                )
                1 -> StandingsTab(t)
                2 -> TeamsTab(t)
                else -> StatsTab(t)
            }
        }
        NavBar(tab) { tab = it }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete tournament?") },
            text = { Text("“${t.name}” and all its matches will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; HoopsApp.repo.delete(id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (confirmStopHosting) {
        AlertDialog(
            onDismissRequest = { confirmStopHosting = false },
            title = { Text("Stop hosting?") },
            text = { Text("Viewers will no longer see updates. Everything stays saved on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStopHosting = false
                    val code = t.roomCode
                    HoopsApp.repo.mutate(id) { it.copy(roomCode = null) }
                    if (code != null) RemoteSync.instance.stopHosting(code)
                }) { Text("Stop hosting") }
            },
            dismissButton = { TextButton(onClick = { confirmStopHosting = false }) { Text("Keep hosting") } }
        )
    }
    if (showShare && !readOnly) {
        RoomCodeSheet(
            t, goingLive,
            onDismiss = { showShare = false },
            onStopHosting = { showShare = false; confirmStopHosting = true }
        )
        // Going live failed: close the sheet, the error dialog explains why.
        LaunchedEffect(liveError) { if (liveError) showShare = false }
    }
    if (liveError) {
        AlertDialog(
            onDismissRequest = { liveError = false },
            title = { Text("Couldn't go live") },
            text = { Text("Check your internet connection. Live hosting also needs a Firebase project set up for this app (see the README). Everything else works offline.") },
            confirmButton = { TextButton(onClick = { liveError = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun HubTab(
    t: Tournament,
    onBack: () -> Unit,
    onFixtures: () -> Unit,
    onFormat: () -> Unit,
    onOpenMatch: (String, Boolean) -> Unit,
    onGoTab: (Int) -> Unit,
    readOnly: Boolean,
    goingLive: Boolean,
    onManageHosting: () -> Unit,
    menu: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(t.name, onBack, right = { menu() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StageProgress(t)
            val code = t.roomCode
            if (!readOnly && code != null) LiveHostingRow(code, onManageHosting)
            if (!readOnly && code == null && goingLive) HText("Going live…", 13.sp, FontWeight.Medium, Mute)
            // Looks ahead like the wizard: the whole tournament played out, not just the stages drawn so far, so the
            // banner (and its optional "Extend end") also covers the rounds still to be drawn. Informational only.
            val projectedOverrun = remember(t) { Scheduler.projectedOverrunMinutes(t) }
            if (projectedOverrun > 0L && t.status != TStatus.COMPLETED) ScheduleWindowBanner(t, projectedOverrun, readOnly)
            if (t.status == TStatus.COMPLETED) {
                HCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = Gold, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            HText("Champion", 12.sp, FontWeight.Medium, Mute)
                            DisplayText(t.teamName(t.championId), 30.sp, Ink)
                        }
                    }
                }
            } else {
                val live = t.realMatches().firstOrNull {
                    it.status == MatchStatus.LIVE || it.status == MatchStatus.BREAK || it.status == MatchStatus.TIEBREAK
                }
                val next = live ?: t.realMatches().filter { it.status == MatchStatus.SCHEDULED }.minByOrNull { it.number }
                if (next != null) UpNextCard(t, next, live != null, onOpenMatch, readOnly)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.AutoMirrored.Filled.FormatListBulleted, "Fixtures", "${t.realMatches().size} matches so far", Modifier.weight(1f), onFixtures)
                Tile(Icons.Filled.BarChart, "Leaderboard", "Across all stages", Modifier.weight(1f)) { onGoTab(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Filled.Groups, "Teams & players", "${t.teams.size} teams · ${t.players.size} players", Modifier.weight(1f)) { onGoTab(2) }
                Tile(Icons.Filled.Tune, "Game format", t.format.summary(), Modifier.weight(1f), onFormat)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Hub topbar entry point for live hosting (TournamentHub design): broadcast icon + "Share". */
@Composable
private fun SharePill(onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Surface)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.WifiTethering, null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        HText("Share", 14.sp, FontWeight.Bold)
    }
}

/** Compact status on the host's hub while live: "Live · room code XXXXXX" with a "Manage" link to the sheet. */
@Composable
private fun LiveHostingRow(code: String, onManage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GreenSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(Green, 8.dp)
        Spacer(Modifier.width(10.dp))
        // TODO: "· N watching" needs viewer presence, which the read-only room model does not record yet.
        HText("Live · room code $code", 13.sp, FontWeight.Bold, Green, Modifier.weight(1f), maxLines = 1)
        HText("Manage", 13.sp, FontWeight.Bold, Green, Modifier.clickable(onClick = onManage).padding(start = 8.dp))
    }
}

/**
 * Room-code sheet (RoomCode design): the code, Copy / Share, what viewers can do, and Stop hosting.
 * While going live the code is not known yet, so a short placeholder is shown.
 */
@Composable
private fun RoomCodeSheet(t: Tournament, goingLive: Boolean, onDismiss: () -> Unit, onStopHosting: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val code = t.roomCode
    var copied by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Navy)
                    .clickable(enabled = false) {}.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Navy3))
                if (code == null) {
                    DisplayText(if (goingLive) "Going live…" else "Not live", 30.sp, OnDark)
                    HText("Creating a room code for ${t.name}.", 13.sp, FontWeight.Medium, MuteDark, align = TextAlign.Center)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DisplayText("You're live", 30.sp, OnDark)
                        HText("Anyone with the code can watch ${t.name} update in real time", 13.sp, FontWeight.Medium, MuteDark, align = TextAlign.Center)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        code.forEach { ch ->
                            Box(
                                Modifier.size(width = 44.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).background(Navy2)
                                    .border(BorderStroke(1.dp, Navy3), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) { DisplayText(ch.toString(), 32.sp, OnDark) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            if (copied) "Copied" else "Copy code",
                            { clipboard.setText(AnnotatedString(code)); copied = true },
                            Modifier.weight(1f), icon = Icons.Filled.ContentCopy, dark = true
                        )
                        SecondaryButton(
                            "Share link",
                            {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, roomShareText(t.name, code))
                                }
                                context.startActivity(Intent.createChooser(send, "Share room code"))
                            },
                            Modifier.weight(1f), icon = Icons.Filled.Share, dark = true
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Navy2).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Visibility, null, tint = MuteDark, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        HText(
                            "Viewers see live scores and the clock — read-only, they can't change anything",
                            12.sp, FontWeight.Medium, MuteDark, Modifier.weight(1f)
                        )
                    }
                    SecondaryButton("Stop hosting", onStopHosting, Modifier.fillMaxWidth(), dark = true)
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(10.dp))
        HText(title, 15.sp, FontWeight.Bold)
        HText(sub, 12.sp, FontWeight.Normal, Mute)
    }
}

@Composable
private fun StageProgress(t: Tournament) {
    val existing = t.stages()
    val names = existing.map { it.second }
    val steps = existing.map { it.second to it.first } + Scheduler.expectedStages(t).filter { it !in names }.map { it to -1 }
    val states = steps.map { (name, idx) ->
        if (idx < 0) 2 else {
            val ms = t.realMatches().filter { it.stageIndex == idx }
            if (ms.isNotEmpty() && ms.all { it.status == MatchStatus.FINISHED }) 0 else 1
        }
    }
    val nowIdx = states.indexOf(1)
    HCard {
        if (steps.size > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                steps.forEachIndexed { i, (name, _) ->
                    val st = if (states[i] == 0) 0 else if (i == nowIdx) 1 else 2
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(
                                when (st) { 0 -> Green; 1 -> Accent; else -> Line }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (st == 0) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            else if (st == 1) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                        }
                        Spacer(Modifier.height(6.dp))
                        HText(name, 12.sp, if (st == 1) FontWeight.Bold else FontWeight.Medium, if (st == 2) Mute else Ink, align = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        val stage = t.currentStageName()
        val stageMatches = t.realMatches().filter { it.stage == stage }
        val done = stageMatches.count { it.status == MatchStatus.FINISHED }
        HText(
            if (t.status == TStatus.COMPLETED) "Tournament complete · ${t.playedCount()} matches played"
            else "$stage · $done of ${stageMatches.size} matches played",
            13.sp, FontWeight.Medium, Mute, Modifier.fillMaxWidth(), TextAlign.Center
        )
    }
}

@Composable
internal fun UpNextCard(t: Tournament, m: Match, live: Boolean, onOpenMatch: (String, Boolean) -> Unit, readOnly: Boolean = false) {
    // Informational only: the planned times are a tentative display estimate and never stop a match being started.
    val pastPlan = !live && m.status == MatchStatus.SCHEDULED && m.exceedsWindow(t)
    var fixing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Navy).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val slot = m.slotText().let { if (it.isEmpty()) "" else " · ${it.uppercase()}" }
        HText(if (live) "LIVE NOW · ${m.title(t).uppercase()}$slot" else "UP NEXT · ${m.title(t).uppercase()}$slot", 12.sp, FontWeight.Bold, MuteDark)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { DisplayText(t.teamName(m.teamAId).uppercase(), 32.sp, OnDark) }
            HText("vs", 14.sp, FontWeight.Medium, MuteDark)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { DisplayText(t.teamName(m.teamBId).uppercase(), 32.sp, OnDark, align = TextAlign.End) }
        }
        HText(t.format.forMatch(m).summary(), 13.sp, FontWeight.Normal, MuteDark)
        if (readOnly) {
            if (live) LiveScoreLine(t, m)
            if (m.status == MatchStatus.TIEBREAK) ShootoutLivePanel(t, m, dark = true)
            if (pastPlan) PastPlanRow(onFix = null, dark = true)
        } else {
            if (pastPlan) PastPlanRow(onFix = { fixing = true }, dark = true)
            PrimaryButton(
                if (live) "Resume match" else "Start match",
                { onOpenMatch(m.id, !live) },
                icon = Icons.Filled.PlayArrow
            )
        }
    }
    if (fixing) ScheduleFixDialog(t, m) { fixing = false }
}

// ======================= SCHEDULE WINDOW (tentative plan vs. the end time) =======================
// The end time is enforced only when a plan is made (the wizard's "Save & finish" and "Save format" refuse a plan
// that doesn't fit). Once matches exist, planned times are a tentative display estimate: real games run long, so
// nothing here ever stops a match from being started. The labels and banner below are informational only.

/** Matches not started yet whose tentative slot ends after the tournament's end time (informational). */
private fun Tournament.matchesPastPlannedEnd(): List<Match> =
    realMatches().filter { it.status == MatchStatus.SCHEDULED && it.exceedsWindow(this) }

/** Applies a time-window fix, re-slots every match with the new settings and saves, so every observer updates live. */
private fun applyWindowFix(id: String, change: (Tournament) -> Tournament) {
    HoopsApp.repo.mutate(id) { Scheduler.scheduleTimes(change(it)) }
}

/**
 * The quick fixes for a tentative schedule that runs past the end time: move the end time later (one tap to the earliest
 * end that fits, or any date/time via the same picker as the create screen) or change the number of courts.
 * Each fix re-slots every match and saves at once; [onApplied] runs after each one (e.g. to close a dialog).
 * [overrunMin] is how far the relevant schedule runs past the end, and sizes the one-tap extension: pass the
 * whole-tournament overrun ([Scheduler.projectedOverrunMinutes] or the wizard's projection) so one extension
 * also covers the stages not drawn yet.
 */
@Composable
fun ScheduleFixActions(t: Tournament, overrunMin: Long, onApplied: () -> Unit = {}) {
    var picking by remember { mutableStateOf(false) }
    // A picked end time that isn't after the start: shown inline, like the create screen, instead of being dropped.
    var pickError by remember { mutableStateOf(false) }
    val fitEnd = if (t.endAt > 0L && overrunMin > 0L) t.endAt + overrunMin * 60_000L else 0L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fitEnd > 0L) {
            // Includes the date when the new end falls on another day than the current end (e.g. past midnight).
            SecondaryButton("Extend end to ${formatTimeFrom(fitEnd, t.endAt)}", {
                pickError = false
                applyWindowFix(t.id) { it.copy(endAt = maxOf(it.endAt, fitEnd)) }
                onApplied()
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Schedule)
        }
        SecondaryButton("Pick a new end time", { picking = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Edit)
        if (pickError) HText("The end time must be after the start time (${formatDateTime(t.startAt)}).", 12.sp, FontWeight.Medium, Accent)
        StepperRow(
            "Courts", t.courts.toString(), "courts",
            { if (t.courts > 1) { pickError = false; applyWindowFix(t.id) { it.copy(courts = (it.courts - 1).coerceAtLeast(1)) }; onApplied() } },
            { if (t.courts < 20) { pickError = false; applyWindowFix(t.id) { it.copy(courts = (it.courts + 1).coerceAtMost(20)) }; onApplied() } },
            "Add a court to run more games at once"
        )
    }
    if (picking) {
        DateTimePickerFlow(
            initialMs = when {
                fitEnd > 0L -> fitEnd
                t.endAt > 0L -> t.endAt
                else -> nextWholeHourMs()
            },
            onDismiss = { picking = false },
            onPicked = { ms ->
                picking = false
                // Same rule as the create screen: the end must come after the start.
                if (ms > t.startAt) {
                    pickError = false
                    applyWindowFix(t.id) { it.copy(endAt = ms) }
                    onApplied()
                } else {
                    pickError = true
                }
            }
        )
    }
}

/** Optional dialog opened from a match's "Adjust plan"; closes after a fix is applied. Never required to start a match. */
@Composable
private fun ScheduleFixDialog(t: Tournament, m: Match?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DisplayText("Adjust plan", 26.sp)
            // Whole tournament, like the hub banner: extending for this match alone would leave later rounds over the end.
            val overrun = remember(t) { Scheduler.projectedOverrunMinutes(t) }
            val end = m?.estimatedEndAt(t)
            val why = if (m != null && end != null && end > t.endAt) {
                "Match ${m.number} is planned to end at ${formatTimeFrom(end, t.endAt)}, after the tournament ends (${formatTime(t.endAt)})."
            } else {
                "The schedule runs $overrun min past the tournament's end time (${formatTime(t.endAt)})."
            }
            HText("$why The plan is only an estimate and you can still start the match. To tidy it up, extend the end time or add a court.", 13.sp, FontWeight.Medium, Mute)
            ScheduleFixActions(t, overrun, onApplied = onDismiss)
            SecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Informational label for a match whose tentative slot ends after the end time, plus an optional "Adjust plan"
 * ([onFix] null hides it). It never disables anything: the match can be started regardless.
 */
@Composable
private fun PastPlanRow(onFix: (() -> Unit)?, dark: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(if (dark) Navy2 else AccentSoft).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Schedule, null, tint = if (dark) AccentDark else Accent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            HText("Planned past end time", 12.sp, FontWeight.Bold, if (dark) AccentDark else Accent, maxLines = 1)
        }
        if (onFix != null) {
            HText(
                "Adjust plan", 12.sp, FontWeight.Bold, if (dark) OnDark else Ink,
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onFix).padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1
            )
        }
    }
}

/**
 * Informational hub banner while the tentative plan, played through every stage ([Scheduler.projectedOverrunMinutes],
 * passed in as [overrun]), ends after the tournament's end time. Blocks nothing; the quick fixes are optional, and it
 * can be dismissed (it comes back if the overrun changes, e.g. after a round is drawn or a fix is applied).
 */
@Composable
private fun ScheduleWindowBanner(t: Tournament, overrun: Long, readOnly: Boolean) {
    var dismissed by rememberSaveable(t.id, overrun) { mutableStateOf(false) }
    if (dismissed) return
    val pastEnd = t.matchesPastPlannedEnd().size
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AccentSoft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            HText("Schedule is running $overrun min over plan", 14.sp, FontWeight.Bold, Accent, Modifier.weight(1f))
            Icon(
                Icons.Filled.Close, "Dismiss", tint = Mute,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { dismissed = true }.padding(4.dp)
            )
        }
        val pastText = when (pastEnd) {
            0 -> ""
            1 -> " 1 upcoming match is planned to end after it."
            else -> " $pastEnd upcoming matches are planned to end after it."
        }
        HText(
            "Played through every stage, the tentative plan ends $overrun min after the tournament's end time " +
                "(${formatTime(t.endAt)}).$pastText Planned times are only an estimate; matches can still be started." +
                if (readOnly) "" else " To tidy up the plan, extend the end time or add a court:",
            13.sp, FontWeight.Medium, Ink
        )
        if (!readOnly) ScheduleFixActions(t, overrun)
    }
}

/** Viewer-side score and clock, ticking locally from (value, clockEpochMs, clockRunning). */
@Composable
private fun LiveScoreLine(t: Tournament, m: Match) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.clockRunning, m.clockEpochMs) {
        now = System.currentTimeMillis()
        while (m.clockRunning) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    val value = if (m.status == MatchStatus.BREAK) m.breakRemainingSec else m.remainingSec
    val sec = computeDisplaySeconds(value, m.clockEpochMs, m.clockRunning, now)
    val phase = when {
        m.status == MatchStatus.BREAK -> "Break · ${formatSeconds(sec)}"
        m.status == MatchStatus.TIEBREAK -> m.shootout(t.sport).let { "${t.sport.shootoutName} · ${it.madeA}–${it.madeB}" }
        else -> formatSeconds(sec) + if (m.clockRunning) "" else " · paused"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DisplayText("${m.scoreA} – ${m.scoreB}", 44.sp, OnDark, Modifier.weight(1f))
        HText(phase, 15.sp, FontWeight.Bold, AccentDark)
    }
}

// ======================= FIXTURES =======================

@Composable
fun FixturesScreen(
    id: String, onBack: () -> Unit, onOpenMatch: (String, Boolean) -> Unit, readOnly: Boolean = false,
    onReorder: () -> Unit = {}
) {
    val t = observeTournament(id) ?: return
    FixturesContent(t, onBack, onOpenMatch, readOnly, onReorder)
}

@Composable
internal fun FixturesContent(
    t: Tournament, onBack: () -> Unit, onOpenMatch: (String, Boolean) -> Unit, readOnly: Boolean, onReorder: () -> Unit
) {
    val id = t.id
    val existing = t.stages()
    val names = existing.map { it.second }
    val placeholders = Scheduler.expectedStages(t).filter { it !in names }
    val chips = existing.map { it.second } + placeholders
    val defaultIdx = run {
        val open = t.realMatches().filter { it.status != MatchStatus.FINISHED }.minByOrNull { it.frontier() }
        val si = open?.stageIndex ?: (existing.lastOrNull()?.first ?: 0)
        existing.indexOfFirst { it.first == si }.coerceAtLeast(0)
    }
    var sel by rememberSaveable { mutableIntStateOf(-1) }
    val cur = (if (sel < 0) defaultIdx else sel).coerceIn(0, maxOf(0, chips.size - 1))

    Screen {
        TopBar("Fixtures", onBack, right = {
            if (!readOnly && t.startAt > 0L && t.realMatches().any { it.status == MatchStatus.SCHEDULED }) {
                IconCircle(Icons.Filled.Reorder, "Reorder matches", onReorder)
                Spacer(Modifier.width(8.dp))
            }
            if (!readOnly && !t.anyStarted()) IconCircle(Icons.Filled.Shuffle, "Regenerate schedule", { regenerate(id) })
        })
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chips.forEachIndexed { i, n -> Chip(n, i == cur, { sel = i }) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (cur >= existing.size) {
                HCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = Mute, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        HText("Locked. This round is drawn automatically once the earlier stage is finished.", 13.sp, FontWeight.Medium, Mute)
                    }
                }
            } else {
                val stageIdx = existing[cur].first
                val ms = t.matches.filter { it.stageIndex == stageIdx }
                val real = ms.filter { !it.bye }
                val type = ms.firstOrNull()?.stageType
                if (type == StageType.GROUP) {
                    real.map { it.group }.distinct().sorted().forEach { g ->
                        val gm = real.filter { it.group == g }
                        val teamNames = gm.flatMap { listOfNotNull(it.teamAId, it.teamBId) }.distinct().joinToString(" · ") { t.teamName(it) }
                        SectionTitle(g, teamNames)
                        gm.forEach { m -> MatchCard(t, m, onOpenMatch, readOnly) }
                    }
                } else {
                    val rounds = real.map { it.round }.distinct().sorted()
                    rounds.forEach { r ->
                        if (rounds.size > 1) SectionTitle("Round $r", "")
                        real.filter { it.round == r }.forEach { m -> MatchCard(t, m, onOpenMatch, readOnly) }
                    }
                    ms.filter { it.bye }.forEach { m ->
                        HText("${t.teamName(m.teamAId)} has a bye (${m.label.ifEmpty { "advances" }})", 12.sp, FontWeight.Medium, Mute)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ======================= FIXTURES REORDER (drag and drop, per court) =======================

/** Fixed row height used to turn a drag's pixel offset into "how many rows did this move" during reorder. */
private val ReorderRowHeight = 72.dp

@Composable
fun ReorderFixturesScreen(id: String, onBack: () -> Unit) {
    val t = observeTournament(id) ?: return
    ReorderFixturesContent(t, onBack)
}

@Composable
private fun ReorderFixturesContent(t: Tournament, onBack: () -> Unit) {
    val id = t.id
    val courts = (1..t.courts.coerceAtLeast(1)).toList()
    var court by rememberSaveable { mutableIntStateOf(courts.first()) }

    Screen {
        TopBar("Reorder matches", onBack)
        HText(
            "Drag to change the order — times update on their own", 12.sp, FontWeight.Medium, Mute,
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )
        if (courts.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                courts.forEach { c -> Chip("Court $c", c == court, { court = c }) }
            }
        }
        CourtReorderList(id, t, court)
    }
}

@Composable
private fun ColumnScope.CourtReorderList(id: String, t: Tournament, court: Int) {
    val onCourt = t.matches.filter { it.court == court && !it.bye }.sortedBy { it.scheduledAt ?: Long.MAX_VALUE }
    val items = remember(court) { mutableStateListOf<Match>().apply { addAll(onCourt) } }
    var draggingId by remember(court) { mutableStateOf<String?>(null) }
    var dragOffset by remember(court) { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ReorderRowHeight.toPx() }
    // Resync from the live tournament (someone started a match, an earlier stage advanced, …) whenever we're
    // not mid-drag — dragging owns the list until the admin lets go.
    LaunchedEffect(onCourt, draggingId) {
        if (draggingId == null) {
            items.clear(); items.addAll(onCourt)
        }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp)) {
        if (items.isEmpty()) {
            HText("Nothing scheduled on this court yet.", 13.sp, FontWeight.Medium, Mute, Modifier.padding(vertical = 12.dp))
        }
        items.forEachIndexed { index, m ->
            val movable = m.status == MatchStatus.SCHEDULED
            val isDragging = m.id == draggingId
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .height(ReorderRowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .then(
                            if (movable) {
                                Modifier.pointerInput(m.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingId = m.id; dragOffset = 0f },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                            HoopsApp.repo.mutate(id) { Scheduler.reorderCourt(it, court, items.map { mm -> mm.id }) }
                                        },
                                        onDragCancel = { draggingId = null; dragOffset = 0f }
                                    ) { change, drag ->
                                        change.consume()
                                        dragOffset += drag.y
                                        val curIdx = items.indexOfFirst { it.id == m.id }
                                        val shift = (dragOffset / rowHeightPx).roundToInt()
                                        if (shift != 0) {
                                            val target = (curIdx + shift).coerceIn(0, items.size - 1)
                                            if (target != curIdx && items[target].status == MatchStatus.SCHEDULED) {
                                                val moving = items.removeAt(curIdx)
                                                items.add(target, moving)
                                                dragOffset -= shift * rowHeightPx
                                            }
                                        }
                                    }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) { DragHandleDots(movable) }
                HCard(Modifier.weight(1f).padding(start = 8.dp), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            HText(
                                "${t.teamName(m.teamAId)} vs ${t.teamName(m.teamBId)}", 14.sp, FontWeight.Bold,
                                if (movable) Ink else Mute
                            )
                            val timeText = m.scheduledAt?.let { formatTime(it) } ?: "—"
                            HText(
                                if (movable) "$timeText · Match ${m.number}" else "$timeText · ${statusLabel(m.status)}, locked",
                                12.sp, FontWeight.Medium, if (isDragging) Accent else Mute
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(s: MatchStatus): String = when (s) {
    MatchStatus.FINISHED -> "played"
    MatchStatus.LIVE -> "live"
    MatchStatus.BREAK -> "on a break"
    MatchStatus.TIEBREAK -> "in a tie-break"
    MatchStatus.SCHEDULED -> "scheduled"
}

@Composable
private fun DragHandleDots(enabled: Boolean) {
    val c = if (enabled) Mute else Line
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Box(Modifier.size(3.5.dp).clip(CircleShape).background(c)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, sub: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        DisplayText(title, 22.sp)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            HText(sub, 12.sp, FontWeight.Medium, Mute, Modifier.weight(1f, fill = false), maxLines = 1)
        }
    }
}

@Composable
fun MatchCard(
    t: Tournament, m: Match, onOpenMatch: (String, Boolean) -> Unit, readOnly: Boolean = false,
    shootoutExpanded: Boolean = false
) {
    val finished = m.status == MatchStatus.FINISHED
    // Tentative slot ends after the tournament's end time: shown as a note only, the match can still be started.
    val pastPlan = m.status == MatchStatus.SCHEDULED && m.exceedsWindow(t)
    var fixing by remember { mutableStateOf(false) }
    // Viewers can't open the match screens; a tap expands the finished shootout in place instead.
    var expanded by remember(m.id) { mutableStateOf(shootoutExpanded) }
    val canExpand = readOnly && m.hasShootoutDetail(t)
    HCard(modifier = Modifier.clickable(enabled = canExpand || (!readOnly && m.teamAId != null && m.teamBId != null)) {
        if (readOnly) expanded = !expanded else onOpenMatch(m.id, m.status == MatchStatus.SCHEDULED)
    }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamPill(t.teamName(m.teamAId), teamCol(t, m.teamAId))
                TeamPill(t.teamName(m.teamBId), teamCol(t, m.teamBId))
            }
            if (finished || m.status != MatchStatus.SCHEDULED) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DisplayText("${m.scoreA}", 24.sp, if (finished && m.winnerId == m.teamAId) Ink else Mute)
                    DisplayText("${m.scoreB}", 24.sp, if (finished && m.winnerId == m.teamBId) Ink else Mute)
                }
            } else {
                HText("vs", 14.sp, FontWeight.Medium, Mute)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                when (m.status) {
                    MatchStatus.FINISHED -> Badge("Final", GreenSoft, Green)
                    MatchStatus.SCHEDULED -> if (readOnly) {
                        Badge("Upcoming", Line, Mute)
                    } else {
                        Row(
                            Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(Accent).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            HText("Start", 14.sp, FontWeight.Bold, Color.White)
                        }
                    }
                    MatchStatus.LIVE -> Badge("Live", Accent, Color.White)
                    MatchStatus.BREAK -> Badge("Break", AccentSoft, Accent)
                    MatchStatus.TIEBREAK -> Badge("Tie-break", AccentSoft, Accent)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val meta = buildString {
            append("Match ${m.number}")
            val slot = m.slotText()
            if (slot.isNotEmpty()) append(" · $slot")
            if (m.label.isNotEmpty() && m.label != "Match") append(" · ${m.label}")
        }
        HText(meta, 12.sp, FontWeight.Normal, Mute)
        MatchCardShootout(t, m, readOnly, expanded)
        if (pastPlan) {
            Spacer(Modifier.height(8.dp))
            PastPlanRow(onFix = if (readOnly) null else ({ fixing = true }))
        }
    }
    if (fixing) ScheduleFixDialog(t, m) { fixing = false }
}

// ======================= MATCH READY =======================

@Composable
fun MatchReadyScreen(id: String, mid: String, onBack: () -> Unit, onChangeFormat: () -> Unit, onStarted: () -> Unit) {
    val t = observeTournament(id) ?: return
    val m = t.match(mid) ?: return
    MatchReadyContent(t, m, onBack, onChangeFormat, onStarted)
}

@Composable
private fun MatchReadyContent(t: Tournament, m: Match, onBack: () -> Unit, onChangeFormat: () -> Unit, onStarted: () -> Unit) {
    val id = t.id
    val mid = m.id
    val teamA = t.team(m.teamAId)
    val teamB = t.team(m.teamBId)
    var selA by remember { mutableStateOf(teamA?.playerIds?.toSet() ?: emptySet()) }
    var selB by remember { mutableStateOf(teamB?.playerIds?.toSet() ?: emptySet()) }
    val fmt = t.format.forMatch(m)
    // Informational only: the tentative plan never stops a match from being started (real games run long).
    val pastPlan = m.status == MatchStatus.SCHEDULED && m.exceedsWindow(t)
    var fixing by remember { mutableStateOf(false) }

    Screen {
        TopBar("Match ${m.number}", onBack, right = { Badge(if (m.group.isNotEmpty()) m.group else m.stage) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { DisplayText(t.teamName(m.teamAId).uppercase(), 36.sp, teamCol(t, m.teamAId)) }
                HText("vs", 15.sp, FontWeight.Medium, Mute)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    DisplayText(t.teamName(m.teamBId).uppercase(), 36.sp, teamCol(t, m.teamBId), align = TextAlign.End)
                }
            }
            HCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText(if (fmt.type == com.hoopsandkicks.tournament.data.FormatType.HALVES) "Two halves + break" else "Continuous game", 15.sp, FontWeight.Bold)
                        HText(fmt.summary(), 12.sp, FontWeight.Normal, Mute)
                    }
                    SecondaryButton("Change", onChangeFormat, Modifier.height(40.dp))
                }
            }
            // Display only; deliberately not connected to "Start match" (planned times never block a real game).
            if (m.status == MatchStatus.SCHEDULED) PlannedTimeNote(m)
            HText("Who is playing? Tap to pick the players on court.", 13.sp, FontWeight.Medium, Mute)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LineupColumn(t, teamA?.name ?: "Team A", teamA?.color ?: 0, teamA?.playerIds ?: emptyList(), selA, Modifier.weight(1f)) {
                    selA = if (it in selA) selA - it else selA + it
                }
                LineupColumn(t, teamB?.name ?: "Team B", teamB?.color ?: 1, teamB?.playerIds ?: emptyList(), selB, Modifier.weight(1f)) {
                    selB = if (it in selB) selB - it else selB + it
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            if (m.status != MatchStatus.SCHEDULED) {
                // Started elsewhere (another screen, or this one left open): never "start" it again, which would
                // wipe its score and events. Offer to open it instead.
                HText("This match has already started.", 12.sp, FontWeight.Medium, Mute)
                PrimaryButton("Open match", onStarted, icon = Icons.Filled.PlayArrow)
            } else {
                if (pastPlan) PastPlanRow(onFix = { fixing = true })
                PrimaryButton("Start match", {
                    // Re-check the latest saved state, so a stale screen can never restart (and reset) a match that
                    // is already live or finished. The tentative schedule is deliberately not checked here.
                    val cm = HoopsApp.repo.get(id)?.match(mid)
                    if (cm != null && cm.status != MatchStatus.SCHEDULED) {
                        onStarted()
                    } else if (cm != null && cm.canStart()) {
                        val ordered = { ids: List<String>, sel: Set<String> -> ids.filter { it in sel } }
                        HoopsApp.repo.updateMatch(id, mid) { mm ->
                            mm.copy(
                                status = MatchStatus.LIVE,
                                period = 1,
                                remainingSec = fmt.periodMin * 60,
                                breakRemainingSec = 0,
                                lineupA = ordered(teamA?.playerIds ?: emptyList(), selA),
                                lineupB = ordered(teamB?.playerIds ?: emptyList(), selB),
                                scoreA = 0, scoreB = 0,
                                events = emptyList(), winnerId = null, tieNote = "",
                                clockRunning = false, clockEpochMs = System.currentTimeMillis()
                            ).let {
                                // First entry of the append-only match log (the clock starts paused).
                                MatchLog.append(it, MatchEventType.MATCH_START, clockSec = fmt.periodMin * 60, clockRunning = false)
                            }
                        }
                        onStarted()
                    }
                }, icon = Icons.Filled.PlayArrow, enabled = teamA != null && teamB != null)
            }
        }
    }
    if (fixing) ScheduleFixDialog(t, m) { fixing = false }
}

/**
 * Amber "Planned 12:40 PM · Court 2 / Running about 15 min behind plan" note (MatchReady design), from the match's
 * [Match.scheduledAt] vs. the current time, re-evaluated every 30 s. Purely informational: it never gates starting.
 * Hidden when the match has no planned time. Separate from [PastPlanRow] (the tournament end-time check).
 */
@Composable
private fun PlannedTimeNote(m: Match) {
    val planned = m.scheduledAt ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(planned) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val title = "Planned ${formatTimeFrom(planned, now)}" + (m.court?.let { " · Court $it" } ?: "")
    val deltaMin = Math.round((now - planned) / 60_000.0)
    val detail = when {
        deltaMin > 2 -> "Running about ${planDelta(deltaMin)} behind plan — you can still start now"
        deltaMin >= -2 -> "Right on plan — start when ready"
        // "Ahead" only within a few hours of the slot; further out it's just a future slot, not a pace claim.
        deltaMin >= -180 -> "Running about ${planDelta(-deltaMin)} ahead of plan — you can still start now"
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PlanAmber).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CalendarToday, null, tint = PlanAmberInk, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            HText(title, 13.sp, FontWeight.Bold, Ink)
            if (detail != null) HText(detail, 12.sp, FontWeight.Medium, Ink)
        }
    }
}

/** "15 min", or "1 h 20 min" / "2 h" past an hour. */
private fun planDelta(min: Long): String =
    if (min < 60) "$min min" else "${min / 60} h" + (if (min % 60 != 0L) " ${min % 60} min" else "")

@Composable
private fun LineupColumn(
    t: Tournament, name: String, color: Int, ids: List<String>, selected: Set<String>, modifier: Modifier, onToggle: (String) -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
            Dot(teamColor(color), 12.dp)
            Spacer(Modifier.width(8.dp))
            HText(name, 16.sp, FontWeight.Bold, maxLines = 1)
        }
        if (ids.isEmpty()) HText("No players on this team. ${if (t.sport == Sport.FOOTBALL) "Goals" else "Baskets"} will be credited to the team.", 12.sp, FontWeight.Normal, Mute)
        ids.forEach { pid ->
            val on = pid in selected
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) AccentSoft else Surface)
                    .border(BorderStroke(1.dp, if (on) Accent else Line), RoundedCornerShape(12.dp))
                    .clickable { onToggle(pid) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (on) Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(16.dp)) else Spacer(Modifier.width(16.dp))
                Spacer(Modifier.width(8.dp))
                HText(t.player(pid)?.name ?: "?", 14.sp, FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ======================= VIEWER (joined live room) =======================

/** Entry point after "Join a tournament": waits for the room, then shows the normal hub in read-only mode. */
@Composable
fun ViewerScreen(code: String, onBack: () -> Unit, onFixtures: (String) -> Unit) {
    val state by ViewerStore.state.collectAsState()
    BackHandler { onBack() }
    when (val s = state) {
        is JoinState.Live -> TournamentScreen(
            s.tournament.id,
            onBack = onBack,
            onFixtures = { onFixtures(s.tournament.id) },
            onFormat = {},
            onOpenMatch = { _, _ -> },
            onDeleted = {},
            readOnly = true
        )
        else -> ViewerWaitingContent(code, viewerWaitingText(s, code), onBack)
    }
}

private fun viewerWaitingText(s: JoinState?, code: String): String = when (s) {
    is JoinState.NotFound -> "No live tournament with code $code. Check the code with the organiser, or the room may have closed."
    is JoinState.Failed -> "Couldn't join: ${s.message}"
    else -> "Connecting to $code…"
}

@Composable
private fun ViewerWaitingContent(code: String, message: String, onBack: () -> Unit) {
    Screen {
        TopBar("Room $code", onBack)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            HText(message, 14.sp, FontWeight.Medium, Mute, align = TextAlign.Center)
        }
    }
}

// ─── Previews ───────────────────────────────────────────────────────────
// These call the private *Content functions with sample tournaments from PreviewSamples.kt, built by the app's own
// Scheduler. Taps do nothing. "Admin - ..." is the organizer; "Viewer - ..." is someone who joined a live room by code
// (ViewerScreen shows TournamentContent / FixturesContent with readOnly = true; viewers never open a match screen).

private const val PvBg = 0xFFF6F3EE

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Hub - live match, hosting")
@Composable
private fun PreviewHubLive() {
    HoopsTheme {
        TournamentContent(previewActive.copy(roomCode = PreviewRoomCode), {}, {}, {}, { _, _ -> }, readOnly = false)
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Hub - up next")
@Composable
private fun PreviewHubUpNext() {
    HoopsTheme { TournamentContent(previewUpNext, {}, {}, {}, { _, _ -> }, readOnly = false) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Hub - completed")
@Composable
private fun PreviewHubCompleted() {
    HoopsTheme {
        TournamentContent(previewActive.copy(status = TStatus.COMPLETED, championId = "a"), {}, {}, {}, { _, _ -> }, readOnly = false)
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 1100, name = "Admin - Hub - schedule over plan (fix actions, Adjust plan)")
@Composable
private fun PreviewHubOverPlan() {
    HoopsTheme { TournamentContent(previewOverPlan, {}, {}, {}, { _, _ -> }, readOnly = false) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Tab - Standings")
@Composable
private fun PreviewStandingsTab() {
    HoopsTheme { TournamentContent(previewActive, {}, {}, {}, { _, _ -> }, readOnly = false, initialTab = 1) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Tab - Teams")
@Composable
private fun PreviewTeamsTab() {
    HoopsTheme { TournamentContent(previewActive, {}, {}, {}, { _, _ -> }, readOnly = false, initialTab = 2) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Tab - Stats")
@Composable
private fun PreviewStatsTab() {
    HoopsTheme { TournamentContent(previewActive, {}, {}, {}, { _, _ -> }, readOnly = false, initialTab = 3) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Fixtures (reorder)")
@Composable
private fun PreviewFixtures() {
    HoopsTheme { FixturesContent(previewActive, onBack = {}, onOpenMatch = { _, _ -> }, readOnly = false, onReorder = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Fixtures - nothing started (reorder + regenerate)")
@Composable
private fun PreviewFixturesNotStarted() {
    HoopsTheme { FixturesContent(previewNotStarted, onBack = {}, onOpenMatch = { _, _ -> }, readOnly = false, onReorder = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Reorder matches")
@Composable
private fun PreviewReorder() {
    HoopsTheme { ReorderFixturesContent(previewActive, onBack = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Admin - Match ready")
@Composable
private fun PreviewMatchReadyScreen() {
    HoopsTheme {
        val next = previewActive.matches.first { it.status == MatchStatus.SCHEDULED }
        MatchReadyContent(previewActive, next, onBack = {}, onChangeFormat = {}, onStarted = {})
    }
}

/** One card per match state: upcoming, upcoming past the planned end, live, break, tie-break, won, draw, won on a shootout. */
@Composable
private fun MatchCardsEveryStatus(readOnly: Boolean) {
    val upcoming = previewActive.matches.first { it.status == MatchStatus.SCHEDULED }
    val pastPlan = previewOverPlan.matches.first { it.status == MatchStatus.SCHEDULED }
    val cards = listOf(
        previewActive to upcoming,
        previewOverPlan to pastPlan,
        previewMatchIn(MatchStatus.LIVE),
        previewMatchIn(MatchStatus.BREAK),
        previewMatchIn(MatchStatus.TIEBREAK, level = true, shootoutAttempts = 3),
        previewMatchIn(MatchStatus.FINISHED),
        previewMatchIn(MatchStatus.FINISHED, level = true),
        previewMatchIn(MatchStatus.FINISHED, level = true, knockout = true)
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEach { (t, m) -> MatchCard(t, m, { _, _ -> }, readOnly) }
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 1250, name = "Admin - Match cards, every status")
@Composable
private fun PreviewMatchCards() {
    HoopsTheme { MatchCardsEveryStatus(readOnly = false) }
}

@Preview(showBackground = true, backgroundColor = PvBg, name = "Admin - Small pieces")
@Composable
private fun PreviewSmallPieces() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SharePill {}
            LiveHostingRow(PreviewRoomCode) {}
            PastPlanRow(onFix = {})
            Box(Modifier.background(Navy).padding(8.dp)) { PastPlanRow(onFix = {}, dark = true) }
            Tile(Icons.Filled.Groups, "Teams & players", "4 teams · 12 players", Modifier.fillMaxWidth()) {}
        }
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - live, clock running")
@Composable
private fun PreviewViewerHubRunning() {
    HoopsTheme { TournamentContent(previewViewerIn(MatchStatus.LIVE, running = true), {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - live, paused")
@Composable
private fun PreviewHubViewer() {
    HoopsTheme { TournamentContent(previewViewerIn(MatchStatus.LIVE), {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - halftime break")
@Composable
private fun PreviewViewerHubBreak() {
    HoopsTheme { TournamentContent(previewViewerIn(MatchStatus.BREAK, running = true), {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - tie-break shootout")
@Composable
private fun PreviewViewerHubTieBreak() {
    HoopsTheme {
        TournamentContent(previewViewerIn(MatchStatus.TIEBREAK, shootoutAttempts = 3), {}, {}, {}, { _, _ -> }, readOnly = true)
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - up next (no Start button)")
@Composable
private fun PreviewViewerHubUpNext() {
    HoopsTheme { TournamentContent(previewUpNext.copy(roomCode = PreviewRoomCode), {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - schedule over plan (no fix actions)")
@Composable
private fun PreviewViewerHubOverPlan() {
    HoopsTheme { TournamentContent(previewOverPlan.copy(roomCode = PreviewRoomCode), {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Hub - tournament complete")
@Composable
private fun PreviewViewerHubCompleted() {
    HoopsTheme {
        TournamentContent(
            previewActive.copy(status = TStatus.COMPLETED, championId = "a", roomCode = PreviewRoomCode), {}, {}, {}, { _, _ -> }, readOnly = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Tab - Standings (same as admin)")
@Composable
private fun PreviewViewerStandingsTab() {
    HoopsTheme { TournamentContent(previewViewerIn(MatchStatus.LIVE), {}, {}, {}, { _, _ -> }, readOnly = true, initialTab = 1) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Fixtures (no reorder, Upcoming badges)")
@Composable
private fun PreviewViewerFixtures() {
    HoopsTheme { FixturesContent(previewViewerIn(MatchStatus.LIVE), onBack = {}, onOpenMatch = { _, _ -> }, readOnly = true, onReorder = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 800, name = "Viewer - Fixtures - nothing started (no regenerate)")
@Composable
private fun PreviewViewerFixturesNotStarted() {
    HoopsTheme { FixturesContent(previewNotStarted, onBack = {}, onOpenMatch = { _, _ -> }, readOnly = true, onReorder = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 1250, name = "Viewer - Match cards, every status")
@Composable
private fun PreviewViewerMatchCards() {
    HoopsTheme { MatchCardsEveryStatus(readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = 0xFF14171F, widthDp = 412, name = "Viewer - Live score line, every phase")
@Composable
private fun PreviewViewerScoreLines() {
    val lines = listOf(
        previewViewerIn(MatchStatus.LIVE, running = true),
        previewViewerIn(MatchStatus.LIVE),
        previewViewerIn(MatchStatus.BREAK, running = true),
        previewViewerIn(MatchStatus.TIEBREAK, shootoutAttempts = 3),
        previewViewerIn(MatchStatus.TIEBREAK, shootoutAttempts = 4, sport = Sport.FOOTBALL)
    )
    HoopsTheme {
        Column(Modifier.background(Navy).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEach { t ->
                val m = t.realMatches().first {
                    it.status == MatchStatus.LIVE || it.status == MatchStatus.BREAK || it.status == MatchStatus.TIEBREAK
                }
                LiveScoreLine(t, m)
            }
            PastPlanRow(onFix = null, dark = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 500, name = "Viewer - Joining - connecting")
@Composable
private fun PreviewViewerConnecting() {
    HoopsTheme { ViewerWaitingContent(PreviewRoomCode, viewerWaitingText(JoinState.Connecting, PreviewRoomCode), onBack = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 500, name = "Viewer - Joining - room not found")
@Composable
private fun PreviewViewerNotFound() {
    HoopsTheme { ViewerWaitingContent(PreviewRoomCode, viewerWaitingText(JoinState.NotFound, PreviewRoomCode), onBack = {}) }
}

@Preview(showBackground = true, backgroundColor = PvBg, widthDp = 412, heightDp = 500, name = "Viewer - Joining - failed")
@Composable
private fun PreviewViewerFailed() {
    HoopsTheme {
        ViewerWaitingContent(PreviewRoomCode, viewerWaitingText(JoinState.Failed("network unavailable"), PreviewRoomCode), onBack = {})
    }
}
