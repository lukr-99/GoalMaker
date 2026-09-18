-- After 0005: the daily job exists and a purge removes only tombstones older than 90 days.
do $$
begin
  if not exists (select 1 from cron.job where jobname = 'goalmaker-purge-tombstones') then
    raise exception 'purge job not scheduled';
  end if;
  perform public.purge_tombstones();
  if exists (select 1 from public.tasks where title = 'Old') then
    raise exception 'old tombstone survived';
  end if;
  if (select count(*) from public.tasks where title in ('Recent', 'Alive')) <> 2 then
    raise exception 'purge removed too much';
  end if;
end;
$$;
