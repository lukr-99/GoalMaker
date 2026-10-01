# ADR 0017: The Windows tray app on DotNetLib.Tray

ADR 0006 left `dotnetlib` out because it shipped only through a local folder feed, which CI could
not restore. Since then `dotnetlib` publishes its packages to a private GitHub Packages feed, and
CodePrint requires its `DotNetLib.Tray` kit in every Windows tray app (the theme applier, the menu
builder, `.ico` files, dialogs, the notification area icon and a single-instance lock), with
DL-FOV-Fixer as the reference move. On 2026-10-02 GoalMaker moved onto it.

`GoalMaker.App` references `DotNetLib.Tray` 0.2.0 in place of WPF UI and H.NotifyIcon, which the kit
brings along at the same versions. `windows/nuget.config` maps `DotNetLib.*` to the `dotnetlib`
source; a PC stores a `read:packages` token for it once, and CI reads the `DOTNETLIB_PACKAGES_TOKEN`
secret. Nobody without a token can build the Windows app, which the owner accepted for every
`dotnetlib` consumer.

What GoalMaker takes from the kit: `TrayResources` (WPF UI's dictionaries and the kit's Window
style, merged in front of GoalMaker's own), `TrayThemeApplier` (WPF UI's light, dark or high-contrast
theme, following Windows in System mode, and the `Tray.*` brushes in the theme's own colors through
`Theming/TrayPalettes`), `TrayMenuBuilder` for the tray menu, `IconFile` for the tray icon, and
`TrayMessageWindow` for the message when the app cannot start. `Theming/ThemeApplier` keeps
everything that is GoalMaker's: the four themes, pure black, the `GM.*` tokens, fonts, shapes,
density, motion, the logo, and the WPF UI accent keys.

What stays GoalMaker's own, because the kit cannot do it yet without dropping a documented behavior:

- `Shell/TrayIcon` wraps H.NotifyIcon itself. The kit's `TrayIconHost` runs one action on a left
  click and has no flyout and no double click, while GoalMaker's left click shows the Today flyout
  and a double click opens the window (spec, story 79).
- `Startup/SingleInstance` keeps its own pipe. The kit's lock only knocks, while a second GoalMaker
  launch hands over its switches and `goalmaker://` links (`--open`, `--mini`), which the running
  app then follows.

Both are candidates for the kit (a flyout and a double click on `TrayIconHost`, arguments on
`SingleInstance.Knock`); when it has them, these two copies go.
