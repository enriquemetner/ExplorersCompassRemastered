# Structure unlocking

The server reads `config/explorerscompass/server/unlocking_rules.json` at startup. A new file contains:

```json
{
  "default": "unlocked",
  "requirements": {}
}
```

[The full example](unlocking_rules.example.json) demonstrates selectors and requirements with `lockedUntilVisitedSet` as the default. You can replace that value with `unlocked` or `lockedUntilVisited`. To try the example, copy it to the server config folder as `unlocking_rules.json`.

## Requirements

Each named entry under `requirements` has a nonempty `unlockedStructures` array and exactly one of these fields.

- `requiredVisits` contains a nonempty array of structure or structure-set selectors. Visits count across dimensions.
- `requiredAdvancements` contains a nonempty array of exact advancement IDs.
- `alwaysUnlocked` must be the JSON boolean `true`. The targets unlock on ordinary evaluation even under a locked default.

For visits and advancements, `match` defaults to `any`. Use `all` to require every resolved structure type or advancement. One visit to each required structure type is sufficient. An `alwaysUnlocked` entry has no `match` field.

Structure selectors in `unlockedStructures` and `requiredVisits` expand structure sets and support `*` anywhere in an ID. Omitted namespaces default to `minecraft`. A malformed or unknown ID or an unmatched wildcard invalidates its requirement. If an ID names both a structure and a set, the structure takes precedence and the server logs a warning.

Explicit requirements override the default. When multiple valid requirements target the same structure, the last one wins and the warning lists the contributing `unlockedStructures` lines. `lockedUntilVisited` applies to structures without an explicit requirement.

With `lockedUntilVisitedSet`, a visit to any structure in a shared set satisfies the default for the other members of that set. If a structure belongs to multiple sets, a visit to a member of any of those sets counts. Structures without a set still require a visit to their own type.

## Prerequisites

Add named entries in the top-level `prerequisites` object. `appliesTo` selects gated structures, and `requiresUnlocked` selects the structures the player must personally unlock first. Both fields accept the same selectors as `unlockedStructures`. `match` defaults to `any` and also accepts `all`. If valid entries target the same structure, the last one wins.

While a prerequisite is unmet, the Locked popup lists its structures before the player can see ordinary requirements. Prerequisites apply to default and explicit requirements, claimed FTB rewards, and unlocks copied through compass sharing. Existing personal unlocks and explicit operator grants remain valid. Temporary party and Creative access do not count toward personal prerequisite progress.

If the resolved dependencies contain any loop, the server logs each loop with its selector lines and ignores the entire prerequisite section. The rest of the file still loads.

## FTB Quests rewards

When FTB Quests is installed, its reward editor offers **Unlock Structures**. Its **Structures** (`structures`) list accepts only structure IDs and patterns. The **Structure Sets** (`structure_sets`) list directly below it accepts only structure-set IDs and patterns. Both support `*` anywhere and default omitted namespaces to `minecraft`. Their combined structure targets are deduplicated and displayed in the existing alphabetical order. Move any previously configured structure-set selectors from `structures` into `structure_sets`. These same two fields are available in the custom **Visit Structures** task; Any/All applies to the combined, deduplicated structure types. An SNBT reward looks like `{ id: "0123456789ABCDEF", type: "explorerscompass:unlock_structures", structures: ["minecraft:fortress"], structure_sets: ["minecraft:villages"] }`. Claiming it grants the resolved structures to the receiving player. Completing the quest alone does not grant them.

The reward uses a static compass icon. Its title shows **Unlock Structure** for one resolved structure or **Unlock N Structures** for several. Hold Left Alt over the reward to see up to ten structure names; if there are more, the tooltip shows the remaining count.

An FTB reward takes precedence over a JSON unlocking rule for every structure it currently targets. New players see those structures locked until they claim a reward; adding a reward does not relock anyone who already unlocked a structure. Multiple rewards are alternatives. The locked requirements window shows the distinct quest names under **Complete Quest:** or **Complete any Quest:**. A previously claimed reward is reevaluated when the player opens the compass, so newly added targets unlock then and an admin-locked target is restored. If the last reward for a structure is removed, the JSON rule or configured default applies on the next open. Saved unlocks are never erased by these edits.

New visits and advancement progress can immediately add to a player's saved unlocked structures. Opening the compass also evaluates current conditions. The open screen uses the snapshot received when opened; reopen it after progress, commands, or rule changes. Saved unlocked structures (including default-unlocked structures) persist through lost requirements, config edits, and restarts. There is no automatic relocking.

Locked entries remain visible and show a requirements dialog on search attempts. Search Listed skips them. Requirement text wraps in a panel no taller than half the screen; use the wheel, scrollbar, arrow keys, Page Up/Down, or Home/End to scroll. Hover an advancement name to see its title and description. Previously found compass locations remain usable.

The client setting **Unlock All Structures in Creative** temporarily treats locked entries as unlocked while the player is in Creative mode. The server verifies Creative mode for searches, including multiplayer searches. This does not grant saved unlocks; normal locks return when the player leaves Creative mode.

## Operator commands

Permission level 2 is required. `<player>` accepts an online name, a name in `usercache.json`, or a UUID. Names not found in the cache require a UUID.

```text
/explorerscompass lock <player> structure <id>
/explorerscompass lock <player> structureSet <id>
/explorerscompass lock <player> requirement <name>
/explorerscompass lock <player> everything

/explorerscompass unlock <player> structure <id>
/explorerscompass unlock <player> structureSet <id>
/explorerscompass unlock <player> requirement <name>
/explorerscompass unlock <player> everything

/explorerscompass clearVisited <player> structure <id>
/explorerscompass clearVisited <player> all
/explorerscompass report [error|warning]
```

`lock` removes the selected IDs from saved unlocked structures. If their requirements still hold, reopening the compass adds them again. Locking also cancels an in-progress search for that online player, restoring the compass's previous result. `unlock` adds the selected IDs regardless of conditions. A `requirement` selector targets the named entry’s `unlockedStructures`. Quote requirement names containing spaces.

`clearVisited` removes every visited instance of the chosen type, or the player's entire history. It does not relock anything. A player standing inside a structure can have that visit recorded again at the next normal check. Use Minecraft's `/advancement revoke` to reset advancements separately.

Explorer’s Compass reads server settings and unlocking definitions when the world starts. After changing server files or FTB quest definitions, close and reopen a singleplayer world or restart a dedicated server. When `progression.relockOnReload` is enabled, changes relock only structures whose effective unlock requirements changed and any unlocked dependents whose prerequisites are no longer met. The mod checks each player’s progress when they join and restores unlocks they still qualify for. A dependent can keep its saved unlock if another prerequisite still satisfies its gate.

The first startup after upgrading records a baseline and preserves existing unlocks. Valid edits made while relocking is disabled still update the baseline. If the server detects invalid rules, it defers relocking and keeps the last valid baseline. If the whole file is rejected, the server applies the generated default of `unlocked` and leaves the file intact. FTB Quest rewards still take precedence over JSON unlock rules. JSON prerequisites also apply to FTB rewards.

The file must be strict JSON with an object at the root. An invalid `default`, a non-object `requirements` value, the legacy `rules` field, malformed JSON, or an unreadable file rejects the whole file. An invalid individual requirement is skipped. Its targets use another valid requirement or the configured default. Errors include the relevant source line when available. When valid rules overlap, a warning lists the contributing selector lines and the last matching rule wins.

Use `/ecomp report` to see errors and warnings from the latest load attempt. Append `error` or `warning` to show just that severity.
