package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hoopsandkicks.tournament.HoopsApp
import com.hoopsandkicks.tournament.data.Player
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Team
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.TStatus.ACTIVE
import com.hoopsandkicks.tournament.data.TStatus.COMPLETED
import com.hoopsandkicks.tournament.data.TStatus.DRAFT
import com.hoopsandkicks.tournament.data.newId
import com.hoopsandkicks.tournament.data.remote.RemoteSync
import com.hoopsandkicks.tournament.data.remote.ViewerStore

// ======================= HOME =======================

@Composable
fun HomeScreen(
    onNew: () -> Unit,
    onOpen: (Tournament) -> Unit,
    onJoin: () -> Unit = {},
    onResumeWatching: (String) -> Unit = {}
) {
    val all by HoopsApp.repo.tournaments.collectAsState()
    // A code saved from a previous "Join a tournament" — lets a viewer who pressed back, or fully
    // closed the app, get straight back into the same room instead of re-typing the code.
    val resumeCode = remember { ViewerStore.lastCode() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shown = all.filter {
        when (tab) {
            0 -> it.status == ACTIVE
            1 -> it.status == DRAFT
            else -> it.status == COMPLETED
        }
    }
    Screen {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Accent),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.SportsBasketball, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        DisplayText("HOOPS & KICKS", 26.sp)
                        HText("Tournament manager", 13.sp, FontWeight.Medium, Mute)
                    }
                }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Active", tab == 0, { tab = 0 })
                    Chip("Drafts", tab == 1, { tab = 1 })
                    Chip("Completed", tab == 2, { tab = 2 })
                }
                if (resumeCode != null) {
                    ResumeWatchingRow(
                        resumeCode,
                        onClick = { onResumeWatching(resumeCode) },
                        onJoinDifferent = onJoin
                    )
                } else {
                    JoinByCodeButton(onJoin)
                }
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        HText(
                            when (tab) {
                                0 -> "No active tournaments. Tap New tournament to start one."
                                1 -> "No drafts."
                                else -> "No completed tournaments yet."
                            },
                            14.sp, FontWeight.Medium, Mute, align = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown, key = { it.id }) { t -> TournamentCard(t) { onOpen(t) } }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Accent)
                    .clickable(onClick = onNew)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                HText("New tournament", 16.sp, FontWeight.Bold, Color.White)
            }
        }
    }

}

/** Home: dashed full-width entry to the join-by-code screen (Main design). */
@Composable
private fun JoinByCodeButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp).height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = Line,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(16.dp.toPx() - w / 2),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                )
            }
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Visibility, null, tint = Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        HText("Have a room code? Join a tournament", 14.sp, FontWeight.Bold, Ink, maxLines = 1)
    }
}

/**
 * Shown instead of [JoinByCodeButton] when [ViewerStore] has a room this device watched before — lets a
 * viewer who pressed back, or fully closed the app, get straight back in without re-typing the code.
 */
@Composable
private fun ResumeWatchingRow(code: String, onClick: () -> Unit, onJoinDifferent: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AccentSoft)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Visibility, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            HText("Resume watching room $code", 14.sp, FontWeight.Bold, Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Filled.ChevronRight, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp).clickable(onClick = onJoinDifferent),
        ) {
            HText("Not this one? Join a different room", 12.sp, FontWeight.Medium, Mute, maxLines = 1)
        }
    }
}

// ======================= JOIN (viewer) =======================

/**
 * Join a live room as a read-only viewer (JoinTournament design): a 6-box code entry and an explanation of what a
 * viewer can do. [onJoin] receives the normalised code; the Firebase side lives in ViewerStore / RemoteSync.
 */
