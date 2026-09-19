# Lists

Which open tasks each list shows, and in what order. Pure rules, pinned by
[`contracts/vectors/lists.json`](../contracts/vectors/lists.json) and run by both apps.

**The planning day** is the local date shifted back by the day's start hour (04:00 by default, a
setting from 00:00 to 06:00): at 01:30 it is still yesterday. Every list below uses it.

Only open tasks appear; done, dropped and deleted ones leave every list.

| List | Holds | Order |
|---|---|---|
| Today: top priorities | top priority, planned today | time (untimed last), then creation |
| Today: scheduled | planned today with a time, not top priority | time, then creation |
| Today: more | planned today without a time, not top priority | creation |
| Today: overdue | planned before today | day, time (untimed last), creation |
| Tomorrow | planned for tomorrow | top priority first, then time (untimed last), creation |
| Inbox | no planned day and no area | creation |

Tasks planned after tomorrow, and undated tasks with an area, wait in their area (M2-11) and the
calendar (M5). Ties always break by id, so both apps agree.

**The day's summary** ("2 of 5 done") counts tasks planned for today that are open or done; dropped
and deleted ones don't count.

## Filtering

Any list can be narrowed to one area, one tag, or both (spec, story 9). The filter picks the tasks
first and the rules above run on what is left, so the sections, the order and the day's summary all
describe the same slice. A task matches an area when it belongs to it, and a tag when the tag is
linked to it; with both set it has to match both. The Inbox holds tasks without an area, so it is
empty under an area filter. The filter belongs to the screen, not to one list, so it stays when the
owner switches between Today, Tomorrow and the Inbox. Pinned by the `filter` cases in
[`contracts/vectors/lists.json`](../contracts/vectors/lists.json).

Deleting an area keeps its tasks and takes the area off them, so an undated one returns to the
Inbox. Deleting a tag takes it off every task.

**Archiving** an area keeps everything: its tasks keep the area and show its chip, and it keeps its
color and name. It only leaves the pickers and the filters, and a filter on it falls away. Naming it
again (`@Garden` in the composer) brings it back, and so does Restore in the areas manager, where
archived areas are listed apart. The synced `areas.archived_at` says when it was archived.
