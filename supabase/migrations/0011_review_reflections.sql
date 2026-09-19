-- 0011: the answers written in a review (spec, stories 62 and 63; M4-05).
--
-- A review keeps its reflections as a JSON array of {"prompt": "<id from contracts/content/prompts.json>",
-- "answer": "<what the owner wrote>"}, in the order they were asked. It is one column rather than a
-- table because the answers are only ever read and written with their review, and the apps keep the
-- whole review in one row (docs/reviews.md). A check constraint can't look inside the array (no
-- subqueries), so it holds the array and its length, and a trigger checks every entry.

alter table public.reviews
  add column reflections jsonb not null default '[]'::jsonb;

alter table public.reviews
  add constraint reviews_reflections_are_an_array check (
    jsonb_typeof(reflections) = 'array' and jsonb_array_length(reflections) <= 20);

comment on column public.reviews.reflections is
  'The prompts asked and the answers written, as [{"prompt": "<prompt id>", "answer": "<text>"}].';

create or replace function public.check_review_reflections()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  reflection jsonb;
begin
  for reflection in select jsonb_array_elements(new.reflections) loop
    if jsonb_typeof(reflection) <> 'object'
      or jsonb_typeof(reflection -> 'prompt') is distinct from 'string'
      or jsonb_typeof(reflection -> 'answer') is distinct from 'string'
      or char_length(reflection ->> 'prompt') not between 1 and 60
      or char_length(reflection ->> 'answer') > 4000 then
      raise exception 'a reflection is {"prompt": "<id>", "answer": "<text>"}: %', reflection;
    end if;
  end loop;
  return new;
end;
$$;

comment on function public.check_review_reflections() is 'Keeps every reflection a prompt id with an answer.';

create trigger reviews_check_reflections before insert or update of reflections on public.reviews
  for each row execute function public.check_review_reflections();