@Composable
fun JoinTournamentScreen(onBack: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: IllegalStateException) {} }
    val ready = code.length == RemoteSync.CODE_LENGTH
    Screen {
        TopBar("Join a tournament", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                HText("Enter a room code", 16.sp, FontWeight.Bold)
                HText("Ask the organizer for the ${RemoteSync.CODE_LENGTH}-character code they shared when they went live", 13.sp, FontWeight.Medium, Mute)
            }
            BasicTextField(
                value = code,
                onValueChange = { code = RemoteSync.normalizeCode(it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (ready) onJoin(code) }),
                decorationBox = { _ ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until RemoteSync.CODE_LENGTH) {
                            val ch = code.getOrNull(i)
                            val active = i == code.length
                            Box(
                                Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(12.dp)).background(Surface)
                                    .border(BorderStroke(if (active) 2.dp else 1.dp, if (active) Accent else Line), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) { DisplayText(ch?.toString() ?: "", 32.sp, Ink) }
                        }
                    }
                }
            )
            HCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Visibility, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        HText("You will join as a viewer", 15.sp, FontWeight.Bold)
                        HText(
                            "You can watch scores, the clock and standings update live. Only the organizer can score or manage the tournament.",
                            13.sp, FontWeight.Normal, Mute
                        )
                    }
                }
            }
        }
        BottomBar {
            PrimaryButton("Join tournament", { onJoin(code) }, icon = Icons.Filled.ChevronRight, enabled = ready)
        }
    }
}

@Composable
private fun TournamentCard(t: Tournament, onClick: () -> Unit) {
    HCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HText(t.sport.emoji, 20.sp)
                    Spacer(Modifier.width(6.dp))
                    DisplayText(t.name, 26.sp)
                }
                val algo = t.algorithm.title
                HText("${t.players.size} players · ${t.teams.size} teams · $algo", 13.sp, FontWeight.Normal, Mute)
            }
            Spacer(Modifier.width(8.dp))
            when (t.status) {
                ACTIVE -> Badge("In progress", GreenSoft, Green)
                DRAFT -> Badge("Draft", Line, Mute)
                COMPLETED -> Badge("Completed", Line, Mute)
            }
        }
        Spacer(Modifier.height(10.dp))
        when (t.status) {
            DRAFT -> HText("Draft · ${t.players.size} of ${t.playerTarget} players added", 13.sp, FontWeight.Medium, Mute)
            COMPLETED -> HText("Champion: ${t.teamName(t.championId)}", 14.sp, FontWeight.Bold)
            ACTIVE -> {
                val total = t.realMatches().size
                val done = t.playedCount()
                Row {
                    HText(t.currentStageName(), 13.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                    HText("$done of $total played", 13.sp, FontWeight.Normal, Mute)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (total == 0) 0f else done.toFloat() / total)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Accent)
                    )
                }
            }
        }
    }
}

// ======================= CREATE =======================

