# Focus Board

The Focus Board is the owner's short list of tasks to keep an eye on, whatever day or list they sit
in: a school deadline, a form to hand in, an errand that keeps slipping. It is a place of its own in
both apps. The rules below are pinned by [`contracts/vectors/focus.json`](../contracts/vectors/focus.json)
and run by both apps and the connector: the board's order, adding at the end, putting a task at a
place, and what the add panel offers.

## On the board

A task is on the board when it has a **focus position**
([`0029_focus_board.sql`](../supabase/migrations/0029_focus_board.sql)); taking it off clears the
position and leaves the task itself alone: its day, area and list stay as they were.

- The board shows open tasks only. A done or dropped task leaves it quietly, and a deleted one too.
- The order is the owner's: by position, then oldest first, then id.
- A task added to the board goes to the end: one more than the last one's position, or 1 on an empty
  board.
- Moving a task to a place puts it halfway between its new neighbours, one below the first at the top
  and one past the last at the end. Positions are fractions, so nothing else is renumbered.

## Finding and adding tasks

- **Quick add.** The field on the board makes a new task straight onto it, at the end. It reads the
  composer's shortcuts ([composer](composer.md)), so "Hand in the form @School #forms friday" sets the
  area, a tag and the day. It sits at the top of the board in both apps.
- **The add panel** (the add button) finds a task that already exists. Its search box keeps the open
  tasks off the board that have every typed word in their title or notes, ignoring case and accents,
  so "studijni zrusit" finds "Studijní: zrušit dipl. práci". They come by their date (the deadline,
  else the planned day), earliest first and undated last, then by title. With something typed, the
  first row makes it a new task on the board instead.
- **The filter.** An area and tag filter of the board's own, the same one the lists use
  ([lists](lists.md)), narrows both the board and the add panel. A task without an area counts as
  being in its project's area. While it hides part of the board, a task moved up or down lands next
  to its shown neighbours, so the hidden tasks keep their places. A filter that hides every task on the
  board says so.

## In the apps

- **Android:** long-press the handle and drag to reorder, swipe a task off the board, tick it to
  complete it, and the add button for the add panel.
- **Windows:** each row shows its place, a box to complete it, Up and Down to reorder (Alt+Up and
  Alt+Down on the row you are on) and the pin to take it off. Where the window is wide, the add panel
  sits beside the board; where it is narrow, "Add existing task" opens it in the board's place and
  "Back to the board" (or Esc) closes it. Ctrl+N goes to the quick add field, Ctrl+F to the search box,
  and Enter there makes the typed task. A late date is in the danger color with a clock.

## Through Claude

The connector reads and changes the board ([connector](connector.md)):

- `get_focus_board` lists the board top first, numbered.
- `add_task` and `update_task` take `focus`: true puts the task at the end (one already on the board
  keeps its place), false takes it off.
- `place_on_focus_board` puts a task at a place, 1 at the top; one not on the board yet joins it there.
- Every other task line says "on Focus" for a task on the board.

The in-app quick chat leaves the board out: every model round carries its tool list, and that list is
kept under a fixed size ([assistant](assistant.md)).
