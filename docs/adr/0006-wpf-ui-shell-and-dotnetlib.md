# ADR 0006: WPF UI for the Windows shell; dotnetlib evaluated, not referenced yet

CodePrint requires checking `dotnetlib` before adding WPF primitives. Its packaged surface
(`DotNetLib.Core`: an observable base and a relay command) is covered by CommunityToolkit.Mvvm, and
its unpackaged controls (stat card, badge, toggle switch) overlap WPF UI's. It also ships only
through a local folder feed, which GoalMaker's CI can't restore. GoalMaker therefore uses WPF UI 4
for the Fluent shell (navigation view, Mica, dialogs, snackbars) and CommunityToolkit.Mvvm, and keeps
its own semantic tokens in one resource dictionary. When dotnetlib publishes a restorable feed, the
tokens and any genuinely missing controls should move there instead of growing here.
