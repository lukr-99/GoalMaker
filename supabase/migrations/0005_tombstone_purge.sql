-- 0005: purge old tombstones and activity (docs/sync.md).
--
-- Devices soft-delete; tombstones stay 90 days so every device hears about the deletion. Devices
-- whose watermark is older than 80 days resync from scratch, so nothing they hold can outlive a purge
-- unseen. The activity log keeps the same 90 days.

create extension if not exists pg_cron;

create function public.purge_tombstones(keep interval default interval '90 days')
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  cutoff timestamptz := now() - keep;
  removed integer := 0;
  step integer;
begin
  -- Children first; the cascades would catch them anyway, but this keeps the count honest.
  delete from public.task_tags where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.task_steps where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.reminders where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.tasks where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.tags where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.areas where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.activity_log where created_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  return removed;
end;
$$;

comment on function public.purge_tombstones(interval) is
  'Hard-deletes tombstones and activity older than keep (90 days). Run daily by pg_cron.';

revoke execute on function public.purge_tombstones(interval) from public, anon, authenticated;

select cron.schedule('goalmaker-purge-tombstones', '17 3 * * *', 'select public.purge_tombstones()');
