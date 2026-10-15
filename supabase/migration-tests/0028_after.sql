-- After 0028: what was there is untouched, a PC sign-in can be started, approved and picked up once.
do $$
declare
  made record;
begin
  if (select count(*) from public.tasks where id = 'aaaaaaaa-2800-0000-0000-000000000001') <> 1 then
    raise exception 'the task from before is still there';
  end if;
  if exists (select 1 from public.pc_sign_ins) then
    raise exception 'there are no PC sign-ins until a PC starts one';
  end if;

  select * into made from public.pc_sign_in_start('DESKTOP-1');
  perform set_config('request.jwt.claims',
    '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
  if not public.pc_sign_in_answer(made.scan_secret, true) then
    raise exception 'the owner could not approve the PC';
  end if;
  if (select email from public.pc_sign_in_pick_up(made.id, made.pickup_secret)) <> 'owner@example.test' then
    raise exception 'the PC picks up the approving owner''s address';
  end if;
  if (select state from public.pc_sign_in_pick_up(made.id, made.pickup_secret)) <> 'expired' then
    raise exception 'an approval is picked up once';
  end if;
end;
$$;
