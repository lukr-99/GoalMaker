# Design questionnaire

The spec settled the direction: **bold and energetic**, a **chat-app layout** (sidebar or bottom
navigation, a main pane, a composer bar), Material 3 Expressive on Android and WPF UI (Fluent) on
Windows. This questionnaire pins down the look before M2, the first UI-heavy milestone.

Answered 2026-09-18 through a design board (four directions drawn as the same Today screen) and a
short form. `ok` means the recommendation stands.

Write under **Answer:**, and `ok` accepts the recommendation.
"You decide" is fine for anything you don't care about. Screenshots or app names as references help
most.

What M0 shows today (placeholders): a violet primary (#5B2EE6), a coral accent (#E5533A), system
fonts, a target-with-check icon.

---

## Personality

## D1. Three words

Which three words should someone use after five seconds with GoalMaker? For example: punchy,
playful, focused, warm, sporty, premium, calm-but-confident.

**Recommended:** punchy, focused, rewarding.

**Answer:**
ok: punchy, focused, rewarding. The board's style is the one to keep.

## D2. References

Name apps (or websites, games, posters) whose look you like, and what exactly you like in each. For
example: Things 3 (clarity), Duolingo (celebration), Linear (speed, dark mode), Arc browser (color),
Nike Run Club (bold type), Claude or ChatGPT (the composer).

**Recommended:** Linear for density and dark mode, Nike Run Club for bold numbers and type,
Duolingo for celebration, the Claude app for the composer.

**Answer:**
ok, as recommended; the four directions on the design board are the working references.

---

## Color

## D3. The main color

Keep the violet, or pick another main hue? Options: violet (current), electric blue, emerald,
tangerine, magenta, or name your own.

**Recommended:** keep violet as the primary and coral as the second accent. Violet reads as
ambitious and isn't taken by most productivity apps.

**Answer:**
The four board directions become **themes the user can switch** in Settings: **Track**
(black, white, volt #D6FF3A) is the **default**; Electric (violet #5B2EE6 and coral), Night (indigo
and cyan) and Sunrise (tangerine and magenta) are the alternatives.

## D4. Area colors

Areas (Health, School, Work, Personal, ...) each get a color. Should area colors be:

- **(a)** a fixed set of 10 to 12 bold colors designed to work together in light and dark, or
- **(b)** any color you pick?

**Recommended:** (a), with a custom picker only as an advanced option later.

**Answer:**
(a) plus a custom picker: a fixed palette of 12 that works in every theme, light and dark, and a
custom color as the escape hatch.

## D5. Dark mode

Pure black (OLED, highest contrast) or a deep neutral gray with a hint of the main color?

**Recommended:** deep gray with a slight violet tint on both apps; pure black as an optional
setting on the phone later.

**Answer:**
Tinted deep gray by default, pure black as an option (both apps).

---

## Type and shape

## D6. Fonts

- **(a)** A geometric sans with personality (for example Manrope, Plus Jakarta Sans, Outfit)
- **(b)** A grotesk with bold numbers (for example Space Grotesk, Inter Tight)
- **(c)** Rounded and friendly (for example Nunito, Quicksand)
- **(d)** The platform fonts (Roboto Flex on Android, Segoe UI Variable on Windows)

**Recommended:** (a) Plus Jakarta Sans for text and headings on both apps, with tabular numbers for
stats and times. It's free, bold-capable and readable small.

**Answer:**
Per theme, as on the board: Track uses Archivo (wide, black italic headings), Electric Plus
Jakarta Sans, Night Space Grotesk, Sunrise Outfit. Tabular numbers for stats and times everywhere.

## D7. Big numbers

Should progress and stats use huge, poster-like numbers (for example "3/5" in 64 pt on a goal card),
or stay modest?

**Recommended:** huge numbers on goal, habit and stats cards; modest everywhere else.

**Answer:**
ok: huge numbers on goal, habit and stats cards; modest elsewhere.

## D8. Roundness and density

- Corners: very round (pills and 28 dp cards), medium, or sharp?
- Density: airy (fewer items on screen, big touch targets) or compact (more tasks at once)?

**Recommended:** very round on the phone (Material Expressive shapes), medium on Windows; phone
airy, Windows compact with an option.

**Answer:**
Corners come with the theme (Track sharp, Night medium, Electric and Sunrise very round).
Density: phone airy, Windows compact.

---

## Motion and feedback

## D9. Completing things

What happens when you tick off a task, finish a habit or hit a goal?

- Task: a quick check morph and the row sliding away, a light haptic
- Habit: the ring fills with a spring and a small burst
- Goal hit or streak milestone: confetti (Konfetti on Android, a lighter burst on Windows)

Too much, too little, or right?

**Recommended:** as listed, with a "reduce motion" setting that respects the system setting.

**Answer:**
ok, at 3 of 5: check morph and slide away for tasks, a ring fill with a small burst for habits,
confetti only for goals and streak milestones. "Reduce motion" follows the system setting.

## D10. Sound

Any sounds (a soft tick on completion, a chime for rituals), or silent apart from notifications?

**Recommended:** silent by default; an optional completion tick later.

**Answer:**
Silent by default, with an optional completion tick.

---

## Layout

## D11. The composer

The chat-style bar at the bottom. Should it look like:

- **(a)** A floating pill with a send button (Claude or ChatGPT style)
- **(b)** A full-width bar docked to the bottom
- **(c)** A floating pill that expands into a sheet with the parsed preview (date, tags, area chips)
  as you type

**Recommended:** (c) on both apps.

**Answer:**
(c): a floating pill that grows a parsed preview (date, time, area and tag chips) as you type.

## D12. What Today shows first

Order the Today screen from top to bottom (reorder or cut): greeting and date, top priorities, the
day's timeline (timed tasks and reminders), other tasks, habits for today, goals for this week,
overdue items.

**Recommended:** date and a one-line summary, top priorities, timed items, habits (a compact row of
rings), other tasks, overdue (collapsed), this week's goals (collapsed).

**Answer:**
ok, as recommended.

## D13. The Windows sidebar

What goes in the sidebar, in which order? Proposal: Today, Tomorrow, Inbox, Habits, Goals, Projects,
Calendar, Reviews, Stats, then areas and tags as filters, Settings at the bottom. Should it collapse
to icons?

**Recommended:** as proposed, collapsible to icons, remembering its state.

**Answer:**
ok, as recommended: collapsible to icons, remembering its state.

---

## Identity

## D14. The icon and name

Keep the target-with-check mark, or explore something else (a rising arrow, a flag, a flame for
streaks, a monogram "G")? Should "GoalMaker" appear as a wordmark in the apps?

**Recommended:** keep the target-with-check as the base and refine it (thicker stroke, the check
breaking out of the ring), a small wordmark on the sign-in screen only.

**Answer:**
A **monogram G**: a stylish G whose middle bar turns into an uptrend arrow. The wordmark appears
on the sign-in screen only.

## D15. Emoji

Can areas, goals and habits carry an emoji (🏃 Run, 📚 Study)?

**Recommended:** yes, optional, shown next to the name and on widgets.

**Answer:**
Yes, optional.

---

## Accessibility

## D16. Text size and contrast

Follow the system text size everywhere (layouts reflow), and keep text contrast at WCAG AA or
better, including on colored cards?

**Recommended:** yes to both, and test with the largest system font on both apps.

**Answer:**
ok: follow the system text size, WCAG AA contrast including colored cards, test at the largest
system font.

---

## Anything else

Colors, moods, pet peeves, things you never want to see.

**Answer:**
Themes are a user setting. Nothing ruled out yet.
