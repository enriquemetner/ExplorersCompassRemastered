> Current behaviour: interactive searches run continuously to 30,000 blocks with
> no candidate cap. The older staged-radius/Keep searching cases below are historical
> and no longer apply. Check cancellation, paused progress and final radius exhaustion instead.

# Paused compass searches

Search workers now run in short jobs on Minecraft's server task queue. The queue
continues running when the single-player world is paused. Candidate order and
synchronous structure checks are unchanged; this does not implement nearest-first
search or parallel chunk requests.

`SearchTaskQueueTest` exercises deferred execution, progress without world ticks,
cancellation, ownership checks, worker handoffs, radius continuation, fairness,
and shutdown. Run the automated checks with `./gradlew test`.

## In-game verification

Use a disposable single-player world with structures enabled. Give the player a
compass and enough XP (or use creative mode with the creative unlock bypass).
Turn on **Pause game when open** and turn off **Close on found**.

1. Open the compass near a moving mob. Search for a structure outside the loaded
   area. Confirm that the mob and world time stay frozen throughout the search
   and that the distance searched increases before the result dialog appears,
   without closing the screen. Progress uses explicit packets rather than the
   inventory synchronization that normally happens during world ticks.
2. Repeat with **Search Listed** and with a stronghold (concentric rings).
3. Search for a rare structure. If the initial radius finds nothing, choose
   **Keep searching**. Confirm that the expanded search progresses while paused
   and its distance starts at the previous radius instead of zero.
4. Cancel a running search, then immediately start another. Confirm that an old
   result does not replace the new result, and the cancelled search does not
   charge XP or damage the compass.
5. Close the screen during a search. Confirm that gameplay resumes and the
   compass's previous target is restored when cancellation is processed.
6. Turn **Pause game when open** off and repeat a search. The world should keep
   running, and the search should still finish normally.
7. Leave and reopen the world after cancelling a search. Confirm that no old
   search continues. Also check **Close on found**: receiving a result should
   close the compass and resume gameplay.
8. On a multiplayer server, search from two clients and disconnect one during
   its search. The other search should finish; the server must not pause.

A single synchronous chunk request can still exceed the scheduler's five-millisecond
slice. The scheduler yields between worker steps, not in the middle of loading or
generating a chunk. Parallel requests and interruptible checks are separate work.
