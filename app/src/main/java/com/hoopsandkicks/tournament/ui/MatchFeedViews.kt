package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopsandkicks.tournament.data.FeedItem
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.buildMatchFeed

// The "Match feed" viewers see: who scored, subs, half-time, the shootout and full time (see data/MatchFeed.kt).
// Plain data in, no view model or repository, so it works for live viewers and in previews.

/** How many of the newest lines the live hub card shows before "Show all". */
internal const val LiveFeedLimit = 5

/** Text colours for the navy hub card ([dark]) or a white match card. */
private class FeedColors(dark: Boolean) {
    val main = if (dark) OnDark else Ink
    val mute = if (dark) MuteDark else Mute
    val accent = if (dark) AccentDark else Accent
    val line = if (dark) Navy3 else Line
}

/** Live hub card (navy): the newest [LiveFeedLimit] lines, newest first, with "Show all (n)" / "Show less". */
@Composable
internal fun LiveMatchFeed(t: Tournament, m: Match) {
    LiveMatchFeedContent(t, buildMatchFeed(t, m), m.id)
}

/** Nothing at all while the feed is empty, so a match that just kicked off keeps a clean card. */
@Composable
internal fun LiveMatchFeedContent(t: Tournament, feed: List<FeedItem>, matchId: String, showAllInitially: Boolean = false) {
    if (feed.isEmpty()) return
    var showAll by remember(matchId) { mutableStateOf(showAllInitially) }
    val newestFirst = feed.asReversed()
    val shown = if (showAll) newestFirst else newestFirst.take(LiveFeedLimit)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Navy2)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        HText("MATCH FEED", 11.sp, FontWeight.Bold, MuteDark, Modifier.padding(top = 2.dp, bottom = 2.dp))
        shown.forEach { FeedRow(t, it, dark = true) }
        if (feed.size > LiveFeedLimit) {
            HText(
                if (showAll) "Show less" else "Show all (${feed.size})",
                13.sp, FontWeight.Bold, AccentDark,
                Modifier.fillMaxWidth().clickable { showAll = !showAll }.padding(top = 6.dp, bottom = 4.dp)
            )
        }
    }
}

/** The whole feed, oldest first, for a match card a viewer tapped open in Fixtures. */
@Composable
internal fun MatchFeedTimeline(t: Tournament, feed: List<FeedItem>) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        HText("Match feed", 12.sp, FontWeight.Bold, Mute, Modifier.padding(top = 8.dp, bottom = 2.dp))
        feed.forEach { FeedRow(t, it, dark = false) }
    }
}

/** Fixtures card part for viewers: a hint while collapsed, the timeline once tapped open. */
@Composable
internal fun MatchCardFeed(t: Tournament, feed: List<FeedItem>, expanded: Boolean) {
    if (feed.isEmpty()) return
    if (expanded) MatchFeedTimeline(t, feed)
    else HText("Tap to see the match feed", 12.sp, FontWeight.Medium, Mute, Modifier.padding(top = 2.dp))
}

@Composable
private fun FeedRow(t: Tournament, item: FeedItem, dark: Boolean) {
    val c = FeedColors(dark)
    when (item) {
        is FeedItem.Score -> ScoreRow(t, item, c)
        is FeedItem.Substitution -> SubRow(item, c)
        is FeedItem.ShootoutAttempt -> AttemptRow(t, item, c)
        is FeedItem.HalfTime, is FeedItem.SecondHalf, is FeedItem.FullTime, is FeedItem.ShootoutStart -> MilestoneRow(item, c)
    }
}

private fun dotColor(t: Tournament, teamId: String?): Color = t.team(teamId)?.let { teamColor(it.color) } ?: Mute

@Composable
private fun ScoreRow(t: Tournament, item: FeedItem.Score, c: FeedColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(dotColor(t, item.teamId), 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            HText(item.title, 14.sp, FontWeight.Bold, c.main, maxLines = 1)
            HText(item.subtitle, 12.sp, FontWeight.Medium, c.mute, maxLines = 1)
        }
        val time = item.time
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            HText(time, 12.sp, FontWeight.Medium, c.mute)
        }
    }
}

@Composable
private fun SubRow(item: FeedItem.Substitution, c: FeedColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.SwapHoriz, "Substitution", tint = c.mute, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        HText("${item.title} · ${item.subtitle}", 12.sp, FontWeight.Normal, c.mute, Modifier.weight(1f), maxLines = 1)
    }
}

@Composable
private fun AttemptRow(t: Tournament, item: FeedItem.ShootoutAttempt, c: FeedColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(if (item.made) Green else ShotMissedTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.made) Icons.Filled.Check else Icons.Filled.Close, null,
                tint = Color.White, modifier = Modifier.size(12.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            HText(item.title, 14.sp, FontWeight.Medium, c.main, maxLines = 1)
            HText(item.subtitle, 12.sp, FontWeight.Normal, c.mute, maxLines = 1)
        }
        Dot(dotColor(t, item.teamId), 8.dp)
    }
}

private val ShotMissedTint = Color(0xFFB3261E)

/** Half-time, second half, full time and the shootout header: a labelled divider, with the result under full time. */
@Composable
private fun MilestoneRow(item: FeedItem, c: FeedColors) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(1.dp).background(c.line))
            Spacer(Modifier.width(8.dp))
            HText(item.title, 13.sp, FontWeight.Bold, c.accent)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
        }
        val sub = item.subtitle
        if (sub != null) HText(sub, 13.sp, FontWeight.Medium, c.main, Modifier.padding(start = 22.dp))
    }
}
