package cl.nheox.quests.i18n

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.DueBucket
import cl.nheox.quests.domain.EnergyBand
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.LaunchView
import cl.nheox.quests.domain.PriorityBreakdown
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.RepeatUnit
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.domain.SortKey
import cl.nheox.quests.domain.SuggestionReason
import cl.nheox.quests.domain.Urgency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.Locale

/** British English, the language the app was written in. */
public object StringsEn : Strings {
    override val appName: String = "Quests"
    override val tagline: String = "Real life, with a quest log."
    override val navToday: String = "Today"
    override val navTomorrow: String = "Tomorrow"
    override val navOverdue: String = "Overdue"
    override val navNext: String = "Next"
    override val navPlan: String = "Plan"
    override val navAll: String = "All"
    override val navCompleted: String = "Completed"
    override val navLastSevenDays: String = "Last 7 days"
    override val navFailed: String = "Failed"
    override val navNoDueDate: String = "No due date"
    override val navSkills: String = "Skills"
    override val navRewards: String = "Rewards"
    override val navInventory: String = "Inventory"
    override val navScratchpad: String = "Scratchpad"
    override val navStats: String = "Insight"
    override val navSettings: String = "Settings"
    override val navHelp: String = "Help"
    override val navStanding: String = "Standing"
    override val sectionQuests: String = "Quests"
    override val sectionProgress: String = "Progress"
    override val sectionApp: String = "App"
    override val actionNewQuest: String = "New quest"
    override val actionEdit: String = "Edit"
    override val actionDuplicate: String = "Duplicate"
    override val actionDelete: String = "Delete"
    override val actionComplete: String = "Complete"
    override val actionFail: String = "Fail"
    override val actionReopen: String = "Reopen"
    override val actionMarkWorked: String = "Mark as worked on"
    override val actionPinToTop: String = "Pin to top"
    override val actionPinToBottom: String = "Pin to bottom"
    override val actionClearPin: String = "Clear pin"
    override val actionPromote: String = "Promote to top level"
    override val actionAddSubQuest: String = "Add sub-quest"
    override val actionSearch: String = "Search"
    override val actionUndo: String = "Undo"
    override val actionSave: String = "Save"
    override val actionCancel: String = "Cancel"
    override val actionClose: String = "Close"
    override val actionClear: String = "Clear"
    override val actionAdd: String = "Add"
    override val actionRemove: String = "Remove"
    override val actionClaim: String = "Claim"
    override val actionUse: String = "Use"
    override val actionRestock: String = "Add one"
    override val actionPickRandom: String = "Pick a random quest"
    override val actionShareAsText: String = "Copy as text"
    override val actionSelectAll: String = "Select all"
    override val actionClearSelection: String = "Clear selection"
    override val actionCommandPalette: String = "Command palette"
    override val actionQuickCapture: String = "Quick capture"
    override val actionWhatNow: String = "What now?"
    override val actionExportNow: String = "Export now"
    override val actionChooseFolder: String = "Choose folder…"
    override val actionReload: String = "Reload from disk"
    override val actionCompact: String = "Compact logs"
    override val actionOpenFolder: String = "Open folder"
    override val actionBack: String = "Back"
    override val actionDone: String = "Done"
    override val actionPromoteNote: String = "Promote to quest"
    override val fieldTitle: String = "Title"
    override val fieldNotes: String = "Notes"
    override val fieldDifficulty: String = "Difficulty"
    override val fieldFear: String = "Fear"
    override val fieldUrgency: String = "Urgency"
    override val fieldEstimate: String = "Estimate"
    override val fieldDueDate: String = "Due date"
    override val fieldDueTime: String = "Due time"
    override val fieldRepetition: String = "Repeats"
    override val fieldReminders: String = "Reminders"
    override val fieldSkills: String = "Skills"
    override val fieldCategory: String = "Category"
    override val fieldCost: String = "Cost"
    override val fieldCostIncrement: String = "Cost increase"
    override val fieldIncrementEvery: String = "After every N claims"
    override val fieldQuantity: String = "Quantity"
    override val fieldConsumable: String = "Consumable"
    override val fieldName: String = "Name"
    override val fieldMinutes: String = "minutes"
    override val fieldRewardBonus: String = "Bonus points"
    override val fieldRewardBonusBody: String = "Added to what this quest pays when it is completed. XP is never affected."
    override val fieldPoints: String = "points"
    override val fieldNone: String = "None"
    override val fieldOptional: String = "optional"
    override val rescheduleToday: String = "Today"
    override val rescheduleTomorrow: String = "Tomorrow"
    override val reschedulePlusWeek: String = "In a week"
    override val reschedulePlusMonth: String = "In a month"
    override val rescheduleNextRepetition: String = "Next repetition"
    override val rescheduleClear: String = "Clear due date"
    override val rescheduleTitle: String = "Reschedule"
    override val rescheduleOverdueTitle: String = "Review overdue quests"
    override val rescheduleOverdueBody: String = "These are past their due date. Move them, or let them go."
    override val sortBy: String = "Sort by"
    override val sortAscending: String = "Ascending"
    override val sortDescending: String = "Descending"
    override val statsTitle: String = "Insight"
    override val statsFearLog: String = "Fear log"
    override val statsFearLogBody: String = "How often quests actually get finished, by how frightening they were."
    override val statsTrends: String = "Trends"
    override val statsXpPerWeek: String = "XP per week"
    override val statsCompletionRate: String = "Completion rate"
    override val statsSkillGrowth: String = "Skill growth"
    override val statsRadar: String = "Skill balance"
    override val statsStreak: String = "Streak"
    override val statsLongestStreak: String = "Longest"
    override val statsOpen: String = "Open"
    override val statsOverdue: String = "Overdue"
    override val statsCompleted: String = "Completed"
    override val statsFailed: String = "Failed"
    override val statsThisWeek: String = "This week"
    override val statsAllTime: String = "All time"
    override val statsStillAvoiding: String = "still open"
    override val statsNotEnoughData: String = "Not enough history yet. Finish a few quests and come back."
    override val statsDays: String = "days"
    override val level: String = "Level"
    override val xp: String = "XP"
    override val xpToNext: String = "to next level"
    override val rewardPoints: String = "Reward points"
    override val energy: String = "Energy"
    override val energyManual: String = "Manual"
    override val energyManualBody: String = "Set it yourself, for days that do not follow your usual rhythm."
    override val energyAuto: String = "Auto"
    override val energyAutoBody: String = "Drains across your day, from wake-up to bedtime."
    override val energyWakeTime: String = "Wake up"
    override val energyBedtime: String = "Bedtime"
    override val energyHigh: String = "High"
    override val energyMedium: String = "Medium"
    override val energyLow: String = "Low"
    override val energySpent: String = "Spent"
    override val energyNote: String = "Energy never changes XP. It only feeds the suggester and this bar."
    override val suggesterTitle: String = "What now?"
    override val suggesterMinutes: String = "Minutes available"
    override val suggesterNoCandidates: String = "Nothing fits right now. Try a longer window."
    override val suggesterRefresh: String = "Suggest again"
    override val standingLastTwelveWeeks: String = "Last 12 weeks"
    override val standingHeatmapCaption: String = "each square is one day — brighter means more XP"
    override val standingXpThisWeek: String = "XP this week"
    override val standingClosestSkill: String = "Closest to next level"
    override val standingWhatNow: String = "What should I do now?"
    override val standingNothingOpen: String = "No open quests. Press N to add one."
    override val actionStart: String = "Start"
    override val messageNoQuests: String = "Nothing here."
    override val messageNothingDueToday: String = "Nothing due today. That is allowed."
    override val messageNoOverdue: String = "Nothing overdue."
    override val messageNoReminders: String = "No reminders to come. Add one from a quest's details."
    override val pickerMove: String = "move"
    override val pickerChoose: String = "choose"
    override val pickerClose: String = "close"
    override val pickerSearchSkills: String = "Search skills…"
    override val pickerSearchQuests: String = "Search quests…"
    override val pickerDurationHint: String = "Search, or type minutes…"
    override val pickerRepeatHint: String = "Search, or type e.g. 3 days…"
    override val actionMoveUnder: String = "Move under…"
    override val actionAddInline: String = "+ add"
    override val actionChange: String = "change"
    override val rewardsSubtitle: String = "Treats you set yourself, paid for with points"
    override val inventorySubtitle: String = "Things you own; consumables run out as you use them"
    override val sectionReadyToClaim: String = "Ready to claim"
    override val sectionSavingUp: String = "Saving up for"
    override val sectionConsumables: String = "Consumables"
    override val sectionKeepsakes: String = "Keepsakes"
    override val actionNewReward: String = "New reward"
    override val actionNewItem: String = "New item"
    override val itemOutOfStock: String = "out of stock"
    override val skillsBalance: String = "Balance"
    override val skillsChoose: String = "Choose skills"
    override val skillsBalanceCaption: String = "Which skills you have been working on most"
    override val skillsUncategorised: String = "Other"
    override val skillNotStarted: String = "not started yet"
    override val skillNoXp: String = "no XP yet"
    override val actionNewSkill: String = "New skill"
    override val skillSortLevel: String = "level"
    override val skillSortName: String = "name"
    override val skillSortRecent: String = "recent"
    override val fieldParent: String = "Parent quest"
    override val topLevel: String = "Top level"
    override val repeatNever: String = "Doesn't repeat"
    override val noEstimate: String = "No estimate"
    override val messageNotEnoughPoints: String = "Not enough reward points."
    override val messageQuestCompleted: String = "Quest completed"
    override val messageQuestFailed: String = "Quest failed"
    override val messageQuestDeleted: String = "Quest deleted"
    override val messageRewardClaimed: String = "Reward claimed"
    override val messageSaved: String = "Saved"
    override val messageExported: String = "Export written"
    override val messageCopied: String = "Copied to clipboard"
    override val messageSearchPlaceholder: String = "Search quests…"
    override val messageCommandPlaceholder: String = "Type a command…"
    override val messageQuickAddPlaceholder: String = "What needs doing?"
    override val messageNoResults: String = "No matches."
    override val messageSelected: String = "selected"
    override val messageSubQuests: String = "sub-quests"
    override val messageLogLines: String = "events replayed"
    override val messageSkippedLines: String = "unreadable lines were skipped"
    override val messageCompacted: String = "Logs compacted"
    override val messageNothingToCompact: String = "Nothing to compact."
    override val settingsFolder: String = "Sync folder"
    override val settingsFolderBody: String = "Everything lives here as plain text. Sync it with whatever you already use — Syncthing, Git, Nextcloud, a cloud drive. Quests never talks to a network."
    override val settingsDevice: String = "This device"
    override val settingsDeviceBody: String = "Only this device ever writes to its own log file, so no sync tool can create a conflict."
    override val settingsSounds: String = "Completion sound"
    override val settingsFailureCosts: String = "Failing a quest costs its reward points"
    override val settingsFailureCostsBody: String = "Off by default: failure is recorded, not punished."
    override val settingsReminders: String = "Reminders"
    override val settingsTray: String = "Keep running in the tray"
    override val settingsLaunchView: String = "Opens on"
    override val settingsLaunchViewBody: String = "The screen Quests shows when it starts."
    override val settingsIcsExport: String = "Generate calendar feed"
    override val settingsIcsExportBody: String = "Writes export/quests.ics. Subscribe your phone or system calendar to that file and reminders arrive through a calendar you already trust."
    override val settingsMarkdownExport: String = "Generate Markdown snapshot"
    override val settingsHotkey: String = "Global quick capture"
    override val settingsHotkeyInstall: String = "Install GNOME shortcut"
    override val settingsHotkeyInstalled: String = "Shortcut installed."
    override val settingsHotkeyFailed: String = "Could not install it automatically. Set it up by hand — see Help."
    override val settingsAbout: String = "About"
    override val settingsVersion: String = "Version"
    override val settingsLicence: String = "Licensed under the GNU General Public License v3."
    override val settingsDanger: String = "Maintenance"
    override val firstRunTitle: String = "Where should your quests live?"
    override val firstRunBody: String = "Everything is plain text in one folder. Put it inside Syncthing, Dropbox, OneDrive or iCloud Drive to share it between computers."
    override val firstRunChoose: String = "Choose or create a folder…"
    override val firstRunUseFolder: String = "Use this folder"
    override val firstRunCreateFolder: String = "Create this folder"
    override val firstRunNewFolder: String = "New folder — Quests will create it."
    override val firstRunEmptyFolder: String = "Empty folder, ready to use."
    override val firstRunOtherFiles: String = "Quests will keep its files beside what is already here."
    override val firstRunPreviousUnavailable: String = "Quests couldn't open the folder it used last time. If it is on a drive, connect the drive, or choose another folder."
    override val firstRunCannotCreate: String = "Quests couldn't create or write to that folder. Choose another one."
    override val firstRunCannotOpen: String = "Quests couldn't read the quests in that folder. Choose another one."
    override val folderChooserTitle: String = "Choose a folder for your quests"
    override val folderNotAFolder: String = "That is a file, not a folder."
    override val folderNotWritable: String = "Quests can't write to this folder."
    override val folderAlreadyInUse: String = "That is already the data folder."
    override val helpTitle: String = "Help"
    override val helpShortcuts: String = "Keyboard shortcuts"
    override val helpQuickAdd: String = "Quick-add syntax"
    override val helpQuickAddBody: String = "Type one line and let it fill in the fields."
    override val helpHotkeyTitle: String = "Global quick capture"
    override val helpSyncTitle: String = "Sync"
    override val helpCalendarTitle: String = "Reminders on your phone"
    override val scratchpadPlaceholder: String = "Add a line…"
    override val scratchpadGlobal: String = "Scratchpad"
    override val scratchpadForQuest: String = "Checklist"
    override val skillsEmpty: String = "No skills yet. Add one, then link quests to it."
    override val rewardsEmpty: String = "No rewards yet. Add something worth working for."
    override val inventoryEmpty: String = "Nothing in the inventory."
    override val questsEmpty: String = "No quests yet. Press N to add one."
    override val balanceNote: String = "XP excludes urgency on purpose: paying more for urgent work rewards leaving things until they are urgent."
    override val pinnedToTop: String = "Pinned to top"
    override val pinnedToBottom: String = "Pinned to bottom"
    override val priorityAutomatic: String = "Placed by priority"
    override val messageCaptureUnavailable: String = "Global quick capture is unavailable on this system; the app itself is unaffected."

