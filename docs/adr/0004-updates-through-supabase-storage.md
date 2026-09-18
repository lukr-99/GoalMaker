# ADR 0004: Updates through a private Supabase Storage bucket with a signed manifest

The repository is private and the apps are sideloaded, so an installed app can't anonymously read
GitHub release assets (Tarot2Go never solved this). Both apps already hold a Supabase session, so the
release workflow uploads the APK, the installer and a release manifest to a private `releases`
bucket readable by signed-in users, and keeps a draft GitHub Release as the record. The manifest is
signed with an ECDSA P-256 key held only in GitHub secrets; the public key is built into the apps,
which verify the signature and each artifact's SHA-256 before installing. ECDSA P-256 is verifiable
with the platform crypto on Android API 26+ and .NET without extra libraries. Signing the exact
manifest bytes avoids JSON canonicalization. A manual download path always remains.
