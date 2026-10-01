# Wants

> M8: the data and rules (M8-03), the Wants place (M8-04), the ready notification (M8-05) and the
> connector tools (M8-06) are built.

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
security, tombstones, activity log and undo, and it is part of the backup. A want keeps its
`cooldown_days`, `added_on` and `cools_until` as they were set, and the server checks that it cools
exactly its days after it was added.

## Cooldowns

The thresholds are one synced row per owner (`want_cooldowns`, its id a UUID version 5 of
`want-cooldowns/<owner>` so two devices make the same row), so both apps and the connector work out
the same cooldown, and the owner changes them from the Wants place. Without the row the defaults
apply:

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

One notification a day, at a time the owner picks (10:00 by default, Settings, Planning, "Wants
ready"; off switches it off), naming the wants that became ready since the last one: a day the device
was off is caught up in the next one. It is worked out from the wants themselves, like the review
reminders, so there are no reminder rows, and it shares the device's one alarm. Like the review
reminders, quiet hours don't move it: the owner picked the time. Tapping it opens the Wants place.
It goes once every want it named is decided or deleted, on this device at once and on the other
after its next sync (the `ready` and `notify` groups of `wants.json`). On Android it has its own
notification channel, so it can be silenced apart from task reminders.

## In the apps

A Wants place with filter chips in the main area (Cooling, Ready, Decided), each row with an accent
ring counting the cooldown down and the price, the reason one tap away. The cooldown thresholds sit
at the top of the place. Wants are added from the bottom bar ([composer](composer.md#the-bottom-bar-on-every-list)):
type `Kindle 3290 Kč wait 2 weeks because I read on the train` and send, with the price, the wait and
the reason previewed as chips; with nothing typed its plus opens the add sheet (on Windows the add
panel at the top of the page). A line without `because` opens the sheet filled in, waiting for the
reason, which is required. `/want` in Today's composer opens the add sheet with the title filled in
(`composer.json`). The activity log says
when a want was added, bought, dropped, reopened or renamed (`activity.json`). Stats gains a Wants block: bought against dropped, and the money not spent (the
total of dropped prices in the owner's currency).

## Through the connector

`get_wants` (by state), `add_want` (the cooldown from the owner's thresholds unless picked),
`update_want`, `decide_want` (bought or dropped with a note, or reopened) and `record_price_check` (a
price in the want's currency, where it was found, and alternatives, kept as the checked note).
Claude looks prices up with its own web search; GoalMaker never fetches from a shop. The tools tell
Claude to ask the owner before it decides anything. A want Claude adds is made by Claude, and both
apps say "by Claude" after its price and state ([connector](connector.md)). `get_review_digest` carries the wants that became
ready or were decided in the period, so the [Letter](letter.md) can mention them.
