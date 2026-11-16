# Structure unlocking

The server reads `config/explorerscompass/server/unlocking_rules.json` at startup. A new file contains:

```json
{
  "default": "unlocked",
  "rules": {}
}
```

[The full example](unlocking_rules.example.json) demonstrates selectors and conditions with `lockedUntilVisitedSet` as the default. You can replace that value with `unlocked` or `lockedUntilVisited`. To try the example, copy it to the server config folder as `unlocking_rules.json`.

## Rules

Each named rule contains:

- `unlocks`: a nonempty array. Entries may name structures or structure sets; set members are expanded and the results are deduplicated.
- `condition`: one visit or advancement condition. Nested `any`/`all` condition groups are not supported.

The compass generates player-facing requirement text from each condition. The former `description` field is no longer accepted.

A visit condition has `type: "visit"` and a nonempty `structures` array. Entries may name structures or structure sets. `match` may be `any` (the default) or `all` across the expanded, deduplicated structure types. One instance of each structure type is sufficient for `all`; visits count across dimensions. An advancement condition has `type: "advancement"` and a nonempty `advancements` array, even for one advancement. Its `match` is `any` by default or `all` to require every listed advancement.

`structures` selectors support `*` for zero or more characters. Patterns match complete IDs in both the structure and structure-set registries, including namespace; omitting the namespace means `minecraft`. `?` is not supported. If the same ID names a structure and a structure set, the structure takes precedence and a warning is logged. Advancement IDs remain exact. Patterns resolve at startup and on `/explorerscompass reload`. A malformed or unknown ID or an unmatched wildcard invalidates the containing rule.

Explicit rules override the default. If multiple rules target the same structure, the rule farther down in the file supplies its condition and requirement text. The server warns with the structure ID and the line numbers of the matching `unlocks` entries, including entries that expand from patterns or structure sets. `lockedUntilVisited` applies only to structures without an explicit rule and requires a visit to that structure type.

With `lockedUntilVisitedSet`, a visit to any structure in a shared set satisfies the default for the other members of that set. If a structure belongs to multiple sets, a visit to a member of any of those sets counts. Structures without a set still require a visit to their own type.

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
/explorerscompass lock <player> rule <name>
/explorerscompass lock <player> all

/explorerscompass unlock <player> structure <id>
/explorerscompass unlock <player> structureSet <id>
/explorerscompass unlock <player> rule <name>
/explorerscompass unlock <player> all

/explorerscompass clearVisited <player> structure <id>
/explorerscompass clearVisited <player> all
/explorerscompass reload
/explorerscompass report [error|warning]
```

`lock` removes the selected IDs from saved unlocked structures. If their requirements still hold, reopening the compass adds them again. Locking also cancels an in-progress search for that online player, restoring the compass's previous result. `unlock` adds the selected IDs regardless of conditions. A `rule` selector means all its target structures, not its requirement structures. Quote rule names containing spaces.

`clearVisited` removes every visited instance of the chosen type, or the player's entire history. It does not relock anything. A player standing inside a structure can have that visit recorded again at the next normal check. Use Minecraft's `/advancement revoke` to reset advancements separately.

The reload command rereads the file. If the whole file is rejected, the server applies the generated default of `unlocked` with no explicit rules and leaves the file intact. FTB Quest rewards still take precedence.

The file must be strict JSON with an object at the root. An invalid `default`, a non-object `rules` value, malformed JSON, or an unreadable file rejects the whole file. An invalid individual rule is skipped. Its targets use another valid rule or the configured default. Errors include the relevant source line when available. When valid rules overlap, a warning lists the contributing selector lines and the last matching rule wins.

Use `/ecomp report` to see errors and warnings from the latest load attempt. Append `error` or `warning` to show just that severity.