@Composable
fun CreateTournamentScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    var sport by rememberSaveable { mutableStateOf(Sport.BASKETBALL) }
    var name by rememberSaveable { mutableStateOf("") }
    var startAt by rememberSaveable { mutableLongStateOf(0L) }
    var endAt by rememberSaveable { mutableLongStateOf(0L) }
    var courts by rememberSaveable { mutableIntStateOf(1) }
    var bufferMin by rememberSaveable { mutableIntStateOf(10) }
    // 0 = no picker open, 1 = picking start, 2 = picking end.
    var picking by remember { mutableIntStateOf(0) }
    var venue by rememberSaveable { mutableStateOf("") }
    var players by rememberSaveable { mutableIntStateOf(24) }
    var teams by rememberSaveable { mutableIntStateOf(6) }
    // Both unset is allowed (times can't be slotted yet); otherwise the end must come after the start.
    val timesValid = (startAt <= 0L && endAt <= 0L) || (startAt > 0L && endAt > startAt)
    val teamsValid = teams >= 2 && players >= teams
    val valid = teamsValid && timesValid

    Screen {
        TopBar("New tournament", onBack)
        StepBar(1, "Details")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HText("Sport", 13.sp, FontWeight.Bold, Mute)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Sport.values().forEach { s ->
                    val on = sport == s
                    Row(
                        Modifier.weight(1f).height(56.dp)
                            .background(if (on) AccentSoft else Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .border(androidx.compose.foundation.BorderStroke(if (on) 2.dp else 1.dp, if (on) Accent else Line), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .clickable { sport = s },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HText(s.emoji, 20.sp)
                        Spacer(Modifier.width(8.dp))
                        HText(s.displayName, 15.sp, FontWeight.Bold, if (on) Ink else Mute)
                    }
                }
            }
            LabeledField("Tournament name", name, { name = it })
            DateTimeRow("Starts", startAt, "Pick a start date") { picking = 1 }
            DateTimeRow("Ends", endAt, "Pick an end time") { picking = 2 }
            if (!timesValid) {
                HText(
                    if (startAt <= 0L) "Pick a start time first." else "The end time must be after the start time.",
                    12.sp, FontWeight.Medium, Accent
                )
            }
            StepperRow("Courts", courts.toString(), "courts", { if (courts > 1) courts -= 1 }, { if (courts < 20) courts += 1 }, "Games that can run at the same time")
            StepperRow("Minutes between matches", bufferMin.toString(), "min", { if (bufferMin > 0) bufferMin -= 1 }, { if (bufferMin < 60) bufferMin += 1 }, "Changeover time on each court")
            LabeledField("Venue (optional)", venue, { venue = it })
            StepperRow("Players (X)", players.toString(), "players", { if (players > 2) players -= 1 }, { if (players < 200) players += 1 }, "Everyone taking part")
            StepperRow("Teams (Y)", teams.toString(), "teams", { if (teams > 2) teams -= 1 }, { if (teams < 32) teams += 1 }, "How many teams to form")
            val summary = if (!teamsValid) "You need at least as many players as teams."
            else if (players % teams == 0) "$players players into $teams teams = ${players / teams} per team, evenly split"
            else "$players players into $teams teams = ${players / teams}–${players / teams + 1} per team"
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AccentSoft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { HText(summary, 13.sp, FontWeight.Medium) }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            PrimaryButton("Continue: add players", {
                val t = Tournament(
                    id = newId(),
                    name = name.trim().ifEmpty { "Tournament" },
                    startAt = startAt,
                    endAt = endAt,
                    courts = courts,
                    matchBufferMin = bufferMin,
                    venue = venue,
                    playerTarget = players,
                    teamTarget = teams,
                    sport = sport,
                    format = sport.defaultFormat(),
                    draftStep = 1,
                    createdAt = System.currentTimeMillis()
                )
                HoopsApp.repo.save(t)
                onCreated(t.id)
            }, icon = Icons.Filled.ChevronRight, enabled = valid)
        }
    }

    if (picking != 0) {
        val editingStart = picking == 1
        val initial = when {
            editingStart && startAt > 0L -> startAt
            !editingStart && endAt > 0L -> endAt
            !editingStart && startAt > 0L -> startAt + 2 * 60 * 60_000L
            else -> nextWholeHourMs()
        }
        DateTimePickerFlow(
            initialMs = initial,
            onDismiss = { picking = 0 },
            onPicked = { ms ->
                if (editingStart) {
                    startAt = ms
                    // Convenience: a fresh (or now-invalid) end defaults to two hours after the new start.
                    if (endAt <= 0L || endAt <= ms) endAt = ms + 2 * 60 * 60_000L
                } else {
                    endAt = ms
                }
                picking = 0
            }
        )
    }
}

/** Tappable field showing a date+time (or a placeholder when unset). */
@Composable
private fun DateTimeRow(label: String, epochMs: Long, placeholder: String, onClick: () -> Unit) {
    Column {
        HText(label, 13.sp, FontWeight.Bold, Mute)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HText(
                if (epochMs > 0L) formatDateTime(epochMs) else placeholder,
                16.sp, FontWeight.Normal, if (epochMs > 0L) Ink else Mute,
                Modifier.weight(1f), maxLines = 1
            )
            Icon(Icons.Filled.ChevronRight, null, tint = Mute, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Material3 date picker, then (on confirm) a time picker; reports the combined local date+time in epoch ms.
 * Also used by the "Fix schedule" actions (ScheduleFixActions in TournamentScreens.kt) to extend the end time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerFlow(initialMs: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }
    val dateUtc = pickedDateUtc
    if (dateUtc == null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOfLocalDay(initialMs))
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { dateState.selectedDateMillis?.let { pickedDateUtc = it } },
                    enabled = dateState.selectedDateMillis != null
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = localHour(initialMs), initialMinute = localMinute(initialMs))
        TimePickerDialogWrapper(
            onDismiss = onDismiss,
            onConfirm = { onPicked(combineDateAndTime(dateUtc, timeState.hour, timeState.minute)) }
        ) {
            TimePicker(state = timeState)
        }
    }
}

