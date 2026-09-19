# M3-03: The shared planning rules in TypeScript

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- The connector must see the same Today, Tomorrow and Inbox as the apps and move a repeating task
  on the same way, so it runs the same rules: the planning day, the list rules, the archive search
  and recurrence (next occurrence), ported to TypeScript in `supabase/functions/_shared/rules/`.
- Each port runs the existing vector files in `contracts/vectors/` (lists, archive, recurrence,
  and the planning-day cases), like the Kotlin and C# tests.
- "Today" for the connector comes from the profile's time zone and day start. Both apps write the
  device's day start and time zone to the profile when they change or at sign-in, so all three
  agree.

## Acceptance criteria
- `deno task test` passes every case of the vector files it ports; a changed case fails it.
- Changing the day start on one app updates `profiles.day_rollover_hour`.

## Result
- `supabase/functions/_shared/rules/`: day arithmetic, the planning day, the list rules, the plan
  rules, recurrence, occurrences (successor and tag-link ids, repair), the archive search and UUID
  version 5. `rules_test.ts` runs lists.json, plan.json, recurrence.json, archive.json and the
  ritual ids in reminders.json; all pass.
- `planner/palette.ts` mirrors the area palette, with a test against contracts/design/themes.json.

- Both apps write the device's time zone and day start to the profile at sign-in and when the day
  start changes (Android `PostgrestProfileSettings`, Windows the same; Windows converts its zone id
  to an IANA one, using the region to pick the city). Checked on the local stack: the profile had
  the PC's zone and day start right after sign-in.
