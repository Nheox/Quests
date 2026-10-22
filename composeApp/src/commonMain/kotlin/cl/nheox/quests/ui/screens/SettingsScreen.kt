package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.nheox.quests.BuildInfo
import cl.nheox.quests.domain.Energy
import cl.nheox.quests.domain.EnergyMode
import cl.nheox.quests.domain.LaunchView
import cl.nheox.quests.domain.ThemeMode
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.ProfileFields
import cl.nheox.quests.events.SettingsFields
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.ChoiceButton
import cl.nheox.quests.ui.components.EnergyBar
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestSwitch
import cl.nheox.quests.ui.components.ReadingPageEnd
import cl.nheox.quests.ui.components.ReadingSection
import cl.nheox.quests.ui.components.ReadingWidth
import cl.nheox.quests.ui.components.SettingRow
import cl.nheox.quests.ui.components.TimeField
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * Settings, as the reading page (the owner's layout 6): one column of
 * sections, each row a name, a line saying what it does, and its control at
 * the right.
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
    fun setting(patch: FieldPatch) = app.store.changeSettings(patch)
    fun profile(patch: FieldPatch) = app.store.changeProfile(patch)

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.navSettings, style = MaterialTheme.typography.headlineSmall, color = theme.text)
        }
        HLine()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Column(Modifier.widthIn(max = ReadingWidth)) {
                ReadingSection(text.settingsGeneral)
                // Where the app opens replaced the digest and the launch summary
                // (§7.5): the Standing screen answers at launch what those two
                // notifications used to announce.
                SettingRow(text.settingsLaunchView, text.settingsLaunchViewBody) {
                    ChoiceButton(
                        value = settings.launchView,
                        options = LaunchView.entries,
                        label = { text.launchView(it) },
                        onSelect = { setting(FieldPatch().setEnum(SettingsFields.LAUNCH_VIEW, it)) },
                        highlight = true,
                    )
                }
                SettingRow(text.settingsTheme, text.settingsThemeBody) {
                    ChoiceButton(
                        value = settings.theme,
                        options = ThemeMode.entries,
                        label = { text.themeMode(it) },
                        onSelect = { setting(FieldPatch().setEnum(SettingsFields.THEME, it)) },
                    )
                }
                SettingRow(text.settingsTray, text.settingsTrayBody) {
                    QuestSwitch(settings.trayEnabled) { setting(FieldPatch().set(SettingsFields.TRAY_ENABLED, it)) }
                }

                ReadingSection(text.energy)
                SettingRow(
                    text.energyHowSet,
                    when (profile.energyMode) {
                        EnergyMode.MANUAL -> text.energyManualBody
                        EnergyMode.SLEEP_SCHEDULE -> text.energyAutoBody
                    },
                ) {
                    ChoiceButton(
                        // Auto first: it is the default, and the mode that needs
                        // no attention once the two times are set.
                        value = profile.energyMode,
                        options = listOf(EnergyMode.SLEEP_SCHEDULE, EnergyMode.MANUAL),
                        label = { mode ->
                            when (mode) {
                                EnergyMode.MANUAL -> text.energyManual
                                EnergyMode.SLEEP_SCHEDULE -> text.energyAuto
                            }
                        },
                        onSelect = { profile(FieldPatch().setEnum(ProfileFields.ENERGY_MODE, it)) },
                    )
                }
                when (profile.energyMode) {
                    EnergyMode.SLEEP_SCHEDULE -> SettingRow(text.energyTimes, text.energyTimesBody) {
                        TimeField(
                            value = profile.wakeTime,
                            onValueChange = { time -> time?.let { profile(FieldPatch().setTime(ProfileFields.WAKE_TIME, it)) } },
                            onFocusChange = { app.typing = it },
                        )
                        TimeField(
                            value = profile.sleepTime,
                            onValueChange = { time -> time?.let { profile(FieldPatch().setTime(ProfileFields.SLEEP_TIME, it)) } },
                            onFocusChange = { app.typing = it },
                        )
                    }

                    EnergyMode.MANUAL -> SettingRow(text.energyLevel, text.energyLevelBody) {
                        Box(Modifier.width(200.dp)) {
                            Slider(
                                value = (profile.manualEnergy ?: 70).toFloat(),
                                onValueChange = { profile(FieldPatch().setIntOrNull(ProfileFields.MANUAL_ENERGY, it.toInt())) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.accent,
                                    activeTrackColor = theme.accent,
                                    inactiveTrackColor = theme.border,
                                ),
                            )
                        }
                    }
                }
                val energy = app.energy(state)
                SettingRow(text.energyNow, text.energyNote) {
                    Box(Modifier.width(120.dp)) { EnergyBar(energy) }
                    Text(
                        "$energy · ${text.energyBand(Energy.band(energy))}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = theme.textMuted,
                    )
                }

                ReadingSection(text.settingsReminders)
                SettingRow(text.settingsReminders, text.settingsRemindersBody) {
                    QuestSwitch(settings.remindersEnabled) { setting(FieldPatch().set(SettingsFields.REMINDERS_ENABLED, it)) }
                }

                ReadingSection(text.settingsYourData)
                SettingRow(text.settingsDataFolder, text.settingsDataFolderBody) {
                    PathBox(app.store.vaultRoot.toString())
                    QuestButton(text.actionOpenFolder, compact = true, onClick = onOpenFolder)
                    QuestButton(text.actionChooseFolder, compact = true, onClick = onChooseFolder)
                }
                app.pendingFolder?.let { pending ->
                    Text(
                        text.folderSwitchPending(pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.text,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }
                SettingRow(text.settingsDevice, text.settingsDeviceShort) {
                    PathBox(app.store.deviceId)
                }
                SettingRow(text.settingsCalendarFile, text.settingsCalendarBody) {
                    QuestSwitch(settings.icsExportEnabled) { setting(FieldPatch().set(SettingsFields.ICS_EXPORT_ENABLED, it)) }
                }
                SettingRow(text.settingsMarkdownFile, text.settingsMarkdownBody) {
                    QuestSwitch(settings.markdownExportEnabled) {
                        setting(FieldPatch().set(SettingsFields.MARKDOWN_EXPORT_ENABLED, it))
                    }
                }
                SettingRow(text.actionExportNow, text.settingsExportBody) {
                    QuestButton(text.actionExportNow, compact = true, onClick = onExportNow)
                }

                ReadingSection(text.settingsHotkey)
                SettingRow(text.settingsHotkeyInstall, text.settingsHotkeyBody) {
                    QuestButton(text.settingsHotkeyInstall, compact = true, onClick = {
                        app.notify(if (onInstallHotkey()) text.settingsHotkeyInstalled else text.settingsHotkeyFailed)
                    })
                }

                ReadingSection(text.settingsAdvanced)
                SettingRow(text.settingsFailureCosts, text.settingsFailureCostsBody) {
                    QuestSwitch(settings.failureCostsPoints) {
                        setting(FieldPatch().set(SettingsFields.FAILURE_COSTS_POINTS, it))
                    }
                }
                SettingRow(text.actionReload, text.settingsReloadBody) {
                    QuestButton(text.actionReload, compact = true, onClick = { app.store.reload() })
                }
                SettingRow(text.actionCompact, text.settingsCompactBody) {
                    QuestButton(text.actionCompact, compact = true, onClick = {
                        app.notify(onCompact() ?: text.messageNothingToCompact)
                    })
                }

                ReadingSection(text.settingsAbout)
                SettingRow("${text.appName} ${BuildInfo.VERSION}", text.settingsLicence) {}
                app.store.loadReport.value?.let { report ->
                    Text(
                        "${report.events.size} ${text.messageLogLines}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                    )
                    if (report.skipped.isNotEmpty()) {
                        Text(
                            text.skippedLinesWarning(report.skipped.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.text,
                        )
                    }
                }
                ReadingPageEnd()
            }
        }
    }
}

/**
 * A path or an identifier, in a quiet box. Monospace is the point here
 * (DESIGN §1): it is read character by character.
 */
@Composable
private fun PathBox(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        color = theme.textMuted,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .background(theme.card, RoundedCornerShape(Radii.md))
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}
