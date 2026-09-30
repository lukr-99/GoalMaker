-- State before 0019: an owner with a task, so the activity log already holds an entry.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-1900-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Before the chat');