    override fun xpAmount(amount: Int): String = "${grouped(amount.toLong())} XP"

    override fun pointsAmount(amount: Int): String = "${grouped(amount.toLong())} points"

    override fun levelUpMessage(skill: String, level: Int): String = "$skill reached level $level"

    override fun countSelected(count: Int): String = "$count selected"

    override fun countSubQuests(count: Int): String = if (count == 1) "1 sub-quest" else "$count sub-quests"

    override fun countOverdue(count: Int): String = if (count == 1) "1 quest is overdue" else "$count quests are overdue"

    override fun countOpen(count: Int): String = if (count == 1) "1 open quest" else "$count open quests"

    override fun dueOn(date: String): String = "Due $date"

    override fun completedOn(date: String): String = "Completed $date"

    override fun repeatsEvery(every: Int, unit: String): String = if (every == 1) "Every $unit" else "Every $every ${unit}s"

    override fun remindBefore(minutes: Int): String = "$minutes min before"

    override fun skippedLinesWarning(count: Int): String = "$count unreadable log lines were skipped"

    override fun compactedSummary(events: Int, fields: Int): String = "Dropped $events superseded events and $fields superseded fields"

    override fun streakDays(days: Int): String = if (days == 1) "1 day" else "$days days"

    override fun percent(value: Float): String = "${(value * 100).toInt()}%"

