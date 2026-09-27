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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopsandkicks.tournament.HoopsApp
import com.hoopsandkicks.tournament.data.Algorithm
import com.hoopsandkicks.tournament.data.FormatType
import com.hoopsandkicks.tournament.data.GameFormat
import com.hoopsandkicks.tournament.data.Player
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.Team
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Tournament

// ======================= SCHEDULE ALGORITHM =======================

private typealias PlanMutate = ((Tournament) -> Tournament) -> Unit

@Composable
fun AlgorithmScreen(id: String, onBack: () -> Unit, onNext: () -> Unit) {
    val t = observeTournament(id) ?: return
    AlgorithmContent(t, { change -> HoopsApp.repo.mutate(id, change) }, onBack, onNext)
}

private const val MaxPoints = 10

/** Win / tie / loss points for the group or league stage. Knockouts, semi-finals and finals never award points. */
@Composable
private fun PointsSettings(t: Tournament, mutate: PlanMutate) {
    Spacer(Modifier.height(12.dp))
    HText("Points per match", 13.sp, FontWeight.Bold)
    HText(
        "Group and round-robin matches only. A level score ends the match as a tie. Knockouts, semi-finals and finals have no points.",
        12.sp, FontWeight.Normal, Mute
    )
    StepperRow(
        "Win", t.winPoints.toString(), "pts",
        { if (t.winPoints > 0) mutate { it.copy(winPoints = it.winPoints - 1) } },
        { if (t.winPoints < MaxPoints) mutate { it.copy(winPoints = it.winPoints + 1) } },
        card = false
    )
    StepperRow(
        "Tie", t.tiePoints.toString(), "pts",
        { if (t.tiePoints > 0) mutate { it.copy(tiePoints = it.tiePoints - 1) } },
        { if (t.tiePoints < MaxPoints) mutate { it.copy(tiePoints = it.tiePoints + 1) } },
        card = false
    )
    StepperRow(
        "Loss", t.lossPoints.toString(), "pts",
        { if (t.lossPoints > 0) mutate { it.copy(lossPoints = it.lossPoints - 1) } },
        { if (t.lossPoints < MaxPoints) mutate { it.copy(lossPoints = it.lossPoints + 1) } },
        card = false
    )
}

@Composable
private fun AlgorithmContent(t: Tournament, mutate: PlanMutate, onBack: () -> Unit, onNext: () -> Unit) {
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
                        .clickable { mutate { it.copy(algorithm = algo) } }
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
                    if (on && algo == Algorithm.ROUND_ROBIN) {
                        val semisOn = Scheduler.rrSemisOn(t)
                        val finalOn = Scheduler.rrFinalOn(t)
                        Spacer(Modifier.height(8.dp))
                        CheckRow(
                            "Semi-finals for the top 4", semisOn, t.teams.size > 4,
                            if (t.teams.size > 4) "1st vs 4th, 2nd vs 3rd" else "Needs more than 4 teams"
                        ) { mutate { it.copy(rrSemis = !semisOn, rrFinal = it.rrFinal || !semisOn) } }
                        CheckRow(
                            "Final", finalOn, t.teams.size > 2 && !semisOn,
                            when {
                                t.teams.size <= 2 -> "Needs more than 2 teams"
                                semisOn -> "Always played after semi-finals"
                                else -> "Top 2 meet for the title"
                            }
                        ) { mutate { it.copy(rrFinal = !finalOn) } }
                        Spacer(Modifier.height(6.dp))
                        HText(Scheduler.expectedStages(t).joinToString(" → "), 12.sp, FontWeight.Normal, Mute)
                    }
                    if (on && algo == Algorithm.GROUP_KO) {
                        Spacer(Modifier.height(12.dp))
                        val g = t.groups.coerceIn(1, t.maxGroups())
                        val minSize = maxOf(1, t.teams.size / g)
                        val adv = Scheduler.clampedAdvance(t)
                        StepperRow(
                            "Groups", g.toString(), "",
                            { if (g > 1) mutate { it.copy(groups = g - 1) } },
                            { if (g < t.maxGroups()) mutate { it.copy(groups = g + 1) } },
                            card = false
                        )
                        StepperRow(
                            "Advance per group", adv.toString(), "",
                            { if (adv > 1) mutate { it.copy(advance = adv - 1) } },
                            { if (adv < minSize) mutate { it.copy(advance = adv + 1) } },
                            card = false
                        )
                        Spacer(Modifier.height(6.dp))
                        val sizes = List(g) { i -> t.teams.size / g + if (i < t.teams.size % g) 1 else 0 }
                        val groupsText = if (sizes.distinct().size == 1) "$g groups of ${sizes[0]}" else "$g groups (${sizes.min()}–${sizes.max()} teams)"
                        HText("$groupsText · top $adv advance", 13.sp, FontWeight.Bold)
                        HText(Scheduler.expectedStages(t).joinToString(" → "), 12.sp, FontWeight.Normal, Mute)
                    }
                    if (on && algo.usesPoints) PointsSettings(t, mutate)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            PrimaryButton("Continue: game format", {
                mutate { it.copy(draftStep = maxOf(it.draftStep, 5)) }
                onNext()
            }, icon = Icons.Filled.CalendarMonth)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, sub: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Accent))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(vertical = 6.dp)) {
            HText(label, 14.sp, FontWeight.Bold, if (enabled) Ink else Mute)
            HText(sub, 12.sp, FontWeight.Normal, Mute)
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
    GameFormatContent(t, wizard, { change -> HoopsApp.repo.mutate(id, change) }, onBack, onDone)
}

