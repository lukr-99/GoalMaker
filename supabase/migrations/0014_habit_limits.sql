-- 0014: habits you want to keep down (docs/habits.md, contracts/vectors/habits.json).
--
-- Until now every habit counted up to something. A habit can now count the other way: 'at_most' makes
-- the target a limit, so two snacks a day is kept by staying at or under two, a day nobody logged is
-- kept because nothing was had, and going over breaks the day at once. A check habit under a limit has
-- no allowance at all, which is how "not once" is written. Only a daily or weekday habit can be a
-- limit: a week with "at most two on three days" says nothing anyone can act on.

alter table public.habits
  add column direction text not null default 'at_least'
    check (direction in ('at_least', 'at_most'));

alter table public.habits
  add constraint habits_only_days_have_a_limit
    check (direction = 'at_least' or cadence in ('daily', 'weekdays'));

comment on column public.habits.direction is
  'at_least: the target is something to reach. at_most: it is a limit, and going over breaks the day.';
