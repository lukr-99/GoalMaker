# ADR 0018: Life goal pictures live in a private Storage bucket, outside the replica and the backup

Life goal pictures are the first files the owner adds, and they are too big for SQLite rows, the
outbox or the JSON backup. Each picture is a synced row in `life_goal_pictures` plus a JPEG in the
private Supabase Storage bucket `life-goal-pictures`, at `<owner id>/<picture id>.jpg`, readable and
writable only by its owner through Storage's row security. The apps shrink a picture to 1600 pixels
on its longest side before it goes up (2 MB limit on the bucket), keep a local cache of the files,
and upload a picture added offline once they can. The backup keeps the rows and not the files, so a
restore depends on the bucket still holding them. The cost: a lost bucket loses the pictures (the
owner keeps the originals in their gallery), and a deleted row's file has to be removed by the app,
so a crash in between can leave an orphan file in the owner's own folder.
