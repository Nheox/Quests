package cl.nheox.quests.i18n

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.DueBucket
import cl.nheox.quests.domain.EnergyBand
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.RepeatUnit
import cl.nheox.quests.domain.SortKey
import cl.nheox.quests.domain.SuggestionReason
import cl.nheox.quests.domain.Urgency

/**
 * Every piece of user-facing text in the app.
 *
 * An interface of named properties rather than a string table looked up by key,
 * because a missing key should be a compile error and not a blank label
 * discovered by a user. English and Spanish both ship from day one (§7.6), and
 * keeping them as two implementations of one interface means neither can
 * quietly fall behind the other.
 *
 * Every string here was written fresh for this app. Nothing is lifted from the
 * app that inspired it (§2).
 */
public interface Strings {
    public val appName: String
    public val tagline: String
    public val navToday: String
    public val navTomorrow: String
    public val navOverdue: String
    public val navNext: String
    public val navPlan: String
    public val navAll: String
    public val navCompleted: String
    public val navLastSevenDays: String
    public val navFailed: String
    public val navNoDueDate: String
    public val navSkills: String
    public val navRewards: String
    public val navInventory: String
    public val navScratchpad: String
    public val navStats: String
    public val navSettings: String
    public val navHelp: String
    public val sectionQuests: String
    public val sectionProgress: String
    public val sectionApp: String
    public val actionNewQuest: String
    public val actionEdit: String
    public val actionDuplicate: String
    public val actionDelete: String
    public val actionComplete: String
    public val actionFail: String
    public val actionReopen: String
    public val actionMarkWorked: String
    public val actionMoveToTop: String
    public val actionMoveToBottom: String
    public val actionPromote: String
    public val actionAddSubQuest: String
    public val actionSearch: String
    public val actionUndo: String
    public val actionSave: String
    public val actionCancel: String
    public val actionClose: String
    public val actionClear: String
    public val actionAdd: String
    public val actionRemove: String
    public val actionClaim: String
    public val actionUse: String
    public val actionRestock: String
    public val actionPickRandom: String
    public val actionShareAsText: String
    public val actionSelectAll: String
    public val actionClearSelection: String
    public val actionCommandPalette: String
    public val actionQuickCapture: String
    public val actionWhatNow: String
    public val actionExportNow: String
    public val actionChooseFolder: String
    public val actionReload: String
    public val actionCompact: String
    public val actionOpenFolder: String
    public val actionBack: String
    public val actionDone: String
    public val actionSkip: String
    public val actionNext: String
    public val actionPromoteNote: String
    public val fieldTitle: String
    public val fieldNotes: String
    public val fieldDifficulty: String
    public val fieldFear: String
    public val fieldUrgency: String
    public val fieldEstimate: String
    public val fieldDueDate: String
    public val fieldDueTime: String
    public val fieldRepetition: String
    public val fieldReminders: String
    public val fieldSkills: String
    public val fieldCategory: String
    public val fieldCost: String
    public val fieldCostIncrement: String
    public val fieldIncrementEvery: String
    public val fieldQuantity: String
    public val fieldConsumable: String
    public val fieldName: String
    public val fieldMinutes: String
    public val fieldNone: String
    public val fieldOptional: String
    public val rescheduleToday: String
    public val rescheduleTomorrow: String
    public val reschedulePlusWeek: String
    public val reschedulePlusMonth: String
    public val rescheduleNextRepetition: String
    public val rescheduleClear: String
    public val rescheduleTitle: String
    public val rescheduleOverdueTitle: String
    public val rescheduleOverdueBody: String
    public val sortBy: String
    public val sortAscending: String
    public val sortDescending: String
    public val statsTitle: String
    public val statsFearLog: String
    public val statsFearLogBody: String
    public val statsTrends: String
    public val statsXpPerWeek: String
    public val statsCompletionRate: String
    public val statsSkillGrowth: String
    public val statsRadar: String
    public val statsStreak: String
    public val statsLongestStreak: String
    public val statsOpen: String
    public val statsOverdue: String
    public val statsCompleted: String
    public val statsFailed: String
    public val statsThisWeek: String
    public val statsAllTime: String
    public val statsStillAvoiding: String
    public val statsNotEnoughData: String
    public val statsDays: String
    public val level: String
    public val xp: String
    public val xpToNext: String
    public val rewardPoints: String
    public val energy: String
    public val energyManual: String
    public val energySleepSchedule: String
    public val energyWakeTime: String
    public val energySleepTime: String
    public val energyHigh: String
    public val energyMedium: String
    public val energyLow: String
    public val energySpent: String
    public val energyNote: String
    public val suggesterTitle: String
    public val suggesterMinutes: String
    public val suggesterNoCandidates: String
    public val suggesterRefresh: String
    public val messageNoQuests: String
    public val messageNothingDueToday: String
    public val messageNoOverdue: String
    public val messageNotEnoughPoints: String
    public val messageQuestCompleted: String
    public val messageQuestFailed: String
    public val messageQuestDeleted: String
    public val messageRewardClaimed: String
    public val messageSaved: String
    public val messageExported: String
    public val messageCopied: String
    public val messageSearchPlaceholder: String
    public val messageCommandPlaceholder: String
    public val messageQuickAddPlaceholder: String
    public val messageNoResults: String
    public val messageSelected: String
    public val messageSubQuests: String
    public val messageLogLines: String
    public val messageSkippedLines: String
    public val messageCompacted: String
    public val messageNothingToCompact: String
    public val settingsFolder: String
    public val settingsFolderBody: String
    public val settingsDevice: String
    public val settingsDeviceBody: String
    public val settingsLanguage: String
    public val settingsSounds: String
    public val settingsFailureCosts: String
    public val settingsFailureCostsBody: String
    public val settingsDailyDigest: String
    public val settingsDigestTime: String
    public val settingsOverdueSummary: String
    public val settingsReminders: String
    public val settingsTray: String
    public val settingsIcsExport: String
    public val settingsIcsExportBody: String
    public val settingsMarkdownExport: String
    public val settingsHotkey: String
    public val settingsHotkeyInstall: String
    public val settingsHotkeyInstalled: String
    public val settingsHotkeyFailed: String
    public val settingsAbout: String
    public val settingsVersion: String
    public val settingsLicence: String
    public val settingsDanger: String
    public val onboardingWelcome: String
    public val onboardingBody: String
    public val onboardingFearTitle: String
    public val onboardingFearBody: String
    public val onboardingFilesTitle: String
    public val onboardingFilesBody: String
    public val onboardingKeyboardTitle: String
    public val onboardingKeyboardBody: String
    public val onboardingStart: String
    public val helpTitle: String
    public val helpShortcuts: String
    public val helpQuickAdd: String
    public val helpQuickAddBody: String
    public val helpHotkeyTitle: String
    public val helpSyncTitle: String
    public val helpCalendarTitle: String
    public val scratchpadPlaceholder: String
    public val scratchpadGlobal: String
    public val scratchpadForQuest: String
    public val skillsEmpty: String
    public val rewardsEmpty: String
    public val inventoryEmpty: String
    public val questsEmpty: String
    public val balanceNote: String
    public val messageCaptureUnavailable: String

