# Test saved structure searches on Fabric 1.20.1


Set `[search]` → `findOldStructures = true` in `config/explorerscompass/server/settings.toml`, then restart the game or server before testing.

1. Create a disposable creative world with structures enabled and cheats on. Leave the test pack out for the initial generation.
2. Run `/locate structure minecraft:desert_pyramid`, teleport to the result, and load the pyramid. Confirm the compass can find it. Disable **Exclude visited** and make sure Desert Pyramid is unlocked.
3. Save and exit. Copy the [saved-structure-placement](saved-structure-placement) folder into the world's `datapacks` folder. Reopen the world so its generation settings are rebuilt. The pack changes the desert pyramid placement salt and sets its generation frequency to zero.
4. Search from near the original pyramid. The compass should find the saved pyramid even though current placement can no longer generate one. Record the result and search time.
5. Save and exit, reopen, and repeat from the same position. The result should be identical. The index is saved under the world's dimension data directory as `explorerscompass_saved_starts_v1.dat`. Chunks already loaded into memory are read from live state, so use an origin outside visual loading range of the pyramid to exercise saved chunk reads and index reuse.
6. To compare against active generation candidates, change the test pack's frequency to `1.0`, keep its new salt, and reopen the world. Search from near the old pyramid again.

The queue ranks by the placement locate position, or the chunk origin when no placement remains. After choosing a start, the compass refines the displayed destination to a piece as usual. Use a disposable world because chunks generated with the pack retain their own generation history.

The index invalidates a region's coverage when the region file revision changes. For a repeat timing comparison, let the world save and close after generating chunks, then measure from an area that stays stable between runs. If a region is heavily explored and has not been indexed yet, the first search can require many metadata reads. File replacement or edits should be done with the world closed.

New chunk saves populate the index from the metadata Minecraft already serialized. This includes saves from exploration and Chunky, and records coverage for chunks with no starts. To test save-time indexing, pregenerate a fresh area with this build, save and quit, then reopen and make the first compass search there. Existing saved chunks still need a metadata scan unless they are saved again.
