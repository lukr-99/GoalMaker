# ADR 0012: The Letter is written by a Claude routine; GoalMaker only stores and shows it

The owner wants a weekly letter that reads across GoalMaker and the owner's other apps (a journal, deadlines,
learning, Tally). Building that inside GoalMaker would mean a paid model API and pulling other apps'
data through GoalMaker's backend. Instead a scheduled Claude routine on the owner's plan does the
gathering and the writing: it reads GoalMaker through one call, `get_review_digest`, reads the other
apps through their own connectors, and saves the letter with `save_review_summary`. GoalMaker stores
it as the review's existing `summary` (no schema change) and shows it as the first step of the guided
review. No other app's data ever passes through GoalMaker's backend, GoalMaker sends no email (the
routine may, through Claude's own mail connector), and the canonical routine prompt lives in
`docs/letter.md` so it is versioned with the tools it calls. The cost is that the letter only exists
when the routine runs; the review works as before without it.