@Composable
private fun GameFormatContent(t: Tournament, wizard: Boolean, mutate: PlanMutate, onBack: () -> Unit, onDone: () -> Unit) {
    var f by remember(t.id) { mutableStateOf(t.format.copy(breakMin = t.format.breakMin.coerceAtLeast(1))) }
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
    val hasSemis = Scheduler.roundName(4) in Scheduler.expectedStages(t)

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
                // Only offered when this tournament actually draws a semi-final round.
                if (hasSemis) {
                    SwitchRow("Different length for Semi-finals", f.semisDifferent) { f = f.copy(semisDifferent = it) }
                    if (f.semisDifferent) {
                        StepperRow(
                            if (f.type == FormatType.HALVES) "Semi-finals half length" else "Semi-finals game length", f.semisPeriodMin.toString(), "min",
                            { if (f.semisPeriodMin > 1) f = f.copy(semisPeriodMin = f.semisPeriodMin - 1) },
                            { if (f.semisPeriodMin < 90) f = f.copy(semisPeriodMin = f.semisPeriodMin + 1) }, card = false
                        )
                    }
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
                mutate { tt ->
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

// ─── Previews ───────────────────────────────────────────────────────────
// These call the private *Content functions with a sample 6-team draft. Taps in a preview change nothing.

private val previewPlanTournament: Tournament by lazy {
    val start = 1_750_000_000_000L
    val positions = listOf("Guard", "Forward", "Center")
    val players = (1..18).map { Player("p$it", "Player $it", positions[it % 3], (it % 5) + 1) }
    val names = listOf("Red Hawks", "Blue Jays", "Green Mambas", "Gold Kings", "Night Owls", "Iron Wolves")
    val teams = names.mapIndexed { i, n -> Team("t$i", n, i, players.subList(i * 3, i * 3 + 3).map { it.id }) }
    Tournament(
        id = "preview", name = "City Cup", startAt = start, endAt = start + 6 * 3_600_000L, courts = 2,
        playerTarget = 18, teamTarget = 6, players = players, teams = teams,
        algorithm = Algorithm.GROUP_KO, groups = 2, advance = 2, status = TStatus.DRAFT, draftStep = 5, createdAt = start
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - group + knockout")
@Composable
private fun PreviewAlgorithm() {
    HoopsTheme { AlgorithmContent(previewPlanTournament, mutate = {}, onBack = {}, onNext = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - round robin, nothing ticked")
@Composable
private fun PreviewAlgorithmRoundRobin() {
    HoopsTheme {
        AlgorithmContent(previewPlanTournament.copy(algorithm = Algorithm.ROUND_ROBIN), mutate = {}, onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - round robin, semis + final")
@Composable
private fun PreviewAlgorithmRoundRobinSemis() {
    HoopsTheme {
        AlgorithmContent(previewPlanTournament.copy(algorithm = Algorithm.ROUND_ROBIN, rrSemis = true), mutate = {}, onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - round robin, final only")
@Composable
private fun PreviewAlgorithmRoundRobinFinal() {
    HoopsTheme {
        AlgorithmContent(previewPlanTournament.copy(algorithm = Algorithm.ROUND_ROBIN, rrFinal = true), mutate = {}, onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - round robin, 4 teams (semis off)")
@Composable
private fun PreviewAlgorithmRoundRobinFourTeams() {
    val t = previewPlanTournament
    HoopsTheme {
        AlgorithmContent(
            t.copy(algorithm = Algorithm.ROUND_ROBIN, teams = t.teams.take(4), rrSemis = true, rrFinal = true),
            mutate = {}, onBack = {}, onNext = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - round robin, 2 teams (both off)")
@Composable
private fun PreviewAlgorithmRoundRobinTwoTeams() {
    val t = previewPlanTournament
    HoopsTheme {
        AlgorithmContent(
            t.copy(algorithm = Algorithm.ROUND_ROBIN, teams = t.teams.take(2), rrSemis = true, rrFinal = true),
            mutate = {}, onBack = {}, onNext = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - custom points (3 / 1 / 0)")
@Composable
private fun PreviewAlgorithmCustomPoints() {
    HoopsTheme {
        AlgorithmContent(previewPlanTournament.copy(winPoints = 3, tiePoints = 1, lossPoints = 0), mutate = {}, onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - swiss (points)")
@Composable
private fun PreviewAlgorithmSwiss() {
    HoopsTheme { AlgorithmContent(previewPlanTournament.copy(algorithm = Algorithm.SWISS), mutate = {}, onBack = {}, onNext = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1100, name = "Algorithm - single elimination (no points)")
@Composable
private fun PreviewAlgorithmSingleElim() {
    HoopsTheme { AlgorithmContent(previewPlanTournament.copy(algorithm = Algorithm.SINGLE_ELIM), mutate = {}, onBack = {}, onNext = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Game format - basketball")
@Composable
private fun PreviewGameFormat() {
    HoopsTheme { GameFormatContent(previewPlanTournament, wizard = true, mutate = {}, onBack = {}, onDone = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Game format - football, halves")
@Composable
private fun PreviewGameFormatFootball() {
    val t = previewPlanTournament
    HoopsTheme {
        GameFormatContent(
            t.copy(sport = Sport.FOOTBALL, format = t.format.copy(type = FormatType.HALVES, finalsDifferent = true, semisDifferent = true)),
            wizard = false, mutate = {}, onBack = {}, onDone = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Game format - doesn't fit")
@Composable
private fun PreviewGameFormatOverrun() {
    val t = previewPlanTournament
    HoopsTheme { GameFormatContent(t.copy(endAt = t.startAt + 45 * 60_000L), wizard = true, mutate = {}, onBack = {}, onDone = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Game format - no semi-finals option")
@Composable
private fun PreviewGameFormatNoSemis() {
    HoopsTheme {
        GameFormatContent(previewPlanTournament.copy(algorithm = Algorithm.ROUND_ROBIN), wizard = true, mutate = {}, onBack = {}, onDone = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1000, name = "Game format - different semis + finals length")
@Composable
private fun PreviewGameFormatSemisFinalsLength() {
    val t = previewPlanTournament
    HoopsTheme {
        GameFormatContent(
            t.copy(format = t.format.copy(semisDifferent = true, semisPeriodMin = 14, finalsDifferent = true, finalsPeriodMin = 16)),
            wizard = true, mutate = {}, onBack = {}, onDone = {}
        )
    }
}
