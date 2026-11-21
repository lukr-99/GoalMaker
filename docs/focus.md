# Focus Board

The Focus Board is the owner's short list of tasks to keep an eye on, whatever day or list they sit
in: a school deadline, a form to hand in, an errand that keeps slipping. It is a place of its own in
both apps. The rules below are pinned by [`contracts/vectors/focus.json`](../contracts/vectors/focus.json)
and run by both apps and the connector.

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

## In the apps

- **Android:** long-press the handle and drag to reorder, swipe a task off the board, tick it to
  complete it, and the add button to pick a task.
- **Windows:** Up and Down to reorder, the add list to pick a task, and the quick-add field to make a
  new task straight onto the board.

## Through Claude

The connector reads and changes the board ([connector](connector.md)):

- `get_focus_board` lists the board top first, numbered.
- `add_task` and `update_task` take `focus`: true puts the task at the end (one already on the board
  keeps its place), false takes it off.
- `place_on_focus_board` puts a task at a place, 1 at the top; one not on the board yet joins it there.
- Every other task line says "on Focus" for a task on the board.

The in-app quick chat leaves the board out: every model round carries its tool list, and that list is
kept under a fixed size ([assistant](assistant.md)).
