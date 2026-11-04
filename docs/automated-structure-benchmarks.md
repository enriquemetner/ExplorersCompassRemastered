# Automated structure searches (benchmark branch only)

Prepare once: `python3 scripts/prepare-structure-benchmarks.py`.
Run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runStructureBenchmarks`.
This uses `run-structure-benchmarks/`, not the normal development world's folder.
The setup script copies existing development mods/configs into that folder and adds
pinned Fabric 1.20.1 versions of Repurposed Structures, When Dungeons Arise,
YUNG's Better Strongholds and their dependencies. Downloads have SHA-512 checks;
versions are pinned in scripts/structure-benchmark-mods.lock.json. Cataclysm and
Alex's Caves are Forge mods and are not part of this Fabric suite.

Create a new creative default-generation world with cheats and a fixed seed. Do not
use a previous benchmark world for a cold repeat. Keep the game unpaused and run:

`/compassbench origins 20 30000`

Runs 20 searches per available suite target at deterministic positions 80,000 blocks
apart (first at 0,0), in the target's dimension. This samples independent origins
rather than favourable positions next to already-found structures. Results include
seed, dimension, exact origin, distance, elapsed time, candidate counts, placement
rejections, timeout and radius limits. A fresh timestamped CSV is written under
`run-structure-benchmarks/logs/explorerscompass/automated/`.

Alternatively `/compassbench distinct 20 20000` finds up to 20 distinct instances
per type from 0,0. A temporary per-type start-chunk exclusion set replaces the need
to physically visit structures. It is cleared between structure types and on exit;
ordinary visit history, quests, player position and inventory are not modified.
This measures the 1st through 20th unvisited result, not 20 independent nearest
results. Distinct mode moves to the next type after a missing result or timeout.

`/compassbench status` and `/compassbench stop` report or cancel a run.
Player disconnect and server shutdown cancel the suite. Only one suite per server
runs at a time. Each search has a 120-second wall-time ceiling; one synchronous
Minecraft operation can delay its enforcement. Timeout rows are censored results,
not evidence that no structure exists. Some rare targets may take more than a few
minutes across all repetitions. Do not estimate success rates while ignoring
timeouts. The automation does not enforce the legacy candidate-count cutoff;
normal interactive searches retain their current settings.

The selected targets cover treasure, mesa mineshafts, vanilla mansions, lich prisons,
RS Nether ancient cities/monuments/desert mansions/ocean mineshafts, WDA Infested
Temple/Heavenly Conqueror and YUNG strongholds. Targets with no applicable placement
in any loaded dimension are omitted; startup reports the number actually selected.
This is search instrumentation: it calls the same search workers and validation,
but bypasses compass costs, ownership and GUI delivery, so elapsed time ends at
server discovery. World generation and chunk caches change while tests run. Keep
mod manifests, use fresh same-seed worlds, and repeat across several seeds. Repeats
within a seed are spatial samples, not independent seeds. No empirical 20k success
guarantee is established just by installing the harness.
