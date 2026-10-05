# M10: Calendar events and adding from the calendar

Things that take up days rather than get done: a trip, a holiday, a conference. An **event** has a
title, a first and a last day, and maybe notes and an area. It is drawn as one bar across its days on
the calendar and shows on Today while it lasts. The calendar gets the bottom bar, and several days
can be picked at once to add one event across them or the same task on each
([docs/calendar.md](../../docs/calendar.md), ADR 0019, spec stories 120 to 123). From the board item
"Creation and multiselect creation form calendar" (GoalMaker: 3764d088-5926-4832-ab54-a5a3151fa29c).

## Issues

1. [01-data-and-rules.md](01-data-and-rules.md): the table, the vectors, the lists.
2. [02-calendar-on-android.md](02-calendar-on-android.md): bars, the day's events, the event sheet, Today.
3. [03-calendar-on-windows.md](03-calendar-on-windows.md): the same on the PC.
4. [04-adding-from-the-calendar.md](04-adding-from-the-calendar.md): the bottom bar and picking several days, both apps.
5. [05-connector.md](05-connector.md): the connector tools and the quick chat.

## Decisions (owner, 2026-10-05)

- **A multi-day event** is a new kind of item, not a task with a span: it is not ticked off, and it
  shows on every one of its days.
- **Picking several days asks each time**: one event across them, or a copy of a task on each day.
- **Adding happens in the bottom bar**, which the calendar gets like Today and the Inbox.

## Not in M10

- Times inside a day (an event from 14:00 to 15:00), repeating events and reminders for events.
- Calendar sync with Google or Outlook.
