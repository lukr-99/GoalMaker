-- After 0002: the bucket exists and existing profiles are untouched.
do $$
begin
  if not exists (select 1 from storage.buckets where id = 'releases' and public = false) then
    raise exception 'releases bucket missing or public';
  end if;
  if not exists (
    select 1 from public.profiles
    where id = '11111111-1111-1111-1111-111111111111'
      and display_name = 'Owner' and day_rollover_hour = 5
  ) then
    raise exception 'existing profile changed by 0002';
  end if;
end;
$$;