    public fun xpAmount(amount: Int): String
    public fun pointsAmount(amount: Int): String
    public fun levelUpMessage(skill: String, level: Int): String
    public fun countSelected(count: Int): String
    public fun countSubQuests(count: Int): String
    public fun countOverdue(count: Int): String
    public fun countOpen(count: Int): String
    public fun dueOn(date: String): String
    public fun completedOn(date: String): String
    public fun repeatsEvery(every: Int, unit: String): String
    public fun remindBefore(minutes: Int): String
    public fun digestBody(open: Int, overdue: Int): String
    public fun skippedLinesWarning(count: Int): String
    public fun compactedSummary(events: Int, fields: Int): String
    public fun streakDays(days: Int): String
    public fun percent(value: Float): String

    public fun difficulty(value: Difficulty): String
    public fun fear(value: Fear): String
    public fun urgency(value: Urgency): String
    public fun repeatUnit(value: RepeatUnit): String
    public fun view(value: QuestView): String
    public fun bucket(value: DueBucket): String
    public fun sortKey(value: SortKey): String
    public fun energyBand(value: EnergyBand): String
    public fun suggestionReason(value: SuggestionReason): String
}

public object Translations {
    public fun of(language: cl.nheox.quests.domain.Language): Strings = when (language) {
        cl.nheox.quests.domain.Language.EN -> StringsEn
        cl.nheox.quests.domain.Language.ES -> StringsEs
    }
}
