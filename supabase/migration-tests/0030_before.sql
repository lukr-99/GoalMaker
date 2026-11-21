-- State before 0030: an owner with an area, a project and a tag. Tags belong to nothing yet.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.areas (id, owner_id, name, color)
values ('aaaaaaaa-3000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'School', 'violet');
insert into public.projects (id, owner_id, name)
values ('bbbbbbbb-3000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Thesis');
insert into public.tags (id, owner_id, name)
values ('cccccccc-3000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'forms');
