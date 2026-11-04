# Follow-ups

- Fix the Teleport button position: keep it at the bottom left regardless of GUI
  width. Current positioning switches to the top at GUI widths of at least 480.
- Pending the user's instruction to continue: remove the candidate limit and run
  interactive searches continuously to a 30,000-block maximum, without intermediate
  radius stops. This change has not been applied yet.

Ongoing development uses `codex/nearest-first`. The comparison branch is
`codex/search-baseline`. Both retain benchmark tools. Main and fabric-1.20.1 are
existing release branches and were not modified during working-branch cleanup.
