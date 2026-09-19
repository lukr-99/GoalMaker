# Activity and undo

Every change to a synced row is written to the server's activity log by a trigger, with who made it
(you, Claude through the [connector](connector.md), or GoalMaker itself), the row before and after,
and when (spec, stories 19 and 72). The log isn't part of the replica, so the apps read it online,
the latest 60 changes, newest first. Entries older than 90 days are purged with the tombstones.

## What a change says

The Activity screen (Android: Settings, Activity; Windows: Activity in the sidebar) says each change
in plain words, like "Claude moved “Call the bank” to Mon 21 Sep". What counts as a completion, a
move, a rename or an archived area is a shared rule, pinned by
[`contracts/vectors/activity.json`](../contracts/vectors/activity.json) and run by both apps:

- the server's action decides added, deleted and restored;
- an update of a task is completed, dropped or reopened when its status changed, else moved when its
  day changed, else renamed when its title changed;
- an area is archived or brought back when `archived_at` changed, a step checked or unchecked, a
  reminder handled or snoozed;
- anything else is "changed".

## Undo

Undo puts the row back the way the entry found it, as the owner (`undo_activity`, migrations 0007
and 0008): an edit gets its old values, a deletion is restored, and something added is deleted
softly. The restored row reaches both replicas through normal sync, and the undo is itself a change
in the log, so undoing it again takes a mistaken undo back.

Undo is offered on each row's latest change only. The server refuses to undo a change the row has
moved on from, because that would throw the later work away; the screen then says so. It also says
when the change was already undone, perhaps on the other device.
