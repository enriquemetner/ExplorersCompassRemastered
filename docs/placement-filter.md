# Early placement filtering

All compass search workers now call the placement object's `shouldGenerate`
predicate before generation feasibility work. This delegates frequency reduction,
exclusion zones and custom placement checks to Minecraft/mod code, including when
our candidate enumeration falls back to individual chunks.

The hook runs in StructureLocator after its cached and saved-data checks, but before
its generation-possibility cache and biome/terrain generation-position calculation.
Existing records remain authoritative even when today's placement rules would
reject that chunk. Old-data/read failures retain vanilla's chunk-load fallback.
This does not add enumeration of old structure positions outside the current
candidate grids; it preserves detection for candidate chunks we already inspect.

Placement rejection is scoped to the calling compass lookup, chunk, structure and
thread. It is not written into Minecraft's structure-wide generation cache, because
another placement for that same structure may allow the chunk. Normal Minecraft
lookups remain unchanged. The context is restored after exceptions and nested calls.

This is an early filter within candidate validation, not a change to candidate
ordering. We still calculate, sort and count the same candidate positions. Saved
chunk record lookup can still incur I/O. Passing the filter still requires normal
structure validation. No fixed speedup is promised.

Tests cover scope cleanup, nesting, reentrancy, other threads, saved-answer bypass,
and rejection isolation between placements. A bytecode test verifies that the
Minecraft 1.20.1 injection target exists exactly once, after the saved-data lookup
and before generation caching. Runtime Mixin application and in-game timing still
need verification in a game launch.

For manual validation: compare returned targets on a fresh world with the previous
build; check treasure, mineshafts, outposts and strongholds; repeat with visited
exclusion and Search Listed. Verify a recorded structure in an inspected candidate
chunk remains findable after changing frequency to zero. Benchmark files for this
revision have the suffix `-placement-filter.csv` so earlier data stays separate.
