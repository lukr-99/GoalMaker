# M8-02: The chosen navigation in both apps

**Status:** todo · **Milestone:** M8

## Scope
- The navigation picked in M8-01, Android then Windows, with every existing place reachable, and
  Wants and Tally given their slots, shown only once they exist.
- Deep links keep working: `--open <place>` and `goalmaker://` on Windows, the widgets' and the
  notifications' targets on Android.
- Polished motion (standard 250 ms, reduce motion honored), the accent prominent on the selected
  place, and icons and labels carefully aligned in all four themes.

## Acceptance criteria
- A view-model test per app for the places and their order (and pinning, if chosen).
- Every place from before is still reachable in at most two taps or clicks.
- `tools/check_accessibility.py` passes: every new control has a name.

## Vectors to add
- `navigation.json` only if M8-01 chose pinnable tabs.

## Check
- Emulator: every place reached from the new navigation, back behaves, the largest text size, all
  four themes light and dark.
- Windows: every place, `--open` for each, the sidebar collapsed and expanded, the keyboard's Tab
  order.
- Endpoint: nothing.
