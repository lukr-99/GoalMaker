# M7-03: The chat on Windows

**Status:** todo · **Milestone:** M7

## Scope
- The same as M7-02 in the Windows composer, including the quick-add mini window: the switch
  (Ctrl+Shift+Space? to be chosen with the existing shortcuts), the short thread above the composer,
  the thinking indicator, and the unavailable states.
- Copy in `Strings.xaml`; the view model gets text through `IStrings`.

## Acceptance criteria
- View-model tests with a fake assistant client, as on Android.
- Quick-add unchanged with the switch on quick-add.

## Check
- Windows against the local stack with a real key, in the main window and the mini window.

## Release
- With M7-01 and M7-02: the next minor release after the M8 ones.
