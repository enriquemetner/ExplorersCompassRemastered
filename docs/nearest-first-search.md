# Nearest-first search

The search uses one coordinator across all placement rules and requested structures.
Distance means exact squared horizontal distance from the request's origin to the
placement's locate position (the compass target), not distance to a building's edge.
Equal-distance targets may be returned in either order.

Random-spread regions enter a priority queue using the minimum distance to their
allowed candidate rectangle. Spacing defines the grid; separation reduces the
rectangle. Exploring a region calculates its candidate once and retains that
candidate even outside the current search radius. Candidate positions share the
queue with region bounds: an expensive check is safe only when no unexplored region
can contain a closer candidate. This avoids preparing an entire candidate-radius
circle before checking an already-proven nearest candidate.

Each grid expands from its closest rectangle, with a unique parent for every
region. Bounds cannot decrease along parent-to-child paths. Consequently the
frontier bounds cover even regions not yet inserted, without a growing visited set.
The frontier keeps advancing when every known candidate fails.

Concentric-ring positions use Minecraft's finite cached list and join the same
queue. Other placements fall back to checking every chunk in distance order.
Bounds assume Minecraft's standard locate offset (a fixed translation of chunk
coordinates); custom mods overriding that coordinate convention need an adapter.

Preparation advances one region per scheduler step. Expensive structure checks
remain synchronous, on the existing server task queue. No parallel chunk checks
are introduced. Cancellation, ownership validation, pause support, unlock/visited
filters and search costs continue using existing code.

Interactive searches run once to the exclusive horizontal radius set by
`search.maxSearchRadius` in `common.toml`. The default is 30,000 blocks and the
maximum is 50,000.
There is no candidate-count limit or intermediate continuation prompt. Progress
reports the lower bound of remaining work, not the farthest candidate prepared.
Candidate counts are retained for reporting only. Cancellation and scheduler slices
remain in place; a single synchronous chunk operation can still overrun a slice.
The retired `search.maxSamples` setting is removed from saved configuration.

Verification: randomized exhaustive comparisons cover mixed 32/24-chunk grids,
chunk fallback, separation gaps, negative origins, and locate offsets. Further tests
cover ties, retained frontier positions, finite ring positions, and advancing after failed checks.
In-game checks still needed: paused progress, Search Listed, strongholds, visited
filtering, cancellation/disconnection, and radius exhaustion (see paused-search-testing.md).