/** Material3 (at this BOM) ships no TimePickerDialog, so this is a minimal one in the app's own style. */
@Composable
private fun TimePickerDialogWrapper(onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HText("Pick a time", 13.sp, FontWeight.Bold, Mute, Modifier.fillMaxWidth())
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton("OK", onConfirm, Modifier.weight(1f))
            }
        }
    }
}

// ======================= PLAYERS =======================

@Composable
fun PlayersScreen(id: String, onBack: () -> Unit, onNext: () -> Unit) {
    val t = observeTournament(id) ?: return
    val positions = t.sport.positions
    var name by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable(t.sport) { mutableStateOf(positions.first()) }
    var skill by rememberSaveable { mutableIntStateOf(3) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var showPaste by remember { mutableStateOf(false) }

    fun add(n: String) {
        val clean = n.trim()
        if (clean.isEmpty()) return
        HoopsApp.repo.mutate(id) { it.copy(players = it.players + Player(newId(), clean, position, skill)) }
    }

    val need = maxOf(2, t.teams.size, t.teamTarget)
    val canContinue = t.players.size >= need

    Screen {
        TopBar("Players", onBack)
        StepBar(2, "Add players")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LabeledField(
                "Player name", name, { name = it }, Modifier.weight(1f),
                imeAction = ImeAction.Done,
                onDone = { add(name); name = "" }
            )
            Box(
                Modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).background(Ink)
                    .clickable { add(name); name = "" }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    HText("Add", 15.sp, FontWeight.Bold, Color.White)
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            positions.forEach { p -> Chip(p, position == p, { position = p }) }
        }
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HText("Skill (for balanced split)", 13.sp, FontWeight.Medium, Mute, Modifier.weight(1f))
            SkillDots(skill) { skill = it }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HText("${t.players.size} of ${t.playerTarget} added", 14.sp, FontWeight.Medium, Mute)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line)) {
                Box(
                    Modifier.fillMaxWidth((t.players.size.toFloat() / t.playerTarget).coerceIn(0f, 1f)).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Green)
                )
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("All") + positions).forEach { p -> Chip(p, filter == p, { filter = p }) }
        }
        val shown = t.players.withIndex().filter { filter == "All" || it.value.position == filter }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            items(shown, key = { it.value.id }) { (idx, p) ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Line), contentAlignment = Alignment.Center) {
                        HText("${idx + 1}", 13.sp, FontWeight.Bold, Mute)
                    }
                    Spacer(Modifier.width(12.dp))
                    HText(p.name, 16.sp, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    Badge(p.position, Line, Mute)
                    Spacer(Modifier.width(8.dp))
                    SkillDots(p.skill, small = true) { s ->
                        HoopsApp.repo.mutate(id) { tt -> tt.copy(players = tt.players.map { if (it.id == p.id) it.copy(skill = s) else it }) }
                    }
                    Box(
                        Modifier.size(44.dp).clickable {
                            HoopsApp.repo.mutate(id) { tt ->
                                tt.copy(
                                    players = tt.players.filter { it.id != p.id },
                                    teams = tt.teams.map { tm -> tm.copy(playerIds = tm.playerIds.filter { x -> x != p.id }) }
                                )
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Delete, "Remove ${p.name}", tint = Mute, modifier = Modifier.size(20.dp)) }
                }
                Divider1()
            }
        }
        BottomBar {
            if (!canContinue) HText("Add at least $need players to continue.", 12.sp, FontWeight.Medium, Mute)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Paste list", { showPaste = true }, icon = Icons.AutoMirrored.Filled.FormatListBulleted)
                PrimaryButton("Continue: teams", {
                    HoopsApp.repo.mutate(id) { it.copy(draftStep = maxOf(it.draftStep, 2)) }
                    onNext()
                }, Modifier.weight(1f), icon = Icons.Filled.ChevronRight, enabled = canContinue)
            }
        }
    }

    if (showPaste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("Paste player names") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("One name per line") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val names = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    HoopsApp.repo.mutate(id) { tt -> tt.copy(players = tt.players + names.map { Player(newId(), it, position, skill) }) }
                    showPaste = false
                }) { Text("Add all") }
            },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SkillDots(value: Int, small: Boolean = false, onChange: (Int) -> Unit) {
    val d = if (small) 8.dp else 14.dp
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange(if (value >= 5) 1 else value + 1) }.padding(horizontal = 6.dp, vertical = if (small) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 3.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) Box(Modifier.size(d).clip(CircleShape).background(if (i <= value) Accent else Line))
    }
}

