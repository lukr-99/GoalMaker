# ADR 0009: Windows reminders as Windows SDK toasts from the tray app, without the Windows App SDK

The spec left Windows App SDK notifications "to be confirmed in M2 against the framework-dependent
installer". Unpackaged, the Windows App SDK needs either its runtime installed separately (a large
extra install the update channel can't carry) or a self-contained copy that grows every release, so
GoalMaker shows reminders as plain toasts through the Windows SDK projection instead: the app
targets `net10.0-windows10.0.19041.0`, registers its AppUserModelID (`GoalMaker`, `GoalMaker.Dev`)
with a name and icon under HKCU at every start, and shows toasts in the reminder style (important
ones in the alarm style, ringing until handled) with Done and the three snoozes as buttons. A
prototype confirmed Windows 11 shows them from an unpackaged process with nothing else registered.
The buttons reach the app through in-process events, which work because the tray app that keeps the
reminder timer is the same process; when GoalMaker quits it takes its toasts down, since nothing
would hear their buttons, and the reminders wait in the replica (the phone still has them). The cost
is the projection assembly, which took the dev installer from about 5 MB to 9.4 MB, well under the
channel's 50 MB limit. If buttons ever have to work while the app is closed, the next step is a COM
activator registered next to the AppUserModelID, not the Windows App SDK.
