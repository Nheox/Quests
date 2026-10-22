package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.nheox.quests.BuildInfo
import cl.nheox.quests.domain.EnergyMode
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.ProfileFields
import cl.nheox.quests.events.SettingsFields
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.EnergyBar
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.SectionHeader
import cl.nheox.quests.ui.components.SegmentedSelector
import cl.nheox.quests.ui.components.TimeField
import cl.nheox.quests.ui.components.ToggleRow
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * Settings.
 *
 * Every switch here writes a single-field event like any other change, so
 * preferences sync between devices along with the quests. There is no separate
 * preferences file, because a second source of truth is a second thing that can
 * disagree with the first.
 */
@Composable
public fun SettingsScreen(
    app: AppState,
    state: QuestsState,
    onExportNow: () -> Unit,
    onInstallHotkey: () -> Boolean,
    onOpenFolder: () -> Unit,
    onChooseFolder: () -> Unit,
    onCompact: () -> String?,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val settings = state.settings
    val profile = state.profile

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.navSettings, style = MaterialTheme.typography.headlineSmall, color = theme.text)
        }
        HLine()

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md),
        ) {
            SectionHeader(text.settingsFolder)
            Text(text.settingsFolderBody, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            Spacer(Modifier.height(Space.sm))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(theme.card, RoundedCornerShape(Radii.md))
                    .padding(Space.sm),
            ) {
                Text(
                    app.store.vaultRoot.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted,
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                QuestButton(text.actionOpenFolder, compact = true, glyph = Glyph.FOLDER, onClick = onOpenFolder)
                QuestButton(text.actionChooseFolder, compact = true, onClick = onChooseFolder)
                QuestButton(text.actionReload, compact = true, glyph = Glyph.REPEAT, onClick = { app.store.reload() })
            }

            SectionHeader(text.settingsDevice)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Pill(app.store.deviceId, color = theme.accent, leading = Glyph.BOX)
                Text(
                    text.settingsDeviceBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textFaint,
                )
            }

            SectionHeader(text.energy)
            SegmentedSelector(
                // Auto first: it is the default, and the mode that needs no
                // attention once the two times are set.
                options = listOf(EnergyMode.SLEEP_SCHEDULE, EnergyMode.MANUAL),
                selected = profile.energyMode,
                onSelect = { app.store.changeProfile(FieldPatch().setEnum(ProfileFields.ENERGY_MODE, it)) },
                label = { mode ->
                    when (mode) {
                        EnergyMode.MANUAL -> text.energyManual
                        EnergyMode.SLEEP_SCHEDULE -> text.energyAuto
                    }
                },
                compact = true,
            )
            Text(
                when (profile.energyMode) {
                    EnergyMode.MANUAL -> text.energyManualBody
                    EnergyMode.SLEEP_SCHEDULE -> text.energyAutoBody
                },
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
                modifier = Modifier.padding(top = Space.xs),
            )
            Spacer(Modifier.height(Space.sm))
            when (profile.energyMode) {
                EnergyMode.MANUAL -> {
                    val value = (profile.manualEnergy ?: 70).toFloat()
                    Slider(
                        value = value,
                        onValueChange = {
                            app.store.changeProfile(
                                FieldPatch().setIntOrNull(ProfileFields.MANUAL_ENERGY, it.toInt()),
                            )
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.accent,
                            activeTrackColor = theme.accent,
                            inactiveTrackColor = theme.border,
                        ),
                    )
                }

                EnergyMode.SLEEP_SCHEDULE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Column {
                            Text(
                                text.energyWakeTime,
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.textFaint,
                            )
                            TimeField(
                                value = profile.wakeTime,
                                onValueChange = { time ->
                                    if (time != null) {
                                        app.store.changeProfile(FieldPatch().setTime(ProfileFields.WAKE_TIME, time))
                                    }
                                },
                                onFocusChange = { app.typing = it },
                            )
                        }
                        Column {
                            Text(
                                text.energyBedtime,
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.textFaint,
                            )
                            TimeField(
                                value = profile.sleepTime,
                                onValueChange = { time ->
                                    if (time != null) {
                                        app.store.changeProfile(FieldPatch().setTime(ProfileFields.SLEEP_TIME, time))
                                    }
                                },
                                onFocusChange = { app.typing = it },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.width(160.dp)) { EnergyBar(app.energy(state)) }
                Text(
                    "${app.energy(state)} · ${text.energyBand(cl.nheox.quests.domain.Energy.band(app.energy(state)))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted,
                )
            }
            Text(
                text.energyNote,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
                modifier = Modifier.padding(top = Space.xs),
            )

            SectionHeader(text.settingsReminders)
            ToggleRow(text.settingsReminders, settings.remindersEnabled, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.REMINDERS_ENABLED, it))
            })
            ToggleRow(text.settingsDailyDigest, settings.dailyDigestEnabled, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.DAILY_DIGEST_ENABLED, it))
            })
            if (settings.dailyDigestEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(text.settingsDigestTime, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
                    TimeField(
                        value = settings.dailyDigestTime,
                        onValueChange = { time ->
                            if (time != null) {
                                app.store.changeSettings(FieldPatch().setTime(SettingsFields.DAILY_DIGEST_TIME, time))
                            }
                        },
                        onFocusChange = { app.typing = it },
                    )
                }
            }
            ToggleRow(text.settingsOverdueSummary, settings.overdueSummaryOnLaunch, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.OVERDUE_SUMMARY_ON_LAUNCH, it))
            })
            ToggleRow(text.settingsTray, settings.trayEnabled, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.TRAY_ENABLED, it))
            })
            ToggleRow(text.settingsSounds, settings.soundsEnabled, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.SOUNDS_ENABLED, it))
            })

            SectionHeader(text.settingsIcsExport)
            Text(text.settingsIcsExportBody, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            ToggleRow(text.settingsIcsExport, settings.icsExportEnabled, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.ICS_EXPORT_ENABLED, it))
            })
            ToggleRow(text.settingsMarkdownExport, settings.markdownExportEnabled, {
                app.store.changeSettings(FieldPatch().set(SettingsFields.MARKDOWN_EXPORT_ENABLED, it))
            })
            QuestButton(text.actionExportNow, compact = true, glyph = Glyph.CALENDAR, onClick = onExportNow)

            SectionHeader(text.settingsHotkey)
            Text(
                text.helpHotkeyTitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
            )
            Spacer(Modifier.height(Space.sm))
            QuestButton(text.settingsHotkeyInstall, compact = true, glyph = Glyph.COMMAND, onClick = {
                app.notify(if (onInstallHotkey()) text.settingsHotkeyInstalled else text.settingsHotkeyFailed)
            })

            SectionHeader(text.settingsDanger)
            ToggleRow(
                label = text.settingsFailureCosts,
                checked = settings.failureCostsPoints,
                onCheckedChange = {
                    app.store.changeSettings(FieldPatch().set(SettingsFields.FAILURE_COSTS_POINTS, it))
                },
                description = text.settingsFailureCostsBody,
            )
            Spacer(Modifier.height(Space.sm))
            QuestButton(text.actionCompact, compact = true, glyph = Glyph.BOX, onClick = {
                app.notify(onCompact() ?: text.messageNothingToCompact)
            })

            SectionHeader(text.settingsAbout)
            Text(
                "${text.appName} ${BuildInfo.VERSION} · ${text.settingsVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted,
            )
            Text(text.settingsLicence, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            app.store.loadReport.value?.let { report ->
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${report.events.size} ${text.messageLogLines}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textFaint,
                )
                if (report.skipped.isNotEmpty()) {
                    Text(
                        text.skippedLinesWarning(report.skipped.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.warning,
                    )
                }
            }

            Spacer(Modifier.height(Space.xxl))
        }
    }
}