// ======================= TEAMS =======================

private val DefaultTeamNames = listOf(
    "Blaze", "Titans", "Hawks", "Wolves", "Vipers", "Storm", "Rockets", "Knights", "Dragons", "Lions",
    "Panthers", "Cobras", "Bulls", "Raptors", "Falcons", "Sharks"
)

private fun nextTeamName(existing: List<Team>): String {
    val used = existing.map { it.name.lowercase() }.toSet()
    return DefaultTeamNames.firstOrNull { it.lowercase() !in used } ?: "Team ${existing.size + 1}"
}

private fun nextColor(existing: List<Team>): Int {
    val used = existing.map { it.color % TeamPalette.size }.toSet()
    return (0 until TeamPalette.size).firstOrNull { it !in used } ?: existing.size % TeamPalette.size
}

@Composable
fun TeamsScreen(id: String, onBack: () -> Unit, onNext: () -> Unit) {
    val t = observeTournament(id) ?: return
    var name by rememberSaveable { mutableStateOf("") }
    var colorIdx by rememberSaveable { mutableIntStateOf(-1) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val suggestion = nextTeamName(t.teams)
    val pickedColor = if (colorIdx >= 0) colorIdx else nextColor(t.teams)

    fun clearAssignments(list: List<Team>) = list.map { it.copy(playerIds = emptyList()) }

    fun commit() {
        val n = name.trim().ifEmpty { suggestion }
        val ed = editing
        if (ed != null) {
            HoopsApp.repo.mutate(id) { tt -> tt.copy(teams = tt.teams.map { if (it.id == ed) it.copy(name = n, color = pickedColor) else it }) }
        } else {
            HoopsApp.repo.mutate(id) { tt ->
                val added = tt.teams + Team(newId(), n, pickedColor)
                tt.copy(teams = clearAssignments(added), teamTarget = maxOf(tt.teamTarget, added.size))
            }
        }
        name = ""; colorIdx = -1; editing = null
    }

    Screen {
        TopBar("Teams", onBack)
        StepBar(3, "Create teams")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LabeledField(
                if (editing != null) "Edit team name" else "Team name", name, { name = it },
                imeAction = ImeAction.Done, onDone = { commit() }
            )
            if (editing == null) HText("Leave blank to use “$suggestion”", 12.sp, FontWeight.Normal, Mute)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeamPalette.forEachIndexed { i, c ->
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(c)
                            .border(if (i == pickedColor) BorderStroke(3.dp, Ink) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(20.dp))
                            .clickable { colorIdx = i }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (editing != null) "Save team" else "Add team", { commit() }, Modifier.weight(1f), icon = if (editing != null) Icons.Filled.Edit else Icons.Filled.Add)
                if (editing != null) {
                    SecondaryButton("Cancel", { editing = null; name = ""; colorIdx = -1 }, Modifier.height(56.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HText("${t.teams.size} of ${t.teamTarget} teams", 14.sp, FontWeight.Medium, Mute, Modifier.weight(1f))
                if (t.teams.size >= t.teamTarget) Badge("Ready", GreenSoft, Green)
            }
            t.teams.forEachIndexed { i, tm ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
                        .border(BorderStroke(1.dp, Line), RoundedCornerShape(16.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(teamColor(tm.color)), contentAlignment = Alignment.Center) {
                        DisplayText(tm.name.take(1).uppercase(), 20.sp, Color.White, align = TextAlign.Center)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        HText(tm.name, 17.sp, FontWeight.Bold, maxLines = 1)
                        HText("Team ${i + 1}", 12.sp, FontWeight.Normal, Mute)
                    }
                    IconCircle(Icons.Filled.Edit, "Edit ${tm.name}", {
                        editing = tm.id; name = tm.name; colorIdx = tm.color % TeamPalette.size
                    })
                    Spacer(Modifier.width(8.dp))
                    IconCircle(Icons.Filled.Delete, "Delete ${tm.name}", {
                        HoopsApp.repo.mutate(id) { tt -> tt.copy(teams = clearAssignments(tt.teams.filter { it.id != tm.id })) }
                        if (editing == tm.id) { editing = null; name = ""; colorIdx = -1 }
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Auto-name", {
                    HoopsApp.repo.mutate(id) { tt ->
                        var list = tt.teams
                        while (list.size < tt.teamTarget) {
                            list = list + Team(newId(), nextTeamName(list), nextColor(list))
                        }
                        tt.copy(teams = clearAssignments(list))
                    }
                }, icon = Icons.Filled.Bolt)
                PrimaryButton("Continue: split players", {
                    HoopsApp.repo.mutate(id) { it.copy(draftStep = maxOf(it.draftStep, 3)) }
                    onNext()
                }, Modifier.weight(1f), icon = Icons.Filled.ChevronRight, enabled = t.teams.size >= 2)
            }
        }
    }
}

// ======================= SPLIT =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(id: String, onBack: () -> Unit, onNext: () -> Unit) {
    val t = observeTournament(id) ?: return
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    fun apply(map: Map<String, List<String>>) {
        HoopsApp.repo.mutate(id) { tt -> tt.copy(teams = tt.teams.map { it.copy(playerIds = map[it.id] ?: emptyList()) }) }
        selected = null
    }

    fun shuffle(m: Int) {
        val teamIds = t.teams.map { it.id }
        when (m) {
            0 -> apply(Scheduler.randomSplit(t.players.map { it.id }, teamIds))
            1 -> apply(Scheduler.balancedSplit(t.players, teamIds))
            else -> apply(emptyMap())
        }
    }

    LaunchedEffect(Unit) {
        if (t.teams.all { it.playerIds.isEmpty() }) shuffle(0)
    }

    val assigned = t.teams.flatMap { it.playerIds }.toSet()
    val pool = t.players.filter { it.id !in assigned }

    Screen {
        TopBar("Split into teams", onBack)
        StepBar(4, "Split players")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Line).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Random", "Balanced", "Manual").forEachIndexed { i, l ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (mode == i) Surface else Color.Transparent)
                            .clickable { mode = i; if (i < 2) shuffle(i) },
                        contentAlignment = Alignment.Center
                    ) { HText(l, 14.sp, FontWeight.Bold, if (mode == i) Ink else Mute) }
                }
            }
            HText(
                when (mode) {
                    0 -> "Random draw across ${t.teams.size} teams. Tap a player to pull them out, then tap a team to place them."
                    1 -> "Balanced uses each player's 1–5 skill rating so teams end up evenly matched."
                    else -> "Manual: tap a player to pick them up, then tap the team to place them."
                },
                13.sp, FontWeight.Normal, Mute
            )
            if (pool.isNotEmpty()) {
                HCard {
                    HText("Unassigned (${pool.size})", 13.sp, FontWeight.Bold, Mute)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pool.forEach { p ->
                            Chip(p.name, selected == p.id, { selected = if (selected == p.id) null else p.id })
                        }
                    }
                }
            }
            t.teams.chunked(2).forEach { rowTeams ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowTeams.forEach { tm ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Surface)
                                .border(BorderStroke(if (selected != null) 2.dp else 1.dp, if (selected != null) Accent else Line), RoundedCornerShape(14.dp))
                                .clickable(enabled = selected != null) {
                                    val pid = selected
                                    if (pid != null) {
                                        HoopsApp.repo.mutate(id) { tt ->
                                            tt.copy(teams = tt.teams.map {
                                                if (it.id == tm.id) it.copy(playerIds = it.playerIds + pid)
                                                else it.copy(playerIds = it.playerIds.filter { x -> x != pid })
                                            })
                                        }
                                        selected = null
                                    }
                                }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().background(teamColor(tm.color)).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HText(tm.name, 14.sp, FontWeight.Bold, Color.White, Modifier.weight(1f), maxLines = 1)
                                HText("${tm.playerIds.size}", 13.sp, FontWeight.Bold, Color.White)
                            }
                            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                tm.playerIds.forEach { pid ->
                                    val name = t.player(pid)?.name ?: "?"
                                    Box(
                                        Modifier.fillMaxWidth().height(36.dp)
                                            .clickable { selected = pid; apply2(id, pid) }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) { HText(name, 13.sp, FontWeight.Medium, maxLines = 1) }
                                }
                            }
                        }
                    }
                    if (rowTeams.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            if (pool.isNotEmpty()) HText("Place all ${pool.size} unassigned players to continue.", 12.sp, FontWeight.Medium, Mute)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(if (mode == 2) "Clear all" else "Shuffle again", { shuffle(mode) }, icon = Icons.Filled.Shuffle)
                PrimaryButton("Lock teams", {
                    HoopsApp.repo.mutate(id) { it.copy(draftStep = maxOf(it.draftStep, 4)) }
                    onNext()
                }, Modifier.weight(1f), icon = Icons.Filled.Lock, enabled = pool.isEmpty() && t.teams.size >= 2)
            }
        }
    }
}

