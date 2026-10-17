package com.streakguard.app.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streakguard.app.R
import com.streakguard.app.schedule.ReminderSchedule
import com.streakguard.app.ui.theme.Primary
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import com.streakguard.app.util.ClockFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Edit in the selected timezone; keep UTC drafts for the platform's daily reset. */
@Composable
internal fun NotificationTimesDialog(
    initialMinutes: List<Int>,
    zone: ZoneId,
    use24HourTime: Boolean,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    var times by rememberSaveable { mutableStateOf(initialMinutes.map { 1440 - it }) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var invalidLocalTime by rememberSaveable { mutableStateOf(false) }
    val referenceDay = rememberSaveable { LocalDate.now(ZoneOffset.UTC).toString() }
    val dayStart = LocalDate.parse(referenceDay).atStartOfDay(ZoneOffset.UTC).toInstant()
    val dayEnd = dayStart.plusSeconds(86400)
    val zoneLabel = DateTimeFormatter.ofPattern("z").withZone(zone).format(dayStart)
    val reset = dayEnd.atZone(zone)
    val resetLabel = "${ClockFormat.format(reset.toLocalTime(), use24HourTime)} ${DateTimeFormatter.ofPattern("z").format(reset)}"
    val valid = ReminderSchedule.offsetsForUtcTimes(times) != null
    val context = LocalContext.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 48).coerceAtLeast(160).dp
    Dialog(onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = !saving, dismissOnClickOutside = !saving)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 390.dp)
                .fillMaxWidth().heightIn(max = maxHeight),
            shape = RoundedCornerShape(16.dp), color = SettingsCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_settings_notifications_active), contentDescription = null,
                        tint = Primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Notification times", color = SettingsText, fontFamily = SettingsCardFont,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !saving,
                        modifier = Modifier.size(40.dp).semantics { contentDescription = "Close notification times" },
                        contentPadding = PaddingValues(0.dp)) {
                        Text("×", color = SettingsMuted, fontSize = 26.sp)
                    }
                }
                Text("Choose when each daily reminder is sent.", color = SettingsMuted,
                    fontFamily = SettingsCardFont, fontSize = 11.sp, lineHeight = 16.sp)
                HorizontalDivider(color = SettingsBorder)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PopupMono("REMINDER", SettingsMuted, 9, FontWeight.Bold)
                    PopupMono("$zoneLabel · ${if (use24HourTime) "24" else "12"} HR", SettingsMuted, 9)
                }
                val titles = listOf("Passive ping", "Warning nudge", "Urgent buzzer")
                val subtitles = listOf("FIRST REMINDER", "SECOND REMINDER", "FINAL REMINDER")
                val colors = listOf(SettingsMuted, SettingsAmber, SettingsUrgent)
                titles.forEachIndexed { tier, title ->
                    Row(Modifier.fillMaxWidth().background(Sunken, RoundedCornerShape(12.dp))
                        .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(6.dp).background(colors[tier], RoundedCornerShape(3.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, color = SettingsText, fontFamily = SettingsCardFont,
                                fontSize = 13.sp, lineHeight = 18.sp)
                            PopupMono(subtitles[tier], SettingsMuted, 8)
                        }
                        val zonedTime = dayStart.plusSeconds(times[tier] * 60L).atZone(zone)
                        val time = zonedTime.toLocalTime()
                        Row(Modifier.background(SettingsButton, RoundedCornerShape(7.dp))
                            .border(1.dp, Color(0xFF263049), RoundedCornerShape(7.dp))
                            .clickable(enabled = !saving, role = Role.Button) {
                                TimePickerDialog(context, android.R.style.Theme_Material_Dialog_Alert,
                                    { _, hour, minute ->
                                        // A UTC streak day can span two local calendar dates.
                                        // Resolve the chosen clock time inside that same streak day.
                                        val firstDate = dayStart.atZone(zone).toLocalDate()
                                        val lastDate = dayEnd.minusSeconds(1).atZone(zone).toLocalDate()
                                        val candidates = listOf(firstDate, lastDate).distinct().flatMap { date ->
                                            val local = date.atTime(LocalTime.of(hour, minute))
                                            zone.rules.getValidOffsets(local).map { local.toInstant(it) }
                                        }.filter { !it.isBefore(dayStart) && it.isBefore(dayEnd) }
                                        val chosen = candidates.minByOrNull {
                                            kotlin.math.abs(it.epochSecond - zonedTime.toEpochSecond())
                                        }
                                        invalidLocalTime = chosen == null
                                        if (chosen != null) {
                                            val utcMinute = ((chosen.epochSecond - dayStart.epochSecond) / 60).toInt()
                                            times = times.toMutableList().also { it[tier] = utcMinute }
                                        }
                                        showValidation = false
                                    }, time.hour, time.minute, use24HourTime).apply {
                                    setTitle("$title ($zoneLabel)")
                                }.show()
                            }
                            .semantics { contentDescription = "$title time, ${ClockFormat.format(time, use24HourTime)}, $zoneLabel" }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PopupMono(ClockFormat.format(time, use24HourTime), colors[tier],
                                if (use24HourTime) 14 else 12, FontWeight.Bold)
                            Icon(painterResource(R.drawable.ic_settings_schedule), contentDescription = null,
                                tint = SettingsMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Text("Times use ${zone.id}. Your daily streak resets at $resetLabel.",
                    color = SettingsMuted, fontFamily = SettingsCardFont, fontSize = 10.sp,
                    lineHeight = 15.sp)
                if (showValidation && !valid) {
                    Text("Choose times in first-to-final order within the same streak day, more than 30 minutes apart. The day resets at $resetLabel.",
                        color = Critical, fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (invalidLocalTime) {
                    Text("That time is unavailable in this timezone for this streak day. Choose another time.",
                        color = Critical, fontSize = 12.sp, lineHeight = 17.sp)
                }
                errorMessage?.let { Text(it, color = Critical, fontSize = 12.sp) }
                HorizontalDivider(color = SettingsBorder)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263049)),
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsButton, contentColor = SettingsText)) {
                        Text("Cancel", fontFamily = SettingsCardFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { if (valid && !invalidLocalTime) onSave(times) else showValidation = true },
                        enabled = !saving, modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = SettingsThumb)) {
                        Text(if (saving) "Saving…" else "Save", fontFamily = SettingsCardFont,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupMono(text: String, color: Color, size: Int, weight: FontWeight = FontWeight.Normal) {
    Text(text, color = color, fontFamily = JetBrainsMonoFontFamily, fontSize = size.sp,
        fontWeight = weight, lineHeight = (size * 1.5).sp)
}
