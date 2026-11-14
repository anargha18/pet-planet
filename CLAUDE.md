# Claude Code entry point

Project rules live in `AGENTS.md` (single source of project instructions; do not duplicate them here):

@AGENTS.md

## Session memory protocol

Persistent working memory for this repository is in `memory/`. It records *where the work stands*; the canonical technical documentation stays in `docs/project-context/`.

| File | Holds |
|---|---|
| `memory/NOW.md` | Current phase, last checkpoint, what is done/verified, what is next. **Read first.** |
| `memory/DECISIONS.md` | Confirmed decisions (owner-approved or deliberate deviations) with reasons. |
| `memory/ARCHITECTURE.md` | Verified current architecture in brief (links to the full docs). |
| `memory/KNOWN_ISSUES.md` | Confirmed open bugs, gaps and environment gotchas. |

**At session start:** read `memory/NOW.md`, then the files it points to. Verify against `git log` / `git status` before acting; never redo work NOW.md marks done.

**After every meaningful task** (a phase step, a commit, a verified fix, a new decision or bug): update the affected memory files in the same change.

**Before context runs low or a session ends:** write a checkpoint in `memory/NOW.md` (HEAD commit, uncommitted state, last verified result, exact next action).

**Rules:** only record what was verified (say "not verified" otherwise); keep entries short; no copied source code or conversation transcripts; link to docs instead of repeating them.
