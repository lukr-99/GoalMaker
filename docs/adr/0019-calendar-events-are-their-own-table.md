# ADR 0019: Calendar events are their own table, not tasks with a span

The owner wants trips, holidays and the like on the calendar: things that take up days rather than
get done. Giving tasks a last day would have reused the task lists, but every place that reads tasks
(Today's count, done and dropped, repeats, stats, the archive, plan tomorrow, the review digest) would
then have to learn that some tasks cover several days and are never ticked off. A separate synced
table, `events` (title, first and last day, notes, area), keeps tasks as they are and lets the
calendar draw events as bars and Today show the ones going on. The cost is one more table to sync,
back up and expose in the connector, and that an event can't be moved to Done or carry a reminder
until it gets those of its own.
