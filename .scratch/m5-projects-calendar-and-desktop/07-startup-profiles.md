# M5-07: Add to Startup Profiles

**Status:** todo · **Milestone:** M5

## Scope
- A settings row, shown only when Startup Profiles is installed, that registers GoalMaker with it
  through that app's own public contract and consent window (spec, story 84).
- GoalMaker keeps its own "start in the tray when I sign in" setting, on by default (story 81);
  Startup Profiles is an extra, never a replacement.

## Acceptance criteria
- A test for finding Startup Profiles and building the registration, with the row hidden when it is
  not installed. Nothing writes outside GoalMaker's own keys without the other app's consent window.
