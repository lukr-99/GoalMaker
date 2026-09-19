-- State before 0007: a user with a task that was renamed, so the activity log has two entries from
-- before undo existed.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Run');
update public.tasks set title = 'Run 5 km' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
