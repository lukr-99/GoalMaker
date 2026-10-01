# ADR 0015: Habit cards on Today and the Habits page

Today showed its habits as a row of rings between the timed tasks and the rest, and the Habits page
was a list of rings with heatmaps. A ring said little (what is a third of a ring of water?), a limit
like Snacks always read as "not done", and the phone's row scrolled sideways. A prototype
(`docs/design/prototypes/habits-goals-add-prototype.html` and its v2) compared rings on Today (A),
cards with pips and a check-in button (B), and a Tasks / Habits switch with morphing rows (C). On
2026-10-01 the owner chose **C's switch with B's cards** on the phone, B's cards in a **panel beside
the tasks** on the PC, and **B for the Habits page**: a summary card (today's count, a ring, the
longest streak) over **Every day, Weekly and Limits** groups of full cards, Hide done, and the
archived ones folded. A card shows the emoji, the name and streak, where today stands, pips or a bar,
the week's dots and a check-in button that fills and turns round when done; skipping lives in the
card's menu, which a long press also opens on the phone. What a card and Today count is a shared
rule (`contracts/vectors/habits.json`: groups, standings, dots, allDone), so a limit is never done
or left and a skip is neither. The phone keeps its switch while the app runs and starts on the
tasks; the PC puts the panel under the tasks where the window is narrow. The bottom bar's add
button from the same prototype is a separate change.
