# M9: Life goals

What the owner wants in their life in the long run ("Own an Audi R8 within 10 years"), with why it
matters, a by date and pictures. A why reminder shows one now and then, and a widget on the phone
cycles through the pictures ([docs/life-goals.md](../../docs/life-goals.md), ADR 0018, spec stories
114 to 119). From the board item "Add Life goals" (GoalMaker: 58efae33-1943-4585-90d2-fd6d8a4b49b5).

## Issues

1. [01-data-and-rules.md](01-data-and-rules.md): the tables, the bucket, the vectors, the lists.
2. [02-place-on-android.md](02-place-on-android.md): the Life goals place and pictures on the phone.
3. [03-place-on-windows.md](03-place-on-windows.md): the same on the PC.
4. [04-why-reminder.md](04-why-reminder.md): the why reminder on both apps.
5. [05-widget.md](05-widget.md): the picture widget on Android.
6. [06-connector.md](06-connector.md): the connector tools.

## Decisions (owner, 2026-10-04)

- **A separate list.** Life goals stand outside the goal cascade, in their own place, not as a rung
  above the year on the ladder.
- **Why reminders** come about weekly at a random time by default; one device setting picks off,
  weekly, every 3 days or daily.
- **Pictures** are uploaded from the gallery or a file, several per life goal, and sync to both apps.

## After M9

- Add "Own an Audi R8" with a by date of October 2036, the owner's first life goal.
