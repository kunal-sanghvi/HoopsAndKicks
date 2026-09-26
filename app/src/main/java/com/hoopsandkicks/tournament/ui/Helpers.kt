package com.hoopsandkicks.tournament.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hoopsandkicks.tournament.HoopsApp
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.remote.JoinState
import com.hoopsandkicks.tournament.data.remote.ViewerStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local tournament by id, or — when watching a live room (read-only) — the remote one. Remote tournaments
 * are never stored in the Repository, so they can't be edited or leak into the home list.
 */
@Composable
fun observeTournament(id: String): Tournament? {
    val list by HoopsApp.repo.tournaments.collectAsState()
    val viewer by ViewerStore.state.collectAsState()
    return list.firstOrNull { it.id == id }
        ?: (viewer as? JoinState.Live)?.tournament?.takeIf { it.id == id }
}

fun todayText(): String = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date())

/** e.g. "Sun, 4 Oct 2026 · 9:00 AM"; "—" when unset. */
fun formatDateTime(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(epochMs))

/** e.g. "10:40 AM"; "—" when unset. */
fun formatTime(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))

/**
 * [formatTime], plus a short date when [epochMs] falls on a different local day than [referenceMs]
 * (e.g. "Mon, 5 Oct · 12:30 AM"), so a time that crosses midnight isn't mistaken for the same day.
 */
fun formatTimeFrom(epochMs: Long, referenceMs: Long): String {
    if (epochMs <= 0L || referenceMs <= 0L || utcMidnightOfLocalDay(epochMs) == utcMidnightOfLocalDay(referenceMs)) return formatTime(epochMs)
    return SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(epochMs))
}

// ---- Date/time picking (java.util.Calendar: no core library desugaring is configured) ----

/** The next whole hour from now, local time — the default start suggestion. */
fun nextWholeHourMs(): Long = Calendar.getInstance().apply {
    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.HOUR_OF_DAY, 1)
}.timeInMillis

fun localHour(epochMs: Long): Int = Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)
fun localMinute(epochMs: Long): Int = Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.MINUTE)

/** Material3's DatePicker works in UTC-midnight millis; this maps a local instant to its day in that form. */
fun utcMidnightOfLocalDay(epochMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = epochMs }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

/** Combines a DatePicker selection (UTC-midnight millis) with a local hour/minute into epoch ms. */
fun combineDateAndTime(dateUtcMs: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMs }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    }.timeInMillis
}

/** "Court 2 · 10:40 AM" from the scheduler's slot, or "" when the match hasn't been slotted. */
fun Match.slotText(): String {
    val parts = ArrayList<String>()
    court?.let { parts.add("Court $it") }
    scheduledAt?.let { parts.add(formatTime(it)) }
    return parts.joinToString(" · ")
}

fun Tournament.playedCount(): Int = realMatches().count { it.status == MatchStatus.FINISHED }

/** Ordered distinct stages that exist, as (stageIndex, name). */
fun Tournament.stages(): List<Pair<Int, String>> =
    matches.map { it.stageIndex to it.stage }.distinct().sortedBy { it.first }

fun Tournament.currentStageName(): String {
    val open = realMatches().filter { it.status != MatchStatus.FINISHED }.minByOrNull { it.frontier() }
    if (open != null) return open.stage
    return matches.maxByOrNull { it.frontier() }?.stage ?: ""
}

fun Match.title(t: Tournament): String {
    val head = when {
        group.isNotEmpty() -> group
        else -> stage
    }
    return if (number > 0) "$head · Match $number" else head
}

// There is no overtime: a tie at full time goes straight to the shootout, so only regulation periods exist.
// (A match saved by an older build could still have period > 2; it simply shows as the 2nd half.)

// A plain number ("1", "2") rather than "1H"/"2H" — the "H" suffix reads as "hour" at a glance in a
// short feed row, which is especially confusing next to a clock value.
fun Match.periodTag(halves: Boolean): String = when {
    halves -> if (period == 1) "1" else "2"
    else -> "G"
}

fun Match.periodName(halves: Boolean): String = when {
    halves -> if (period == 1) "1st half" else "2nd half"
    else -> "Game"
}
