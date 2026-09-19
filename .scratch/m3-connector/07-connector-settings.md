# M3-07: Connector link in both apps' settings

**Status:** todo · **Milestone:** M3

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
