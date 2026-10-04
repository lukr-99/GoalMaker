# M9-04: The why reminder

**Status:** planned · **Milestone:** M9

## Scope
- A device setting under reminders: Off, Weekly (default), Every 3 days, Daily.
- `WhyReminder` in Kotlin and C# over the `whyMoment`, `whyGoal`, `whyDue` and `whyNext` vectors, carried by
  `ReminderService` like the wants notification: no reminder rows, the one alarm or timer, the latest missed
  one caught up at the next look.
- **Android:** a channel of its own, the life goal's title, why and time left, its first picture as a
  big picture when cached; a tap opens the place on that life goal. **Windows:** a toast with the same
  text and the picture as a hero image.

## Acceptance criteria
- `ReminderService` tests on both apps: the moment, quiet hours, off, no open life goal, a missed
  moment.

## Check
- Emulator with a dev clock: it rings at the worked-out moment with the picture and opens the goal.
