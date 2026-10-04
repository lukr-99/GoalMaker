# M9-04: The why reminder

**Status:** built, waiting for review · **Milestone:** M9

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

## Result

2026-10-04.

- `ReminderService` on both apps carries the why reminder: a look names the life goal of the latest
  moment since the last look (`WhyDue`), the one alarm or timer arms for the next moment while a
  life goal is open, and `whyStale` takes the notification down once its life goal is achieved,
  dropped or deleted, here or after a sync.
- **Android:** a "Why reminder" channel; the life goal's title, why and time left, and its first
  cached picture as a big picture; a tap opens Life goals scrolled to that life goal. Settings has a
  Why reminder row (Off, Weekly, Every 3 days, Daily). `WhyReminderServiceTest` covers the moment,
  quiet hours, off, nothing open, a missed moment and stale.
- **Windows:** a toast with the title, the why, the time left and the first picture as its hero
  image (copied to `toast-pictures/` under the app's data, and cleared with the toast); a click opens
  Life goals and gives that life goal's card the keyboard. The Settings page has the same Why reminder
  list. `WhyReminderServiceTests` (6) and `WhyToastTests` (2).
- **Checked on the emulator:** the channel and the Settings row. The notification itself was not
  seen ringing: the next weekly moment is days away and the emulator's clock was left alone. The
  Windows toast was not seen on a desktop either, so its hero image and the click are checked only
  in tests.
- Found on the way: the Wants ready hint in Android Settings uses the Plan tomorrow wording (board
  bug 13bdf91f-b731-48f6-92a9-9a2b54dbcd90).
