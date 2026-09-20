# Pitfalls

Mistakes this project has already made, kept short so they are not made twice. Add one when a bug
took longer to find than to fix.

## A queued row must fill every column the server needs

2026-09-20, M5. Saving the composer's `+Project` and `?` set `item_type` and `board_column` on a new
task but left `priority` alone, so the outbox queued an explicit `null` and Postgres refused the push
with `23502: null value in column "priority" violates not-null constraint`. **A null in the payload
defeats the column's default**: the default only applies to a column the insert leaves out, and the
replica sends every column the synced-tables contract names.

Adding a column to a synced table means giving it a value wherever a row is created, in both apps and
the connector. The test that checks a new row is complete also checks that every column the server
has as not null carries a value.

## The apps' tests never meet the server

The same bug: both apps' tests run against a test replica with a fake remote, so nothing there can
refuse a row. Only the connector's endpoint test and running the apps against the local stack reach
real constraints. After changing what a row carries, add a task in a running app against
`npx supabase start`, not only in the tests.

## Every migration needs its before and after fixtures

2026-09-20. `0012_task_moves.sql` shipped without `supabase/migration-tests/0012_before.sql`, and the
harness stops at the first migration that has none, so the whole chain went unchecked from M4 until
someone ran it. `python tools/supabase_migrations.py test` says exactly which file it wants; run it
with the migration, not later.

## A test that formats a date or a time must format it the way the code does

2026-09-20 and 2026-09-18. `"14,20 SEP"` and `"18:00"` are one machine's calendar and clock. A
culture that writes `Sept` or `6:00 PM` fails a test that hard-codes them. Build the expected text
with the same `CultureInfo.CurrentCulture` format the view model uses.

## CI that has never run proves nothing

The first push of this repository was 2026-09-20, so every check had been green only on one machine
until then. Two of the pitfalls above were waiting in `main` the whole time.

## A debug build is signed per machine

An Android debug APK is signed with `~/.android/debug.keystore`, which differs on every machine and
is not the release key on the backup drive. A phone that holds a debug build from another machine
cannot be updated in place: `adb uninstall com.goalmaker.app.debug` first, which clears that app's
replica, so check its outbox is empty before doing it.
