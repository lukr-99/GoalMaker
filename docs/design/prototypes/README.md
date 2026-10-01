# Design prototypes

Throwaway, clickable HTML pages for choosing between design options. They are not app code. When the
owner picks an option, the choice goes into an ADR and [the design spec](../spec.md), and the apps
are built from those.

Open a prototype straight from disk, or serve the repository root so the bundled fonts in `fonts/`
load (otherwise they come from Google Fonts):

```powershell
python -m http.server 8765
# http://localhost:8765/docs/design/prototypes/<file>.html
```

| File | Questions | Status |
|---|---|---|
| [habits-goals-add-prototype.html](habits-goals-add-prototype.html) | 1. The habit item and where habits sit on Today. 2. The Goals page. 3. Where the add button goes on Wants, Habits and Goals. | Waiting for the owner's picks |

The navigation prototype behind [ADR 0014](../../adr/0014-pinned-places-and-the-places-hub.md) lives on
the branch `prototype/m8-navigation`.

Each page switches question, option, device (phone or PC), theme and light or dark at the top, and
keeps that in the URL. Theme tokens are copied from `contracts/design/themes.json`; if those change,
the copy in the page goes stale, which is fine for a throwaway.
