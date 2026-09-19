# M3-07: Connector link in both apps' settings

**Status:** done 2026-09-19 · **Milestone:** M3

## Scope
- Settings, Claude connector, on Android and Windows: create the link (shown once, with Copy and
  how to add it on claude.ai), rotate it, revoke it, and when it was last used (spec, stories 69
  and 77).
- The link is the backend URL plus `/functions/v1/connector/<secret>`; the secret never goes to the
  replica, the settings file or a log.
- docs/connector.md: adding GoalMaker to Claude, what the connector can do, and the link's risk.

## Acceptance criteria
- Creating on one app and revoking on the other kills the link (the next call gets 404).
- View-model tests on both apps with a fake gateway; the page renders offscreen on Windows.

## Result
- Android: Settings, Claude, Claude connector (`ui/connector/`); Windows: a Claude connector card in
  Settings (`ConnectorViewModel`). Create, Make a new link and Revoke (both confirmed first), when
  the link was made and last used, and a new link shown once with Copy and where to paste it. The
  URL stays in the screen's state only, and goes when the link is revoked or replaced elsewhere.
- `ConnectorLinks` / `IConnectorLinks` over PostgREST (`connector_links`, `create_connector_link`,
  `revoke_connector_links`), through one `PostgrestHttp` per app that sync uses too.
- Checked on the local stack: a link made on the PC worked for Claude's calls; revoking it on the
  phone made the next call 404 and the PC's card said "No link yet."
