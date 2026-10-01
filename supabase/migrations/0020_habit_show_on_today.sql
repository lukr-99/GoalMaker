-- 0020: a habit can stay off Today (docs/habits.md, the owner's board item "Option to set habits not
-- to show up").
--
-- Some habits are worth keeping but not worth a ring on Today every day. show_on_today false keeps a
-- habit off Today's ring row and the widgets that list Today's habits. Nothing else changes: it is
-- still checked in on the Habits page, keeps its streak and its map, and counts in stats, reviews,
-- goals and the Places hub. It is not a pause, which stops the cadence. Which habits Today shows is
-- pinned by the 'onToday' group of contracts/vectors/habits.json.
--
-- The column has a default, so every habit from before shows as it did, and an app from before 0020,
-- which leaves the column out of its rows, keeps whatever the owner set elsewhere.

alter table public.habits
  add column show_on_today boolean not null default true;

comment on column public.habits.show_on_today is
  'Whether the habit shows on Today and its widgets. false keeps it to the Habits page; it still counts everywhere else.';
