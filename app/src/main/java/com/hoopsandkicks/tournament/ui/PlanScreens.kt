package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopsandkicks.tournament.HoopsApp
import com.hoopsandkicks.tournament.data.Algorithm
import com.hoopsandkicks.tournament.data.FormatType
import com.hoopsandkicks.tournament.data.GameFormat
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Tournament

// ======================= SCHEDULE ALGORITHM =======================

@Composable
fun AlgorithmScreen(id: String, onBack: () -> Unit, onNext: () -> Unit) {
    val t = observeTournament(id) ?: return
    Screen {
        TopBar("Match schedule", onBack)
        StepBar(5, "Pick an algorithm")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Algorithm.values().forEach { algo ->
                val on = t.algorithm == algo
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) AccentSoft else Surface)
                        .border(BorderStroke(if (on) 2.dp else 1.dp, if (on) Accent else Line), RoundedCornerShape(18.dp))
                        .clickable { HoopsApp.repo.mutate(id) { it.copy(algorithm = algo) } }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(if (on) Accent else Surface)
                                .border(BorderStroke(2.dp, if (on) Accent else Line), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HText(algo.title, 16.sp, FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                                if (algo == Algorithm.GROUP_KO) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge("Default", Accent, Color.White)
                                }
                            }
                            HText(algo.blurb, 13.sp, FontWeight.Normal, Mute)
                        }
                    }
                    if (on && algo == Algorithm.GROUP_KO) {
                        Spacer(Modifier.height(12.dp))
                        val g = t.groups.coerceIn(1, t.maxGroups())
                        val minSize = maxOf(1, t.teams.size / g)
                        val adv = Scheduler.clampedAdvance(t)
                        StepperRow(
                            "Groups", g.toString(), "",
                            { if (g > 1) HoopsApp.repo.mutate(id) { it.copy(groups = g - 1) } },
                            { if (g < t.maxGroups()) HoopsApp.repo.mutate(id) { it.copy(groups = g + 1) } },
                            card = false
                        )
                        StepperRow(
                            "Advance per group", adv.toString(), "",
                            { if (adv > 1) HoopsApp.repo.mutate(id) { it.copy(advance = adv - 1) } },
                            { if (adv < minSize) HoopsApp.repo.mutate(id) { it.copy(advance = adv + 1) } },
                            card = false
                        )
                        Spacer(Modifier.height(6.dp))
                        val sizes = List(g) { i -> t.teams.size / g + if (i < t.teams.size % g) 1 else 0 }
                        val groupsText = if (sizes.distinct().size == 1) "$g groups of ${sizes[0]}" else "$g groups (${sizes.min()}–${sizes.max()} teams)"
                        HText("$groupsText · top $adv advance", 13.sp, FontWeight.Bold)
                        HText(Scheduler.expectedStages(t).joinToString(" → "), 12.sp, FontWeight.Normal, Mute)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            PrimaryButton("Continue: game format", {
                HoopsApp.repo.mutate(id) { it.copy(draftStep = maxOf(it.draftStep, 5)) }
                onNext()
            }, icon = Icons.Filled.CalendarMonth)
        }
    }
}

// ======================= GAME FORMAT =======================

/** The wizard's first real schedule: activate, draw the first stage, slot times/courts (advance re-slots). */
private fun initialSchedule(t: Tournament): Tournament {
    var n = t.copy(status = TStatus.ACTIVE)
    n = n.copy(matches = Scheduler.generate(n))
    n = Scheduler.scheduleTimes(n)
    return Scheduler.advance(n) // also re-slots any round drawn during advance
}

