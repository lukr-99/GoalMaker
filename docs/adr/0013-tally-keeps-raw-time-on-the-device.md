# ADR 0013: Tally keeps raw time on the device and syncs only daily totals by category

Tally records which app or window was in front and for how long, on the phone (Android's usage
history) and the PC (the tray app's foreground tracker). That raw trail is the most private data
GoalMaker would hold. It never leaves the device: Android keeps no copy of its own and reads the
system's history (about a week), and Windows keeps a local log for 30 days. What syncs is one row per
day, device, category and (on the PC) project with the minutes spent, so app and window names never
reach Supabase, backups or the connector. The sorting rules (app, window title or folder to
category) are the owner's own words, not usage, so the owner's rules and categories do sync, next to
defaults shipped in `contracts/content/tally-rules.json`; the matching is pinned by
`contracts/vectors/tally.json` so both devices sort alike. The cost: Claude can answer "how much
video" but not "how much YouTube" unless a rule gives YouTube its own category. Tally is off until
the owner turns it on, on each device.
