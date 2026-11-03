# Compass ownership and sharing

`common.toml` contains `unlocking.compassSharing` (default `false`, server controlled).
A compass remembers its last player holder. Dropping it, storing it, or placing it in a display
keeps its target. Taking your own compass back preserves it.

When a different player receives the compass, sharing off clears the target only; sharing on
preserves it and adds the previous holder's current saved structure unlocks to the recipient.
The previous holder may be offline. Missing/blacklisted IDs are excluded. Temporary Creative
access, quest progress, and visits are not transferred. Existing compasses without holder metadata
are assigned to their first observed holder without a reset or unlock import.

An inherited target can keep pointing even if it remains locked in the recipient's list.
This does not grant search or teleport permission. Replacing or clearing the target removes
that pointing exception. Targets in another dimension spin until returned to their dimension.
Compasses animate independently in inventories, item frames, and as dropped items.

## Display integrations (client only)

Minecraft 1.20.1 does not provide a position for every mod's block-entity item renderer.
Unknown world display contexts spin instead of using the viewer's position. A display can
supply its context with `CompassRenderContexts`:

```java
try (var scope = CompassRenderContexts.push(new CompassRenderContexts.Context(
        clientWorld, displayPosition, displayYawDegrees, stableDisplayKey, false))) {
    // Include model lookup as well as the item render in this scope.
    itemRenderer.renderItem(/* display's usual arguments */);
}
```

`displayYawDegrees` follows Minecraft entity/body yaw. `stableDisplayKey` identifies the display
(or its individual slot) across frames. Alternatively, register a `CompassRenderContexts.Provider`
on the client thread; return null when it does not apply. Supplied scopes take precedence over
providers. Providers take precedence over the standard entity/holder fallback.

GUI item rendering uses the viewing player's position. Frame orientation follows vanilla body yaw,
including frame rotation; dropped-item orientation compensates for its visual rotation.
The integration API allows adapters for pedestals or future shelves but does not automatically
provide compatibility with every mod or a later Minecraft version.
