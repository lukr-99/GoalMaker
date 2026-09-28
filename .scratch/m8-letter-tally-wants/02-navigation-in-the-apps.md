# M8-02: Pinned places and the Places hub in both apps

**Status:** done · **Milestone:** M8

## Scope
- The navigation from M8-01 ([ADR 0014](../../docs/adr/0014-pinned-places-and-the-places-hub.md),
  `docs/design/spec.md` → Navigation), Android then Windows, with every existing place reachable.
  Wants and Tally show up once they exist, unpinned.
- **The shared rules**, as `PlaceRules` in Kotlin and C#: the places and their order, the default
  pins per device kind, pinning and unpinning (the phone's limit of four, a fifth refused, the last
  pin kept), what a stored pin list that names an unknown or removed place becomes, and the Places
  count (what waits in unpinned places).
- **Android:** `MainDestination` becomes the pins plus Places. The Places page is live tiles over the
  existing lists (rings from `HabitRules` and `GoalRules`, counts from the lists) with the Edit mode.
  The top bar drops Habits, Goals and the menu and keeps sync, Plan tomorrow and Settings. Pins are
  stored in `SettingsStore`.
- **Windows:** the sidebar becomes Go to… (Ctrl+K), Pinned, All places (collapsible) and Settings,
  with a Pin button on every page's toolbar and the Ctrl+K box. Pins are stored in the JSON settings.
- Deep links keep working: `--open <place>` and `goalmaker://` on Windows, the widgets' and the
  notifications' targets on Android, whether or not the place is pinned.
- Motion as the prototype: tiles grow in with the emphasized spring and a stagger, the pill behind a
  selected tab and the sidebar's accent bar animate in, Edit wiggles; reduce motion turns all of it
  into short fades. The accent carries the selected tab, the pins and the headline.

## Acceptance criteria
- Every `navigation.json` case passes in Kotlin and C#.
- View-model tests on both apps: pinning and unpinning with the limit, the Places count, Ctrl+K's
  filter and keys, a pin list that names a place that no longer exists.
- Every place from before is reachable in at most two taps or clicks.
- `tools/check_accessibility.py` passes: every new control has a name, and the pin toggles say
  pinned or not.

## Vectors to add
- `contracts/vectors/navigation.json` (new, listed in `contracts/README.md`): `places` (the ids and
  order), `defaults` (phone and PC), `pin` (pin, unpin, a fifth on the phone refused, the last kept,
  the PC unlimited), `stored` (an unknown id dropped, duplicates dropped, an empty list back to the
  defaults), `count` (what the Places tab shows).

## Check
- Emulator: every place from the bar and from Places, Edit at four pins, back from an unpinned
  place, a widget and a notification opening an unpinned place, the largest text size, all four
  themes light and dark, reduce motion.
- Windows: every place, `--open` for each, pin and unpin from a toolbar, Ctrl+K with the keyboard
  only, the sidebar collapsed and expanded, Tab order.
- Endpoint: nothing.

## Prototype
- Branch `prototype/m8-navigation`, `docs/design/prototypes/navigation-prototype.html`, variant D.
  Run `python -m http.server 8765` from the repository root on that branch and open
  http://localhost:8765/docs/design/prototypes/navigation-prototype.html?variant=D&device=both.
  It is a reference for layout and motion, not code to copy.

## Result

2026-09-28, both apps.

- `contracts/vectors/navigation.json` with `PlaceRules` in Kotlin (`domain/navigation`) and C#
  (`GoalMaker.Core/Navigation`); both pass every case. Pins are a device setting on each app
  (`SettingsStore.pins`, `ISettingsStore.PinnedPlaces`), read back through `PlaceRules.stored`.
- **Android:** the bottom bar is the four pins plus Places. Every place is a tab of `MainScreen`, so a
  pinned Habits, Goals, Reviews, Stats or Archive shows the mark and the shared actions, and one
  opened from Places shows a back arrow to it; Back goes to Places from there and home otherwise.
  The top bar keeps sync, Plan tomorrow and Settings. The Places hub (`ui/places`) has live tiles from
  `PlacesBoard` (rings for Today, Habits and Goals, counts, the Letter as a hero tile once it lands),
  Edit with a pin toggle per tile, the unpinned tiles faded at four, a staggered spring entrance and a
  wiggle, both off with reduce motion. Checked on the emulator: pin refused at four, swap, back from
  Calendar to Places, pins kept after a restart.
- **Windows:** the sidebar is Go to (Ctrl+K) in the pane header, a Pinned label and the pins, All
  places folding out the rest, then Activity, Areas and tags and Settings. The Pin toggle for the page
  on show sits in the title bar (the pages' toolbars are already full), and every sidebar item has Pin
  or Unpin on a right click. Go to filters as you type, arrows move, Enter opens, Esc or the scrim
  closes, with a short grow and fade. Checked on the dev build through UI Automation: unpin and pin
  Today, the sidebar rebuilt with the page marked, Go to filtering.
- Tests: `PlaceRulesContractTest(s)`, `PlacesBoardTest`, the settings stores on both apps,
  `PlacesViewModelTests` on Windows.
- Left: Android's pin toggles have no view-model test of their own beyond `PlacesViewModel.togglePin`
  going through `PlaceRules`; widgets and notifications still open Today and a review as before,
  which is where they belong whether pinned or not.
