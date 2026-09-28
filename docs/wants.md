# Wants

> Planned in M8 (`.scratch/m8-letter-tally-wants/`, M8-03 to M8-06). Not built yet.

A **want** is something the owner would like to buy, written down with the reason, which waits out a
**cooldown** before it is decided (spec, stories 104 to 108). It stops impulse buys and leaves a
history of what was really wanted.

## A want

| Field | Notes |
|---|---|
| title | Required |
| reason | Required: why it is wanted is the point |
| link, area | Optional |
| price, currency | Optional; the currency defaults to the owner's (CZK) |
| cooldown days, cools until | Set when it is added, from the price unless picked |
| decision | `bought` or `dropped`, with decided at and an optional note |
| last checked price | What Claude found, when, and where (with alternatives as a note) |
| made by | Owner or Claude, like tasks |

`wants` is a synced table (Supabase migration 0016, replica migration 0011) with the usual row
security, tombstones, activity log and undo, and it is part of the backup.

## Cooldowns

The thresholds live on the profile (`want_cooldowns`), so both apps and the connector work out the
same cooldown, and the owner changes them from the Wants place:

| Price (in the owner's currency) | Cooldown |
|---|---|
| under 1,000 | 7 days |
| under 10,000 | 30 days |
| 10,000 or more | 90 days |
| no price, or another currency | 30 days |

A want can have its own number of days. Changing the thresholds never moves wants already cooling.

## States

- **Cooling:** before `cools_until`.
- **Ready:** on or after `cools_until` and not decided. "Still want it" is not stored: the want stays
  ready.
- **Decided:** bought or dropped. Reopening clears the decision.

The states, the cooldown and its edges (the day rollover, a price of exactly 1,000) are pinned by
`contracts/vectors/wants.json`.

## The ready notification

One notification a day, at a time the owner picks (10:00 by default), listing the wants that became
ready since the last one. It is worked out from the wants themselves, like the review reminders, so
there are no reminder rows; quiet hours hold it back, and deciding a want takes it out of the
notification on both devices after the next sync (the `ready` group of `wants.json`).

## In the apps

A Wants place with filter chips in the main area (Cooling, Ready, Decided), each row with an accent
ring counting the cooldown down and the price, the reason one tap away. The cooldown thresholds sit
at the top of the place. `/want` in the composer opens the add sheet with the reason required
(`composer.json`). Stats gains a Wants block: bought against dropped, and the money not spent (the
total of dropped prices in the owner's currency).

## Through the connector

`get_wants`, `add_want`, `update_want`, `decide_want` (bought or dropped with a note) and
`record_price_check` (a price, where it was found, alternatives). Claude looks prices up with its own
web search; GoalMaker never fetches from a shop. `get_review_digest` carries the wants that became
ready or were decided in the period, so the [Letter](letter.md) can mention them.