    override fun todayOfTotal(total: Int): String = "of $total done"

    override fun questsWord(count: Int): String = if (count == 1) "quest" else "quests"

    override fun weekVsLast(thisWeek: Int, lastWeek: Int): String = when {
        lastWeek == 0 -> "nothing last week"
        thisWeek > lastWeek -> "up from $lastWeek last week"
        thisWeek < lastWeek -> "down from $lastWeek last week"
        else -> "same as last week"
    }

    override fun xpUntilLevel(xp: Long, level: Int): String = "$xp XP until level $level"

    override fun levelLower(level: Int): String = "level $level"

    override fun overdueFor(days: Long): String = when (days) {
        0L -> "today"
        1L -> "1 day"
        else -> "$days days"
    }

    override fun moreCount(count: Int): String = "+$count more"

    override fun newSkill(name: String): String = if (name.isBlank()) "+ New skill…" else "+ New skill “$name”"

    override fun useMinutes(minutes: Int?): String =
        if (minutes == null) "Type a number of minutes" else "Use ${durationShort(minutes)}"

    override fun repetitionLabel(repetition: Repetition): String {
        val unit = repeatUnit(repetition.unit)
        return if (repetition.every == 1) "Every $unit" else "Every ${repetition.every} ${unit}s"
    }

    override fun useRepetition(repetition: Repetition?): String =
        repetition?.let { repetitionLabel(it) } ?: "Type a number and a unit, e.g. 3 days"

