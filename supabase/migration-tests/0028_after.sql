-- After 0028: what was there is untouched, a PC sign-in can be started, approved with its match
-- number and picked up once, and the purge is scheduled.
do $$
declare
  made record;
  caller text := repeat('c', 64);
begin
  if (select count(*) from public.tasks where id = 'aaaaaaaa-2800-0000-0000-000000000001') <> 1 then
    raise exception 'the task from before is still there';
  end if;
  if exists (select 1 from public.pc_sign_ins) then
    raise exception 'there are no PC sign-ins until a PC starts one';
  end if;
  if not exists (select 1 from cron.job where jobname = 'goalmaker-purge-pc-sign-ins') then
    raise exception 'old PC sign-ins are cleared every hour';
  end if;

  select * into made from public.pc_sign_in_start('DESKTOP-1', caller);
  perform set_config('request.jwt.claims',
    '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
  if public.pc_sign_in_answer(made.scan_secret, true, made.match_number) <> 'approved' then
    raise exception 'the owner could not approve the PC with its number';
  end if;
  if (select email from public.pc_sign_in_pick_up(made.id, made.pickup_secret)) <> 'owner@example.test' then
    raise exception 'the PC picks up the approving owner''s address';
  end if;
  if not public.pc_sign_in_finish(made.id, made.pickup_secret, true) then
    raise exception 'a claimed approval is used up once its code is made';
  end if;
  if (select state from public.pc_sign_in_pick_up(made.id, made.pickup_secret)) <> 'expired' then
    raise exception 'an approval is picked up once';
  end if;
end;
$$;
