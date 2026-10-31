# Task workflow (planner → designer → implementer → tester)

Goal: build the right thing the first time, and spend fewer tokens doing it. The agents come from the `workflow` plugin
(github.com/Epsuchti/claude-agents, enabled per user in `~/.claude/settings.json`). A project can override an agent
with a file of the same name in `.claude/agents/`.

1. **Plan.** The main session hands the owner's request to `planner` (Opus).
   - The planner reads only what it needs and returns its understanding plus numbered questions, each with a
     recommendation.
   - The main session asks the owner and sends the answers back to the same planner, which keeps its context.
   - Repeat until clear. The planner then writes `docs/tasks/<yyyy-mm-dd>-<slug>.md`.
2. **Approve.** The owner reads the plan's summary and approves it, or corrects it (back to step 1).
3. **Design (UI changes only, optional).** If the plan flags a UI change, the main session asks the owner
   whether `designer` (Fable) should make a mockup first. The owner's decisions on it go into the plan file.
4. **Implement.** `implementer` (Opus) works in its own worktree from the plan file, runs only targeted checks,
   and reports.
5. **Test.** `tester` (Haiku) runs the full suites on the implementer's branch and reports failures compactly.
6. **Fix.** Findings go back to the same implementer (SendMessage), so it reuses its context. If its context is
   nearly full, start a fresh implementer with the plan file and the tester's report instead; that's cheaper.
7. **Merge.** The main session merges into `main` (check `git diff --name-only --diff-filter=U` first), updates
   `docs/ledger.md` and `docs/decisions.md`, and pushes only when the owner says so.

## Why this saves tokens
- The plan file is the handover. The implementer and tester read it instead of re-exploring, and the main
  session doesn't carry their transcripts.
- Questions are answered before any code is written: wrong-direction work is the most expensive kind.
- The tester reports in about 30 lines instead of whole logs, and the implementer runs only targeted checks.
- Fixes reuse the implementer's context while it's still small.

## Tips
- Which path to take is decided by the table in `AGENTS.md` ("Choosing the workflow"): small, clear tasks go to `fixer`
  (Sonnet), questions and one-line changes stay in the main session, everything else uses this flow.
- Keep the plan file current: when the owner changes something mid-way, edit the plan first, then tell the
  implementer.
- The tester's model is Haiku: fine for running commands and summarising. If a failure needs judgement (bug or
  infrastructure), the implementer decides.
