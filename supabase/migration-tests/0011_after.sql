-- After 0011: the old review has an empty array, takes reflections, and refuses a bad entry.
do $$
begin
  if (select reflections from public.reviews where id = 'aaaaaaaa-0000-0000-0000-000000000001') <> '[]'::jsonb then
    raise exception 'a review from before 0011 starts with no reflections';
  end if;

  update public.reviews
  set reflections = '[{"prompt": "wins/proud", "answer": "Shipped the habit screen."}]'::jsonb
  where id = 'aaaaaaaa-0000-0000-0000-000000000001';

  begin
    update public.reviews set reflections = '[{"prompt": "wins/proud", "answer": 3}]'::jsonb
    where id = 'aaaaaaaa-0000-0000-0000-000000000001';
    raise exception 'an answer that is not text must be refused';
  exception
    when raise_exception then null;
  end;
end;
$$;
