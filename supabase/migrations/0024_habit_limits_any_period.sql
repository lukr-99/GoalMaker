-- 0024: a limit for a week or a month, and a limit of none (docs/habits.md, contracts/vectors/habits.json).
--
-- 0014 let only a daily or weekday habit be a limit. A weekly or monthly habit can now be one too:
-- "takeaway at most twice a week" or "at most 5 drinks a month". Its number covers the whole period:
-- a check habit's 'times' is how many days it may happen in the period, and a count or amount habit's
-- 'target' is the most the period may add up to ('times' is then not read). A limit's number may now
-- be 0, which is "not once": a count of nothing a day, or no day at all in a week.

alter table public.habits drop constraint habits_only_days_have_a_limit;

alter table public.habits drop constraint habits_target_check;
alter table public.habits
  add constraint habits_target_fits_the_direction
    check (target is null or target > 0 or (direction = 'at_most' and target >= 0));

alter table public.habits drop constraint habits_times_fit_the_cadence;
alter table public.habits
  add constraint habits_times_fit_the_cadence check (
    (cadence in ('per_week', 'per_month')) = (times is not null)
    and (times is null or times >= 1 or (direction = 'at_most' and times >= 0))
    and (cadence <> 'per_week' or times <= 7)
    and (cadence <> 'per_month' or times <= 31));

comment on column public.habits.direction is
  'at_least: the target is something to reach. at_most: it is a limit for the day, or for the week or month of a weekly or monthly habit, and going over misses the period.';
comment on column public.habits.times is
  'For per_week (1 to 7) and per_month (1 to 31): how many days a period needs, or for a check habit under a limit how many it may have (from 0).';
