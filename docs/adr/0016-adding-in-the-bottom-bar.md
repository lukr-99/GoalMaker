# ADR 0016: Adding in the bottom bar

Today, Tomorrow and the Inbox added tasks through the composer at the bottom, while Wants, Habits and
Goals each had their own add button: a plus in the phone's top bar, a primary button in the PC's page
header, and "Add a goal" under each period. The owner disliked the top bar's plus. The add prototype
(`docs/design/prototypes/habits-goals-add-v2.html`) put the composer, with its quick chat switch, at
the bottom of all of these pages, with one round button at its end. On 2026-10-01 the owner chose it:
"plus turns into send when we type, otherwise the button will open a window to add manually".

So the bottom bar is the one way to add on every list (docs/composer.md). Empty, its button is a plus
that opens the page's full form (the new task form, the want form, the habit or goal editor); typed,
it is the send arrow that adds what the line says, with a live preview. A line that can't be added as
it stands (a want with no reason, a habit or goal with no name) opens the form filled in instead. On
the PC Enter adds and Ctrl+N opens the form. On Goals the "Add a goal" rows and the List view's
per-group button stay, since they add to one period directly. The quick-add box and Plan tomorrow keep
their send-only bar.

Wants, habits and goals need their own small line rules (a price and a reason; a cadence and an
amount; a period and a target), which are not the task grammar. They live in the application layer of
both apps and in the connector's rules, pinned by `contracts/vectors/quick-add.json`, and they stay
deliberately small: the first phrase of each kind counts, and anything not understood stays in the
title. A limit, an emoji, a parent goal or a link are set in the form.
