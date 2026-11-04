# Comparing the search algorithms

Branches:
- `codex/search-baseline`: unchanged algorithm at 178f8d9.
- `codex/nearest-first`: clean nearest-first implementation.
- `codex/search-baseline-benchmark`: baseline plus temporary measurements.
- `codex/nearest-first-benchmark`: nearest-first plus the same measurement collector.

Build either benchmark branch with `./gradlew build --offline`. Install only one
Explorer's Compass JAR at a time. Clearly labelled builds are in `build/comparison/`
(untracked local artifacts). Do not merge benchmark branches into the release branch.

Each completed search stage appends a CSV row to `logs/explorerscompass/baseline-placement-filter.csv`
or `logs/explorerscompass/nearest-first-placement-filter.csv` inside the game's folder. With
`./gradlew runClient`, these are under this project's `run/logs/explorerscompass/`.
The files retain previous launches, include UTC timestamps, and are created after
the first completed/cancelled stage. They can be opened in a spreadsheet or text
editor. File writing happens after the elapsed-time measurement stops. Each stage
also announces its output path in the normal log.

Each completed search stage also writes a `COMPASS_BENCH` line to Minecraft's
`logs/latest.log` (server log for multiplayer). It includes:
- request id, requested structure/group, last checking worker, outcome and origin;
- result coordinates and exact horizontal distance, or -1 for no result;
- elapsed_ms: server-side wall time, including queue waits, excluding user wait
  between Keep searching stages; initial placement preparation is included;
- candidates: candidate positions calculated in this stage, including candidates
  retained for later stages (thus a later stage may check more than it calculates);
- placement_rejections: placement predicates that rejected a candidate before generation checks; saved answers bypass this filter;
- checks: calls to candidate validation, including immediate presence-cache answers;
- chunk_requests: synchronous STRUCTURE_STARTS chunk requests, not necessarily new generation;
- check_ms: total wall time inside candidate validation, not CPU time.

`stage_exhausted` means a stage ended without success, including radius/sample
limits. `stopped` covers cancellation/disconnect. A `found` line records discovery;
normal final ownership/XP validation may still reject delivery. Timing ends at
server discovery, not at the client's rendering of the result. Logging happens
once per stage, not once per candidate. Preparation/check counters are aggregated
across all placement rules for that stage.

For fair cold comparisons, stop Minecraft and make two copies of the same saved
world BEFORE searching. Match seed, mods, configs, unlocks, visited data, player
position, requested structure(s), and pause setting. Use one fresh copy per run.
Restart the game between variants, alternate which variant goes first, and repeat
several positions/structures. Do not test the second variant in a world modified
by the first. Repeated searches in the same loaded world are a separate warm-cache
experiment; report them separately. Compare both elapsed time and result distance:
the baseline can return a farther structure, so it is not a correctness oracle.

Include random spread, strongholds, Search Listed, multiple grids, no nearby
result, Keep searching and visited exclusions. Record game version and hardware
alongside results. No live-world performance conclusions have been established
by the automated tests.

The placement-filter revision keeps earlier `.csv` files intact. Candidate and check
counts still include positions rejected by placement rules; `placement_rejections`
shows how often the new filter avoided further generation validation. A passing
predicate does not guarantee a structure. Existing saved-record lookup still runs
first, so cached results may produce zero placement rejections.
