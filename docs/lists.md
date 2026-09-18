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