    override fun youHavePoints(points: Int): String = "You have ${pointsAmount(points)}"

    override fun morePointsNeeded(points: Int): String =
        if (points == 1) "1 more point needed" else "${grouped(points.toLong())} more points needed"

    override fun rewardDescription(claimCount: Int, costIncrement: Int?, incrementEvery: Int?): String {
        val claimed = when (claimCount) {
            0 -> "never claimed"
            1 -> "claimed once"
            else -> "claimed $claimCount times"
        }
        val rises = if (costIncrement != null && incrementEvery != null && costIncrement > 0 && incrementEvery > 0) {
            val after = if (incrementEvery == 1) "every claim" else "every $incrementEvery claims"
            "+${pointsAmount(costIncrement)} after $after"
        } else {
            null
        }
        return listOfNotNull(claimed, rises).joinToString(" · ")
    }

    override fun itemDescription(consumable: Boolean): String = if (consumable) "consumable" else "keeps"

    override fun youHave(quantity: Int): String = "you have $quantity"

    override fun sortedBy(what: String): String = "Sort: $what"

    override fun lastWorkedOn(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0L -> "last worked on today"
            days == 1L -> "last worked on yesterday"
            days < 7 -> "last worked $days days ago"
            else -> "last worked on ${date.dayOfMonth} ${monthShort(date.month)}"
        }
    }

    override fun xpProgress(total: Long, nextLevelAt: Long): String = "${grouped(total)} / ${grouped(nextLevelAt)} XP"

    override fun questsMoved(count: Int): String = if (count == 1) "Moved 1 quest" else "Moved $count quests"

    override fun movedDetail(parentTitle: String?): String = parentTitle?.let { "under $it" } ?: "to the top level"

    override fun dueMeta(due: LocalDate, time: LocalTime?, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
        if (days < 0) return if (days == -1L) "overdue 1 day" else "overdue ${-days} days"
        val day = when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days < 7 -> weekdayLong(due.dayOfWeek)
            else -> "${due.dayOfMonth} ${monthShort(due.month)}"
        }
        val at = time?.let { String.format(Locale.ROOT, " %02d:%02d", it.hour, it.minute) }.orEmpty()
        return "due $day$at"
    }

    // Minutes up to two hours, because "90 min" is read faster than "1h 30m".
    override fun durationShort(minutes: Int): String =
        if (minutes < 120) "$minutes min" else Dates.formatMinutes(minutes)

    override fun urgencyMeta(urgency: Urgency): String = when (urgency) {
        Urgency.CRITICAL -> "critical"
        else -> "${urgency(urgency).lowercase()} urgency"
    }

    override fun reminderAt(moment: LocalDateTime, today: LocalDate): String {
        // Locale.ROOT: some locales format digits in another script.
        val time = String.format(Locale.ROOT, "%02d:%02d", moment.hour, moment.minute)
        val day = when (moment.toLocalDate()) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> "${weekdayShort(moment.dayOfWeek)} ${moment.dayOfMonth} ${monthShort(moment.month)}"
        }
        return "Reminder $day $time"
    }

    override fun questMeta(minutes: Int?, xp: Int): String {
        return listOfNotNull(minutes?.let { durationShort(it) }, xpAmount(xp)).joinToString(" · ")
    }

    // Each line says what the suggester actually did at that energy (see
    // Suggester.score): it is a reason, not a mood.
    override fun energyCaption(band: EnergyBand): String = when (band) {
        EnergyBand.HIGH -> "Your energy is high, so these include the hard and frightening ones:"
        EnergyBand.MEDIUM -> "Your energy is steady, so these are a balanced mix:"
        EnergyBand.LOW -> "Your energy is low, so these are light and easy:"
        EnergyBand.SPENT -> "You are running on empty, so these are the gentlest there are:"
    }

    override fun weekdayInitial(day: DayOfWeek): String = weekdayLong(day).take(1)

    override fun heatmapDay(date: LocalDate, xp: Int, quests: Int): String {
        val day = "${weekdayShort(date.dayOfWeek)} ${date.dayOfMonth} ${monthShort(date.month)}"
        return when (quests) {
            0 -> "$day · nothing finished"
            1 -> "$day · $xp XP · 1 quest"
            else -> "$day · $xp XP · $quests quests"
        }
    }

    override fun heatmapDescription(xp: Int, activeDays: Int, todayXp: Int): String {
        val days = if (activeDays == 1) "1 day" else "$activeDays days"
        return "Activity over the last 12 weeks: $xp XP across $days. Today: $todayXp XP."
    }

    override fun longDate(date: LocalDate): String = "${weekdayLong(date.dayOfWeek)} ${date.dayOfMonth} ${monthLong(date.month)}"

    override fun weekdayShort(day: DayOfWeek): String = weekdayLong(day).take(3)

    override fun monthShort(month: Month): String = monthLong(month).take(3)

    /**
     * "1 210": thousands set apart by a narrow no-break space, the SI
     * convention, which reads the same in every locale and never lets a
     * number wrap in the middle.
     */
    private fun grouped(value: Long): String {
        val digits = kotlin.math.abs(value).toString()
        val head = digits.length % 3
        val groups = listOfNotNull(digits.take(head).ifEmpty { null }) + digits.drop(head).chunked(3)
        return (if (value < 0) "−" else "") + groups.joinToString("\u202F")
    }

    // Spelled out rather than taken from java.time's display names, which
    // follow the JVM's default locale: on a machine set to another language
    // they would put foreign day names into an English interface.
    private fun weekdayLong(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }

    private fun monthLong(month: Month): String = when (month) {
        Month.JANUARY -> "January"
        Month.FEBRUARY -> "February"
        Month.MARCH -> "March"
        Month.APRIL -> "April"
        Month.MAY -> "May"
        Month.JUNE -> "June"
        Month.JULY -> "July"
        Month.AUGUST -> "August"
        Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "October"
        Month.NOVEMBER -> "November"
        Month.DECEMBER -> "December"
    }

    override fun firstRunUse(path: String): String = "Use $path"

    override fun firstRunCreate(path: String): String = "Create $path"

    override fun firstRunExisting(devices: Int): String =
        if (devices == 1) "Existing quests found — 1 device" else "Existing quests found — $devices devices"

    override fun folderCannotRemember(configDir: String): String = "Quests couldn't save this choice in $configDir."

    override fun folderSwitchScheduled(path: String): String =
        "From the next launch Quests will use $path, with a copy of this device's log. The current folder is left untouched."

    override fun folderSwitchPending(path: String): String = "Switching to $path at the next launch."

    override fun folderSwitched(path: String): String = "Now using $path. The previous folder was left untouched."

    override fun folderSwitchConflict(file: String): String =
        "Kept the current folder: the new one already holds a different $file."

    override fun folderSwitchFailed(path: String): String =
        "Kept the current folder: this device's files could not be copied to $path."

    override fun priorityBreakdown(breakdown: PriorityBreakdown): String =
        "Priority ${score(breakdown.total)} — urgency ${score(breakdown.urgency)} · due ${score(breakdown.due)} · " +
            "fear ${score(breakdown.fear)} · age ${score(breakdown.age)}"

    /** Up to two decimals and no trailing zeros: "4", "0.4", "0.35". */
    private fun score(value: Double): String =
        java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    override fun difficulty(value: Difficulty): String = when (value) {
        Difficulty.TRIVIAL -> "Trivial"
        Difficulty.VERY_LOW -> "Very low"
        Difficulty.LOW -> "Low"
        Difficulty.MODERATE -> "Moderate"
        Difficulty.HIGH -> "High"
        Difficulty.VERY_HIGH -> "Very high"
        Difficulty.EXTREME -> "Extreme"
    }

    override fun fear(value: Fear): String = when (value) {
        Fear.NONE -> "None"
        Fear.MILD -> "Mild"
        Fear.MODERATE -> "Moderate"
        Fear.STRONG -> "Strong"
        Fear.SEVERE -> "Severe"
    }

    override fun urgency(value: Urgency): String = when (value) {
        Urgency.WHENEVER -> "Whenever"
        Urgency.LOW -> "Low"
        Urgency.NORMAL -> "Normal"
        Urgency.HIGH -> "High"
        Urgency.CRITICAL -> "Critical"
    }

    override fun repeatUnit(value: RepeatUnit): String = when (value) {
        RepeatUnit.HOUR -> "hour"
        RepeatUnit.DAY -> "day"
        RepeatUnit.WEEK -> "week"
        RepeatUnit.MONTH -> "month"
        RepeatUnit.YEAR -> "year"
    }

    override fun view(value: QuestView): String = when (value) {
        QuestView.TODAY -> "Today"
        QuestView.TOMORROW -> "Tomorrow"
        QuestView.OVERDUE -> "Overdue"
        QuestView.NEXT -> "Next"
        QuestView.PLAN -> "Plan"
        QuestView.ALL -> "All"
        QuestView.COMPLETED -> "Completed"
        QuestView.LAST_SEVEN_DAYS -> "Last 7 days"
        QuestView.FAILED -> "Failed"
        QuestView.NO_DUE_DATE -> "No due date"
        QuestView.REMINDERS -> "Reminders"
    }

    override fun bucket(value: DueBucket): String = when (value) {
        DueBucket.OVERDUE -> "Overdue"
        DueBucket.TODAY -> "Today"
        DueBucket.TOMORROW -> "Tomorrow"
        DueBucket.THIS_WEEK -> "This week"
        DueBucket.LATER -> "Later"
        DueBucket.SOMEDAY -> "Someday"
    }

    override fun sortKey(value: SortKey): String = when (value) {
        SortKey.PRIORITY -> "Priority"
        SortKey.MANUAL -> "Manual order"
        SortKey.NAME -> "Name"
        SortKey.CREATED -> "Created"
        SortKey.UPDATED -> "Updated"
        SortKey.DUE_DATE -> "Due date"
        SortKey.REMINDER -> "Next reminder"
        SortKey.DIFFICULTY -> "Difficulty"
        SortKey.FEAR -> "Fear"
        SortKey.URGENCY -> "Urgency"
        SortKey.DURATION -> "Duration"
        SortKey.XP -> "XP"
        SortKey.XP_TO_NEXT_LEVEL -> "XP to next level"
        SortKey.LAST_WORKED -> "Last worked on"
        SortKey.COMPLETED -> "Date completed"
        SortKey.RANDOM -> "Random"
    }

    override fun energyBand(value: EnergyBand): String = when (value) {
        EnergyBand.HIGH -> "High"
        EnergyBand.MEDIUM -> "Medium"
        EnergyBand.LOW -> "Low"
        EnergyBand.SPENT -> "Spent"
    }

    override fun launchView(value: LaunchView): String = when (value) {
        LaunchView.STANDING -> navStanding
        LaunchView.TODAY -> navToday
        LaunchView.ALL -> navAll
        LaunchView.PLAN -> navPlan
    }

    override fun suggestionReason(value: SuggestionReason): String = when (value) {
        SuggestionReason.FITS_THE_TIME -> "fits the time you have"
        SuggestionReason.OVERDUE -> "overdue"
        SuggestionReason.DUE_TODAY -> "due today"
        SuggestionReason.URGENT -> "urgent"
        SuggestionReason.GOOD_ENERGY_FOR_FEAR -> "your energy is high enough for the scary one"
        SuggestionReason.GENTLE_ON_LOW_ENERGY -> "gentle on low energy"
        SuggestionReason.UNTOUCHED_FOR_A_WHILE -> "untouched for a while"
        SuggestionReason.CLOSE_TO_A_LEVEL -> "would tip a skill over a level"
        SuggestionReason.QUICK_WIN -> "a quick win"
    }
}
