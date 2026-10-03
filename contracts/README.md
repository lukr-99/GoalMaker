# Contracts

Behavior that the Android app (Kotlin) and the Windows app (C#) must implement identically lives
here as data. Both test suites load the same files, so a disagreement fails CI in whichever app is
wrong. The Claude connector's TypeScript rules (`supabase/functions/_shared/rules/rules_test.ts`) run
the planning files too: lists, plan, recurrence, archive, the ritual ids in reminders, reviews, goals,
habits and projects, and `wants_test.ts`, `digest_test.ts`, `tally_test.ts` and `quickAdd_test.ts`
run wants, the review digest, Tally and the bottom bar's lines.

| File | Rule | Kotlin test | C# test |
| --- | --- | --- | --- |
| `vectors/semantic-version.json` | Semantic version parsing, precedence, update offer policy | `SemanticVersionContractTest` | `SemanticVersionContractTests` |
| `vectors/release-manifest.json` | Release manifest signature check and validation | `ReleaseManifestContractTest` | `ReleaseManifestContractTests` |
| `vectors/release-channel.json` | Where the update channel's manifest, signature and artifacts live on GitHub Releases (ADR 0010) | `ReleaseChannelContractTest` | `ReleaseChannelContractTests` |
| `vectors/sync-merge.json` | Sync merge, full resync, pull start, timestamp form ([sync](../docs/sync.md)) | `SyncRulesContractTest` | `SyncRulesContractTests` |
| `vectors/composer.json` | The composer's shortcut grammar ([composer](../docs/composer.md)) | `ComposerParserContractTest` | `ComposerParserContractTests` |
| `vectors/quick-add.json` | What the bottom bar reads from a want, habit or goal line ([composer](../docs/composer.md#adding-on-wants-habits-and-goals)) | `QuickAddLinesContractTest` | `QuickAddLinesContractTests` |
| `vectors/lists.json` | What Today, Tomorrow and Inbox hold, and filtering by area and tag, which Projects, the calendar and the archive share ([lists](../docs/lists.md)) | `ListRulesContractTest` | `ListRulesContractTests` |
| `vectors/plan.json` | The Plan tomorrow ritual ([plan tomorrow](../docs/plan-tomorrow.md)) | `PlanRulesContractTest` | `PlanRulesContractTests` |
| `vectors/recurrence.json` | Repeating tasks and their occurrences ([repeating](../docs/repeating.md)) | `RecurrenceContractTest` | `RecurrenceContractTests` |
| `vectors/archive.json` | The archive of done tasks and its search ([archive](../docs/archive.md)) | `ArchiveContractTest` | `ArchiveContractTests` |
| `vectors/markdown.json` | The light Markdown in task notes ([archive](../docs/archive.md)) | `LightMarkdownContractTest` | `LightMarkdownContractTests` |
| `vectors/reminders.json` | Reminder times, quiet hours and snooze ([reminders](../docs/reminders.md)) | `ReminderRulesContractTest` | `ReminderRulesContractTests` |
| `vectors/reviews.json` | Review periods and ids, the prompt rotation and the reactive prompts ([reviews](../docs/reviews.md)) | `ReviewRulesContractTest`, `PromptRulesContractTest` | `ReviewRulesContractTests`, `PromptRulesContractTests` |
| `vectors/activity.json` | What an activity log entry did ([activity](../docs/activity.md)) | `ActivityRulesContractTest` | `ActivityRulesContractTests` |
| `vectors/goals.json` | Goal periods, the cascade, progress, copying, pace and its order, the chain a picked goal lights, and the quick log amount ([goals](../docs/goals.md)) | `GoalRulesContractTest` | `GoalRulesContractTests` |
| `vectors/habits.json` | Habit cadences, periods, streaks, the heatmap, rings, check-in ids, check-ins toward goals, which habits are due today and on Today, the Habits page's groups, where a habit stands today, the week's dots and Today's all done card ([habits](../docs/habits.md)) | `HabitRulesContractTest` | `HabitRulesContractTests` |
| `vectors/stats.json` | The stats numbers and the review look back ([stats](../docs/stats.md)) | `StatsRulesContractTest` | `StatsRulesContractTests` |
| `vectors/projects.json` | Project boards: new items, moves, order ([projects](../docs/projects.md)) | `ProjectRulesContractTest` | `ProjectRulesContractTests` |
| `vectors/backup.json` | The export's format, what a restore refuses, and which row wins ([backup](../docs/backup.md)) | `BackupRulesContractTest` | `BackupRulesContractTests` |
| `vectors/calendar.json` | The week and month grids and what lands on a day ([calendar](../docs/calendar.md)) | `CalendarRulesContractTest` | `CalendarRulesContractTests` |
| `vectors/wants.json` | Want cooldowns, states, the ready notification, the stats block and the thresholds id ([wants](../docs/wants.md)) | `WantRulesContractTest` | `WantRulesContractTests` |
| `vectors/tally.json` | Tally: which category and project time goes to, editor folders, idle, daily totals and their ids, and the twelve-week stats ([tally](../docs/tally.md)) | `TallyRulesContractTest` | `TallyRulesContractTests` |
| `vectors/navigation.json` | Pinned places, the phone's limit of four, stored pins and the Places count (ADR 0014) | `PlaceRulesContractTest` | `PlaceRulesContractTests` |
| `vectors/settings.json` | The Settings page: section order, the chips or list only with 4+ sections, the current section (80 line, the bottom, a jump's target), the jump scroll's time, the jump and scroll hints with reduce motion, the Saved mark's timing, the update deep link ([design](../docs/design/spec.md#settings)) | `SettingsPageRulesContractTest` | `SettingsPageRulesContractTests` |
| `schemas/release-manifest.schema.json` | Shape of the manifest in the update channel | (documentation) | (documentation) |
| `schemas/synced-tables.json` | Every synced column once, for both replicas and the JSON mapping | (`tools/check_synced_tables.py`) | (`tools/check_synced_tables.py`) |
| `design/themes.json` | The four themes' tokens, and each theme's Settings highlight ([design](../docs/design/spec.md)) | `DesignTokensTest` | `DesignTokensTests` |
| `design/logo.json` | The mark's shape, written by `tools/generate_app_icon.py`; each theme colors it | `DesignTokensTest` | `DesignTokensTests` |

`content/prompts.json` is not a vector file but shipped content: the review prompt library both
apps and the connector read ([reviews](../docs/reviews.md)). `tools/check_prompts.py` validates it,
and the rotation over it is pinned by `vectors/reviews.json`.

`content/tally-rules.json` is shipped content too: Tally's default categories and sorting rules
([tally](../docs/tally.md)). `tools/check_tally_rules.py` validates it, and the matching over it is
pinned by `vectors/tally.json`.

## Rules for changing a contract

- Add cases; don't rewrite existing ones unless the behavior deliberately changes.
- Bump the file's `schema` number when the file format changes.
- Change both implementations in the same commit as the vector change.
- `release-manifest.json` is generated by `tools/generate_manifest_vectors.py` with throwaway keys;
  no private key is stored anywhere in the repository.