/** Removes a player from whichever team holds them (they then show in the unassigned pool). */
private fun apply2(id: String, playerId: String) {
    HoopsApp.repo.mutate(id) { tt -> tt.copy(teams = tt.teams.map { it.copy(playerIds = it.playerIds.filter { x -> x != playerId }) }) }
}

// ─── Previews ───────────────────────────────────────────────────────────
// HomeScreen, CreateTournamentScreen, PlayersScreen, TeamsScreen and SplitScreen read the real
// database (HoopsApp.repo), which doesn't exist inside the Design tab, so only the pieces that take
// plain data can be previewed here.

private val previewPlayers = listOf(
    Player("p1", "Alex", "Guard", 4), Player("p2", "Sam", "Forward", 3),
    Player("p3", "Jordan", "Center", 5), Player("p4", "Riley", "Guard", 2)
)

private val previewTeams = listOf(
    Team("t1", "Red Hawks", 0, listOf("p1", "p2")),
    Team("t2", "Blue Jays", 1, listOf("p3", "p4"))
)

private val previewDraft = Tournament(
    id = "draft", name = "Summer Hoops", players = previewPlayers, playerTarget = 8,
    status = TStatus.DRAFT, sport = Sport.BASKETBALL
)

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, name = "Join screen")
@Composable
private fun PreviewJoinTournamentScreen() {
    HoopsTheme { JoinTournamentScreen(onBack = {}, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, name = "Home join buttons")
@Composable
private fun PreviewHomeJoinRows() {
    HoopsTheme {
        Column {
            JoinByCodeButton(onClick = {})
            ResumeWatchingRow("K7M2QX", onClick = {}, onJoinDifferent = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, name = "Tournament cards")
@Composable
private fun PreviewTournamentCards() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TournamentCard(previewDraft, onClick = {})
            TournamentCard(
                previewDraft.copy(name = "City Cup", status = TStatus.ACTIVE, teams = previewTeams),
                onClick = {}
            )
            TournamentCard(
                previewDraft.copy(name = "Spring Kicks", sport = Sport.FOOTBALL, status = TStatus.COMPLETED,
                    teams = previewTeams, championId = "t1"),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, name = "Date row and skill dots")
@Composable
private fun PreviewDateRowAndSkillDots() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTimeRow("Starts", 0L, "Pick a start time", onClick = {})
            DateTimeRow("Ends", 1_750_000_000_000L, "Pick an end time", onClick = {})
            SkillDots(value = 3, onChange = {})
            SkillDots(value = 5, small = true, onChange = {})
        }
    }
}
