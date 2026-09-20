# Starting with Windows

GoalMaker's reminders on the PC need GoalMaker running, so it starts in the tray when the owner
signs in (spec, story 81). Settings, **Startup**, holds the switch, and the installer offers the same
thing while installing; both write the one value GoalMaker owns:

```
HKCU\Software\Microsoft\Windows\CurrentVersion\Run
  GoalMaker      "C:\...\GoalMaker.exe" --tray
```

A dev build writes `GoalMaker-dev` instead, so an installed release and a build from source never
fight over the value. Nothing else in that key is read or changed.

## Add to Startup Profiles

Startup Profiles launches a whole context (Work, Dev, School, Games, ...) at once. When it is installed, Settings shows **Add to Startup Profiles**
(spec, story 84). GoalMaker then follows that app's public integration contract: it builds a
registration link and hands it over.

```
startupprofiles://register?appId=com.goalmaker.app&name=GoalMaker&target=<GoalMaker.exe>&args=--tray&publisher=...&supportsMinimized=true
```

What happens next belongs to Startup Profiles: **its** window opens, shows who is asking, and lets
the owner pick the profiles or refuse. GoalMaker never writes to that app's files or keys, never
suggests a profile, and is never told which profiles were picked. GoalMaker finds it by the
`startupprofiles://` handler it registers for itself
(`HKCU\Software\Classes\startupprofiles\shell\open\command`), and failing that at
`%LOCALAPPDATA%\Programs\StartupProfiles\StartupProfiles.exe`. With Startup Profiles missing, the row
is not shown and everything else works as before: it is an extra, never a replacement for the switch
above.
