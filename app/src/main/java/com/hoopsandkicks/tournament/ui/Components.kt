package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Screen(bg: Color = Bg, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        content = content
    )
}

@Composable
fun HText(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Ink,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = (size.value * 1.3f).sp
    )
}

@Composable
fun DisplayText(
    text: String,
    size: TextUnit = 28.sp,
    color: Color = Ink,
    modifier: Modifier = Modifier,
    align: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            lineHeight = (size.value * 1.05f).sp,
            color = color,
            fontFeatureSettings = "tnum"
        )
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Accent
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else Line)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Mute, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        HText(text, 16.sp, FontWeight.Bold, if (enabled) Color.White else Mute)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dark: Boolean = false,
    enabled: Boolean = true
) {
    val fg = if (dark) OnDark else Ink
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) Navy2 else Surface)
            .border(BorderStroke(1.dp, if (dark) Navy3 else Line), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        HText(text, 15.sp, FontWeight.Bold, if (enabled) fg else Mute, maxLines = 1)
    }
}

@Composable
fun IconCircle(icon: ImageVector, description: String, onClick: () -> Unit, dark: Boolean = false, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) Navy2 else Surface)
            .border(BorderStroke(1.dp, if (dark) Navy3 else Line), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (dark) OnDark else Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    val bg = if (dark) (if (selected) OnDark else Navy2) else (if (selected) Ink else Surface)
    val fg = if (dark) (if (selected) Navy else OnDark) else (if (selected) Color.White else Ink)
    val bd = if (dark) (if (selected) OnDark else Navy3) else (if (selected) Ink else Line)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(BorderStroke(1.dp, bd), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        HText(text, 14.sp, FontWeight.Bold, fg, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, bg: Color = AccentSoft, fg: Color = Accent) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        HText(text, 12.sp, FontWeight.Bold, fg, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 14.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun TeamPill(name: String, color: Color, size: TextUnit = 15.sp, textColor: Color = Ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 12.dp)
        Spacer(Modifier.width(8.dp))
        HText(name, size, FontWeight.Bold, textColor, maxLines = 1)
    }
}

@Composable
fun HCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(18.dp))
            .padding(padding),
        content = content
    )
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    dark: Boolean = false,
    right: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, dark)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            DisplayText(title, 30.sp, if (dark) OnDark else Ink)
        }
        right()
    }
}

@Composable
fun StepBar(step: Int, label: String, total: Int = 6) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until total) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < step) Accent else Line)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HText("Step $step of $total · $label", 13.sp, FontWeight.Medium, Mute)
    }
}

@Composable
fun BottomBar(dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) Navy else Bg)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    Column(modifier) {
        HText(label, 13.sp, FontWeight.Bold, Mute)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            shape = RoundedCornerShape(14.dp),
            textStyle = TextStyle(fontSize = 16.sp, color = Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onDone?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Line,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                cursorColor = Accent
            )
        )
    }
}

@Composable
fun StepperRow(
    label: String,
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    hint: String = "",
    card: Boolean = true
) {
    val inner: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                HText(label, 15.sp, FontWeight.Bold)
                if (hint.isNotEmpty()) HText(hint, 12.sp, FontWeight.Normal, Mute)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Bg).border(BorderStroke(1.dp, Line), CircleShape).clickable(onClick = onMinus),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Remove, "Decrease $label", tint = Ink, modifier = Modifier.size(20.dp)) }
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DisplayText(value, 30.sp, Ink, align = TextAlign.Center)
                if (unit.isNotEmpty()) HText(unit, 11.sp, FontWeight.Medium, Mute)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Ink).clickable(onClick = onPlus),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, "Increase $label", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
    if (card) {
        HCard(padding = 12.dp) { inner() }
    } else {
        inner()
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        HText(label, 15.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun Divider1(color: Color = Line) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun NavBar(active: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.AutoMirrored.Filled.FormatListBulleted to "Matches",
        Icons.Filled.BarChart to "Standings",
        Icons.Filled.Groups to "Teams",
        Icons.Filled.Star to "Stats"
    )
    Column(Modifier.fillMaxWidth().background(Surface)) {
        Divider1()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == active
                Column(
                    modifier = Modifier.weight(1f).height(60.dp).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.width(56.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) AccentSoft else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, label, tint = if (on) Accent else Mute, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.height(4.dp))
                    HText(label, 12.sp, FontWeight.Bold, if (on) Accent else Mute)
                }
            }
        }
    }
}

/**
 * A small highlighted chip for a period tag ("1", "2", "G"), used wherever a period is shown right next
 * to a clock value (e.g. a scoring feed row). Deliberately not text like "1H"/"2H" next to a time — that
 * reads as "1 hour" at a glance; a plain numbered badge doesn't.
 */
@Composable
fun PeriodBadge(period: String, dark: Boolean = true) {
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (dark) Navy3 else AccentSoft),
        contentAlignment = Alignment.Center
    ) {
        HText(period, 11.sp, FontWeight.Bold, if (dark) OnDark else Accent)
    }
}

fun formatClock(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

fun formatSeconds(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

// ─── Previews ───────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewTextComponents() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HText("Regular Text", size = 14.sp)
        HText("Bold Text", size = 16.sp, weight = FontWeight.Bold)
        DisplayText("42", size = 32.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewButtons() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HoopsTheme {
            PrimaryButton("Start Match", onClick = {})
            PrimaryButton("Disabled Button", onClick = {}, enabled = false)
            SecondaryButton("Cancel", onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewPeriodBadge() {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodBadge("1")
        PeriodBadge("2")
        PeriodBadge("OT", dark = false)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewChipsBadgesDots() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", selected = true, onClick = {})
                Chip("Court 1", selected = false, onClick = {})
                Chip("Court 2", selected = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("LIVE")
                Badge("FINAL", bg = GreenSoft, fg = Green)
                Dot(teamColor(0))
                Dot(teamColor(1))
            }
            TeamPill("Red Hawks", teamColor(0))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewIconCircles() {
    HoopsTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconCircle(Icons.Filled.Add, "Add", onClick = {})
            IconCircle(Icons.Filled.Star, "Star", onClick = {})
            Box(Modifier.background(Navy).padding(4.dp)) {
                IconCircle(Icons.Filled.Remove, "Remove", onClick = {}, dark = true)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewCardAndTopBar() {
    HoopsTheme {
        Column {
            TopBar("Standings", onBack = {})
            StepBar(step = 2, label = "Teams")
            HCard(Modifier.padding(horizontal = 20.dp)) {
                HText("Card title", 16.sp, FontWeight.Bold)
                Divider1()
                HText("Content inside a card", 14.sp, color = Mute)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewFormControls() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField("Team name", "Red Hawks", onChange = {})
            StepperRow("Game length", "10", "min", onMinus = {}, onPlus = {}, hint = "Per half")
            HCard { SwitchRow("Use shot clock", checked = true, onChange = {}) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE)
@Composable
private fun PreviewNavBarAndBottomBar() {
    HoopsTheme {
        Column {
            BottomBar {
                PrimaryButton("Save & next", onClick = {})
                SecondaryButton("Edit scores", onClick = {}, Modifier.fillMaxWidth())
            }
            NavBar(active = 0, onSelect = {})
        }
    }
}
