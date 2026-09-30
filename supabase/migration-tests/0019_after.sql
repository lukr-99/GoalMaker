-- After 0019: the old entry came through no chat, a change the chat sends is noted, and the chat's
-- limits count.
do $$
begin
  if (select via from public.activity_log where entity_id = 'aaaaaaaa-1900-0000-0000-000000000001') is not null then
    raise exception 'an entry from before 0019 did not come through the chat';
  end if;

  perform set_config('request.headers', '{"x-goalmaker-actor": "owner", "x-goalmaker-via": "chat"}', true);
  update public.tasks set title = 'Renamed in the chat' where id = 'aaaaaaaa-1900-0000-0000-000000000001';
  if (select via || '/' || actor from public.activity_log
      where entity_id = 'aaaaaaaa-1900-0000-0000-000000000001' and action = 'update') <> 'chat/owner' then
    raise exception 'a change through the chat is the owner''s, noted as the chat';
  end if;

  if public.assistant_count('11111111-1111-1111-1111-111111111111', 1, 10) <> 'ok'
     or public.assistant_count('11111111-1111-1111-1111-111111111111', 1, 10) <> 'minute' then
    raise exception 'the chat''s per-minute limit counts';
  end if;
end;
$$;