@Composable
fun GameFormatScreen(id: String, wizard: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val t = observeTournament(id) ?: return
    var f by remember(t.id) { mutableStateOf(t.format) }
    val creating = wizard && t.matches.isEmpty()
    // A draft can come back here with fewer than 2 teams (e.g. teams deleted after this step was reached, then the
    // draft reopened, which resumes at this step): there's nothing to schedule, so don't project, and don't finish.
    val enoughTeams = t.teams.size >= 2
    // The whole tournament (every stage, via placeholder results) as it would be slotted with this format and
    // the current start/end/courts: for a new tournament from its first draw, otherwise from the current matches
    // (started/finished ones keep their slots) re-slotted as saving does below. Recomputed live as the format or
    // the window fixes change. Not saved. A completed tournament has nothing left to slot, so it isn't checked.
    val projected = remember(t, f, creating) {
        when {
            !enoughTeams || t.status == TStatus.COMPLETED -> null
            creating -> Scheduler.projectedSchedule(initialSchedule(t.copy(format = f)))
            else -> Scheduler.projectedSchedule(t.copy(format = f))
        }
    }
    val fits = projected == null || Scheduler.fits(projected)
    val overrun = if (projected == null) 0L else Scheduler.scheduleOverrunMinutes(projected)
    val canSave = fits && (enoughTeams || !creating)

    Screen {
        TopBar("Game format", onBack)
        if (wizard) StepBar(6, "Configure the game")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (creating && !enoughTeams) {
                HText("Add at least 2 teams to schedule the tournament. Go back to the Teams step.", 13.sp, FontWeight.Medium, Accent)
            }
            if (!fits) {
                // Blocking (new tournament or a format change): can't save until every stage fits between the start and end time.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AccentSoft).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HText("Schedule doesn't fit", 14.sp, FontWeight.Bold, Accent)
                    HText(
                        "Played through every stage, the schedule ends about $overrun min after your end time " +
                            "(${formatDateTime(t.endAt)}). The plan can't run past the end time, so extend it, add a court, " +
                            "or shorten the games below.",
                        13.sp, FontWeight.Medium, Ink
                    )
                    ScheduleFixActions(t, overrun)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormatCard(
                    "Continuous game", "One running clock, no break.", Icons.Filled.Schedule,
                    f.type == FormatType.CONTINUOUS, Modifier.weight(1f)
                ) {
                    if (f.type != FormatType.CONTINUOUS) f = f.copy(type = FormatType.CONTINUOUS, periodMin = (f.periodMin * 2).coerceAtMost(90))
                }
                FormatCard(
                    "Two halves + break", "Two periods with a halftime.", Icons.Filled.Pause,
                    f.type == FormatType.HALVES, Modifier.weight(1f)
                ) {
                    if (f.type != FormatType.HALVES) f = f.copy(type = FormatType.HALVES, periodMin = (f.periodMin / 2).coerceAtLeast(1))
                }
            }
            HCard(padding = 12.dp) {
                val halves = f.type == FormatType.HALVES
                StepperRow(
                    if (halves) "Half length" else "Game length", f.periodMin.toString(), "min",
                    { if (f.periodMin > 1) f = f.copy(periodMin = f.periodMin - 1) },
                    { if (f.periodMin < 90) f = f.copy(periodMin = f.periodMin + 1) }, card = false
                )
                if (halves) {
                    Divider1()
                    StepperRow(
                        "Break length", f.breakMin.toString(), "min",
                        { if (f.breakMin > 1) f = f.copy(breakMin = f.breakMin - 1) },
                        { if (f.breakMin < 30) f = f.copy(breakMin = f.breakMin + 1) }, card = false
                    )
                }
            }
            HCard(padding = 14.dp) {
                if (t.sport == Sport.BASKETBALL) {
                    HText("Basket values", 13.sp, FontWeight.Bold, Mute)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("1 pt", f.allow1, { f = f.copy(allow1 = !f.allow1 || !(f.allow2 || f.allow3)) })
                        Chip("2 pt", f.allow2, { f = f.copy(allow2 = !f.allow2 || !(f.allow1 || f.allow3)) })
                        Chip("3 pt", f.allow3, { f = f.copy(allow3 = !f.allow3 || !(f.allow1 || f.allow2)) })
                    }
                    Divider1()
                } else {
                    HText("Every goal is worth 1 point.", 13.sp, FontWeight.Medium, Mute)
                    Divider1()
                }
                SwitchRow("Different length for Finals", f.finalsDifferent) { f = f.copy(finalsDifferent = it) }
                if (f.finalsDifferent) {
                    StepperRow(
                        if (f.type == FormatType.HALVES) "Finals half length" else "Finals game length", f.finalsPeriodMin.toString(), "min",
                        { if (f.finalsPeriodMin > 1) f = f.copy(finalsPeriodMin = f.finalsPeriodMin - 1) },
                        { if (f.finalsPeriodMin < 90) f = f.copy(finalsPeriodMin = f.finalsPeriodMin + 1) }, card = false
                    )
                }
            }
            // No tie-breaker settings: a tie always goes to the sport's fixed shootout (see data/Shootout.kt).
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bg)
                    .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (t.sport == Sport.FOOTBALL) Icons.Filled.SportsSoccer else Icons.Filled.SportsBasketball,
                    null, tint = Mute, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                HText(
                    if (t.sport == Sport.FOOTBALL)
                        "Tied at full time always goes to a free-kick shootout — no extra time, no timeouts to configure."
                    else
                        "Tied at full time always goes to a free-throw shootout — no overtime, no timeouts to configure.",
                    13.sp, FontWeight.Medium, Mute, Modifier.weight(1f)
                )
            }
            HText(f.summary(), 13.sp, FontWeight.Medium, Mute)
            if (!wizard) HText("Changes apply to matches that have not started yet.", 12.sp, FontWeight.Normal, Mute)
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            if (creating && !enoughTeams) HText("Add at least 2 teams to finish.", 12.sp, FontWeight.Medium, Accent)
            else if (!fits) HText("Fix the schedule above to ${if (creating) "finish" else "save"}: it runs $overrun min past the end time.", 12.sp, FontWeight.Medium, Accent)
            PrimaryButton(if (wizard) "Save & finish" else "Save format", {
                var done = true
                HoopsApp.repo.mutate(id) { tt ->
                    var n = tt.copy(format = f, draftStep = 6)
                    if (wizard && tt.matches.isEmpty()) {
                        if (tt.teams.size < 2) {
                            // Nothing to draw: keep it a draft (the button is disabled too; this guards a stale screen).
                            done = false
                            n = tt.copy(format = f)
                        } else {
                            n = initialSchedule(n)
                            // Re-check on the saved state: never finish with a plan that runs past the end time.
                            if (!Scheduler.fits(Scheduler.projectedSchedule(n))) {
                                done = false
                                n = tt.copy(format = f)
                            }
                        }
                    } else {
                        // Format lengths changed: re-slot times/courts of matches not started yet (no-op if no start time is set).
                        n = Scheduler.scheduleTimes(n)
                        // Same rule as a new tournament: never save a format whose whole plan runs past the end time.
                        if (tt.status != TStatus.COMPLETED && !Scheduler.fits(Scheduler.projectedSchedule(n))) {
                            done = false
                            n = tt
                        }
                    }
                    n
                }
                if (done) onDone()
            }, icon = if (wizard) Icons.Filled.Check else Icons.Filled.ChevronRight, enabled = canSave)
        }
    }
}

@Composable
private fun FormatCard(title: String, blurb: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) AccentSoft else Surface)
            .border(BorderStroke(if (on) 2.dp else 1.dp, if (on) Accent else Line), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = if (on) Accent else Ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        HText(title, 15.sp, FontWeight.Bold)
        HText(blurb, 12.sp, FontWeight.Normal, Mute)
    }
}
