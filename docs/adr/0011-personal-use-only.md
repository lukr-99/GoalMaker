# ADR 0011: GoalMaker stays a personal, single-owner app

Every table carries `owner_id` with row security that allows only `auth.uid() = owner_id`, the
connector acts as exactly one owner, and signups on the cloud project are closed. An idea for shared
habit challenges with friends (groups, a shared feed, reactions, Health Connect steps) would have
needed cross-owner row security, invites, open signups and a connector that speaks for more than one
person, which is a different product. On 2026-09-28 the owner decided GoalMaker is developed for
personal use only: anything shared or multi-user is postponed or removed, and the challenges idea is
off the roadmap. New features keep the single-owner model, and a feature that needs another person is
out of scope until this ADR is superseded.
