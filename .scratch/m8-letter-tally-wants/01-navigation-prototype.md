# M8-01: A navigation with room to grow: the prototype

**Status:** todo · **Milestone:** M8

## Scope
- The phone is full on both edges: the bottom bar holds Today, Tomorrow, Inbox, Projects and
  Calendar, and the top bar carries six icons (sync, Habits, Goals, the dots with Reviews, Stats and
  Archive, Plan tomorrow, Settings). The Windows sidebar already lists eleven places (Today,
  Tomorrow, Inbox, Goals, Habits, Reviews, Calendar, Projects, Stats, Archive, Settings). M8 adds
  Wants and Tally (spec, story 99; board: "Navigation that scales").
- A throwaway prototype (the `prototype` skill) of three or four directions, clickable at phone size
  and at Windows size, for the owner to choose from:
  - a **More** place of big tiles, each with an accent icon and a live count or ring, instead of a menu;
  - a **drawer or rail with sections** (Plan, Track, Reflect);
  - **tabs the owner pins**: pick which four or five places sit in the bar;
  - **Windows**: sidebar sections that group and collapse.
- Motion for each: how a place arrives and how the bar or tiles respond, in the design spec's
  durations.

## Acceptance criteria
- The owner picks one direction per app, recorded as an ADR (with what was rejected and why) and in
  `docs/design/spec.md` under Layout.
- Nothing from the prototype is merged into the apps.

## Vectors to add
- None, unless the choice is pinnable tabs: then which places can be pinned, the default set and the
  order live in `contracts/vectors/navigation.json`, read by both apps.

## Check
- Emulator: the prototype at phone size and at the largest text size.
- Windows: the prototype at the narrowest and the widest window.
- Endpoint: nothing.
