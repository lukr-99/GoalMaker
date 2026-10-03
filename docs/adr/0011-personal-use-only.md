# ADR 0011: GoalMaker stays a personal, single-owner app

Every table carries `owner_id` with row security that allows only `auth.uid() = owner_id`, the
connector acts as exactly one owner, and signups on the cloud project are closed. Anything shared or
multi-user would need cross-owner row security, invites, open signups and a connector that speaks
for more than one person, which is a different product. On 2026-09-28 the owner decided GoalMaker is
developed for personal use only. New features keep the single-owner model, and a feature that needs
another person is out of scope until this ADR is superseded.
