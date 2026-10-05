-- 0025: needs beside wants (docs/wants.md, contracts/vectors/wants.json).
--
-- A need is something the owner has to buy rather than would like to: it sits in the Wants place
-- under its own tab, skips the cooldown (it is ready the day it is added), may have a day it is
-- needed by, and doesn't have to say why. It is bought or dropped like a want, and it stays out of
-- the wants' ready notification and the bought against dropped stats.

alter table public.wants
  add column kind text not null default 'want' check (kind in ('want', 'need')),
  add column need_by date;

alter table public.wants
  add constraint wants_only_needs_have_a_day check (kind = 'need' or need_by is null);
alter table public.wants
  add constraint wants_needs_skip_the_cooldown check (kind = 'want' or cooldown_days = 0);

-- A want says why it is wanted; a need may leave it empty.
alter table public.wants drop constraint wants_reason_check;
alter table public.wants
  add constraint wants_reason_fits_the_kind
    check (char_length(reason) <= 2000 and (kind = 'need' or char_length(reason) >= 1));

comment on column public.wants.kind is 'want: something to wait out a cooldown for. need: something to buy, no cooldown.';
comment on column public.wants.need_by is 'For a need: the day it is needed by; optional.';
