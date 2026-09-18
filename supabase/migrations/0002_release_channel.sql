-- 0002: the update channel (ADR 0004).
--
-- A private bucket holding release artifacts and the signed release manifest. Signed-in users may
-- read it; only the release workflow writes to it, using the secret key, which bypasses row
-- security. The 50 MB limit matches the free plan's per-file cap.

insert into storage.buckets (id, name, public, file_size_limit)
values ('releases', 'releases', false, 52428800)
on conflict (id) do nothing;

create policy "releases: signed-in users read"
  on storage.objects for select
  to authenticated
  using (bucket_id = 'releases');
