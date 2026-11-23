package io.github.enriquemetner.explorerscompass.items;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockedStructures;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockNotifications;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.availability.AvailabilitySelection;
import io.github.enriquemetner.explorerscompass.availability.StructureAvailability;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.unlocking.PartySharing;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.gui.GuiWrapper;
import io.github.enriquemetner.explorerscompass.items.CompassSearchSessions.Session;
import io.github.enriquemetner.explorerscompass.network.SyncPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchAvailabilityPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchResultPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchProgressPacket;
import io.github.enriquemetner.explorerscompass.util.ItemUtils;
import io.github.enriquemetner.explorerscompass.util.PlayerUtils;
import io.github.enriquemetner.explorerscompass.structure.StructureDestination;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures;
import io.github.enriquemetner.explorerscompass.workers.SearchWorker;
import com.google.common.collect.ListMultimap;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

public class ExplorersCompassItem extends Item {
    public static final String NAME = "explorerscompass";

    private final CompassSearchSessions sessions = new CompassSearchSessions();


    private Identifier getTrackedStructure(ServerPlayerEntity player) {
        ItemStack held = ItemUtils.getHeldItem(player, this);
        return !held.isEmpty() && getState(held) == CompassState.FOUND ? getStructureID(held) : null;
    }
    public void validateTarget(ServerPlayerEntity player, ItemStack stack) {
        if (stack.getItem() != this) return;
        ensureHolder(player, stack);
        if (getState(stack) != CompassState.FOUND) return;
        Identifier id = Identifier.tryParse(stack.getOrCreateNbt().getString(CompassSearchData.STRUCTURE_ID));
        if (id == null || !player.getServer().getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE).containsId(id)
                || StructureUtils.structureIdIsExcluded(id)
                || (!UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), id)
                    && !stack.getOrCreateNbt().getBoolean(CompassTransfer.INHERITED)
					&& !(stack.getNbt() != null && stack.getNbt().getBoolean(CompassSearchData.CREATIVE_BYPASS)
							&& player.getAbilities().creativeMode))) clearCompassTarget(stack);
    }
    public void ensureHolder(ServerPlayerEntity player, ItemStack stack) {
        if (stack.getItem() != this) return;
        NbtCompound data = stack.getOrCreateNbt();
        sessions.recoverTransferredSearch(player, stack, data);
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        CompassTransfer.Result transfer = CompassTransfer.acquire(data, player.getUuid(),
                ExplorersCompassConfig.compassSharing, getState(stack) == CompassState.FOUND,
                state::getStructures, id -> player.getServer().getRegistryManager()
                        .get(net.minecraft.registry.RegistryKeys.STRUCTURE).containsId(id)
                        && !StructureUtils.structureIdIsExcluded(id));
        if (transfer.resetTarget()) clearCompassTarget(stack);
        boolean copiedVisits = false;
        if (ExplorersCompassConfig.visitsSharing && transfer.sharingSource() != null) {
            VisitedStructures visits = VisitedStructures.get(player.getServer());
            copiedVisits = visits.addVisits(player.getUuid(), visits.savedVisits(transfer.sharingSource())) > 0;
        }
        int added = 0;
        if (copiedVisits || !transfer.unlocks().isEmpty()) {
            UnlockingRules rules = UnlockingRules.get();
            added = copiedVisits ? rules.evaluateSilently(player, transfer.unlocks())
                    : state.add(player.getUuid(), rules.applyPrerequisites(transfer.unlocks(), state.getStructures(player.getUuid())));
        }
        if (added > 0) UnlockNotifications.newlyAdded(player, added);
    }

    @Override public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (!world.isClient) SearchCosts.active().initialize(stack);
        if (!world.isClient && entity instanceof ServerPlayerEntity player
                && io.github.enriquemetner.explorerscompass.unlocking.UnlockReloads.ready(player.getUuid())) validateTarget(player, stack);
    }

    public void stopAllSearches() {
        sessions.stopAll();
    }

    public void tickSearchOwnership(MinecraftServer server) {
        sessions.tickOwnership(server);
    }

    public void revalidateAccess(ServerPlayerEntity player) {
        Session session = sessions.get(player);
        if (session != null && !(session.creativeBypass && player.getAbilities().creativeMode)
                && session.eligibleIds.stream().noneMatch(id -> UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), id))) {
            sessions.rejectDetached(session);
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            validateTarget(player, player.getInventory().getStack(slot));
        }
    }

    public void abortFailedSearch(PlayerEntity player, SearchWorker worker, RuntimeException error) {
        sessions.abort(player, worker, error);
    }

    private Session beginSearch(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int requestId,
            List<Identifier> eligibleIds, boolean creativeBypass, String target) {
        cancelSearch(player, -1);
        ensureHolder(player, stack);
        return sessions.begin(player.getUuid(), world, stack, requestId, eligibleIds, creativeBypass, target);
    }

    public void cancelSearch(PlayerEntity player, int requestId) {
        sessions.cancel(player, requestId);
    }

	public void clearCompassTarget(ItemStack stack) {
		clearSearchData(stack);
	}

    private void finishFailure(PlayerEntity player, Session session, int result, int radius) {
        if (sessions.detach(session)) {
            session.logNotFound();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                SearchResultPacket.send(serverPlayer, session.requestId, result, radius);
            }
        }
    }

    private boolean rejectIfUnaffordable(ServerPlayerEntity player, ItemStack stack, int requestId, int cost, boolean multiple) {
        SearchCosts costs = SearchCosts.active();
        if (costs.canPay(player, stack, cost)) return false;
        SearchResultPacket.sendCostFailure(player, requestId, cost, costs.available(player, stack), multiple);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        SearchCosts costs = SearchCosts.remote();
        if (costs.type() == SearchCosts.Type.ITEMS) {
            tooltip.add(1, Text.literal("Energy: " + costs.energy(stack)).formatted(Formatting.GRAY));
        }
    }

    public void consumeItems(ServerPlayerEntity player, Slot slot, boolean wholeStack) {
        var handler = player.currentScreenHandler;
        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty() || !slot.canTakeItems(player) || !slot.canTakePartial(player)) return;
        if (!consumeFuel(SearchCosts.active(), slot.getStack(), cursor, wholeStack)) return;
        if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
        slot.markDirty();
        handler.sendContentUpdates();
        playChargeSound(player, null);
    }

    public static boolean consumeFuel(SearchCosts costs, ItemStack stack, ItemStack cursor, boolean wholeStack) {
        if (cursor.isEmpty() || costs.type() != SearchCosts.Type.ITEMS) return false;
        Integer value = costs.items().get(net.minecraft.registry.Registries.ITEM.getId(cursor.getItem()));
        if (value == null) return false;
        int count = wholeStack ? cursor.getCount() : 1;
        int energy = SearchCosts.energyAfterConsumingItems(costs.energy(stack), count, value);
        if (energy < 0) return false;
        costs.setEnergy(stack, energy);
        cursor.decrement(count);
        return true;
    }

    public static void playChargeSound(PlayerEntity player, PlayerEntity excludedPlayer) {
        World world = player.getWorld();
        world.playSound(excludedPlayer, player.getBlockPos(), SoundEvents.ENTITY_ENDER_EYE_DEATH,
                SoundCategory.PLAYERS, 1, 0.8f + world.random.nextFloat() * 0.4f);
    }

	public ExplorersCompassItem() {
		super(new FabricItemSettings().maxCount(1));
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.literal("Explorer's Compass");
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (!world.isClient) ensureHolder((ServerPlayerEntity) player, stack);
		if (player.isSneaking()) {
			if (world.isClient && getState(stack) == CompassState.FOUND) GuiWrapper.animateTargetClear(hand);
			cancelSearch(player, -1);
			clearSearchData(stack);
			return TypedActionResult.pass(stack);
		}

		if (world.isClient) {
			GuiWrapper.openGUI(world, player, stack);
		} else {
			syncScreen((ServerPlayerEntity) player);
		}
		return TypedActionResult.consume(stack);
	}

	/** Re-evaluate and synchronize the compass when opened or returned to from the Quest Book. */
	public void syncScreen(ServerPlayerEntity player) {
        syncScreen(player, true);
    }

    /** Push saved state without reevaluation when only refreshing a command or a party access change. */
    public void syncScreen(ServerPlayerEntity player, boolean reevaluate) {
        ItemStack held = ItemUtils.getHeldItem(player, this);
        if (!held.isEmpty()) ensureHolder(player, held);
        if (reevaluate) UnlockingRules.get().evaluateOnOpen(player);
        PartySharing.synchronize(player.getServer());
        if (!held.isEmpty()) validateTarget(player, held);
		ServerWorld world = player.getServerWorld();
		boolean canTeleport = PlayerUtils.canTeleport(player);
		boolean hasInfiniteXp = player.getAbilities().creativeMode;
		List<Identifier> allowedStructures = StructureUtils.getAllowedStructureIDs(world);
		ListMultimap<Identifier, Identifier> dimensionsForAllowedStructures = StructureUtils.getGeneratingDimensionIDsForAllowedStructures(world, allowedStructures);
		Map<Identifier, Identifier> structureIDsToSetIDs = StructureUtils.getStructureIDsToSetIDs(world);
        Map<Identifier, RequirementNode> locked = UnlockingRules.get().personalRequirements(player, allowedStructures);
        Map<Identifier, RequirementNode> party = new HashMap<>();
        locked.entrySet().removeIf(entry -> {
            if (!UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), entry.getKey())) return false;
            party.put(entry.getKey(), entry.getValue());
            return true;
        });
		ServerPlayNetworking.send(player, SyncPacket.ID, new SyncPacket(canTeleport, ExplorersCompassConfig.allowExcludeVisited,
				hasInfiniteXp,
				allowedStructures, dimensionsForAllowedStructures, structureIDsToSetIDs,
				locked, party,
                getTrackedStructure(player),
                UnlockedStructures.get(player.getServer()).latestUnlocked(player.getUuid()),
                UnlockingRules.get().hasUnlockingRequirements(player),
                world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE).getIds().stream()
                        .filter(StructureUtils::structureIdIsExcluded).sorted().toList(),
                !ExplorersCompassConfig.structureWhitelist.isEmpty(),
                UnlockingRules.get().shouldShowRequirements(player)));
	}

    public void searchForStructure(ServerWorld world, PlayerEntity player, BlockPos pos, Identifier structureOrSetId, boolean isSet, boolean excludeVisited, boolean creativeUnlocksAll, ItemStack stack, int requestId) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        if (isSet) {
            SearchResultPacket.send(serverPlayer, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        if (StructureUtils.structureIdIsExcluded(structureOrSetId)
                && StructureUtils.getStructureForID(world, structureOrSetId) != null) {
            SearchResultPacket.sendExcluded(serverPlayer, requestId, structureOrSetId, !ExplorersCompassConfig.structureWhitelist.isEmpty());
            return;
        }
        if (!StructureUtils.getAllowedStructureIDs(world).contains(structureOrSetId)) {
            SearchResultPacket.send(serverPlayer, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        startValidatedSearch(world, serverPlayer, pos, List.of(structureOrSetId), excludeVisited, creativeUnlocksAll, stack, false, requestId);
    }

    public void searchForListedStructures(ServerWorld world, PlayerEntity player, BlockPos pos, List<Identifier> requestedIds, boolean excludeVisited, boolean creativeUnlocksAll, ItemStack stack, int requestId) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        if (requestedIds.isEmpty() || requestedIds.size() > 32767
                || !StructureUtils.getAllowedStructureIDs(world).containsAll(requestedIds)) {
            SearchResultPacket.send(serverPlayer, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        startValidatedSearch(world, serverPlayer, pos, requestedIds, excludeVisited, creativeUnlocksAll, stack, true, requestId);
    }

    private void startValidatedSearch(ServerWorld world, ServerPlayerEntity player, BlockPos pos, List<Identifier> ids,
                                      boolean excludeVisited, boolean creativeUnlocksAll, ItemStack stack, boolean listed, int requestId) {
        ensureHolder(player, stack);
        PartySharing.synchronize(world.getServer());
        boolean creativeBypass = creativeUnlocksAll && player.getAbilities().creativeMode;
        var selection = AvailabilitySelection.classify(ids, id -> StructureAvailability.check(world, id),
                id -> creativeBypass || UnlockingRules.isUnlocked(world.getServer(), player.getUuid(), id));
        boolean includeSaved = ExplorersCompassConfig.findOldStructures;
        if (!includeSaved && selection.eligible().isEmpty() && !selection.disabled().isEmpty()) {
            cancelSearch(player, -1);
            if (ServerPlayNetworking.canSend(player, SearchAvailabilityPacket.ID)) {
                SearchAvailabilityPacket.send(player, requestId, selection);
            } else {
                SearchResultPacket.send(player, requestId, SearchResultPacket.REJECTED, 0);
            }
            return;
        }
        List<Identifier> searchable = includeSaved ? ids.stream()
                .filter(id -> creativeBypass || UnlockingRules.isUnlocked(world.getServer(), player.getUuid(), id))
                .distinct().toList() : selection.eligible();
        if (searchable.isEmpty() && (includeSaved || selection.locked() > 0)) {
            RequirementNode requirements = listed
                    ? new RequirementNode(Text.literal("No unlocked structures in the list."), false, List.of())
                    : UnlockingRules.get().personalRequirements(player, ids).get(ids.get(0));
            SearchResultPacket.sendLocked(player, requestId, requirements);
            return;
        }
        SearchCosts costs = SearchCosts.active();
        costs.initialize(stack);
        List<Identifier> costTargets = searchable.isEmpty() ? ids : searchable;
        int highestCost = costTargets.stream().mapToInt(costs::cost).max().orElse(0);
        if (rejectIfUnaffordable(player, stack, requestId, highestCost, costTargets.size() > 1)) return;
        if (searchable.isEmpty()) {
            cancelSearch(player, -1);
            SearchResultPacket.send(player, requestId, SearchResultPacket.WRONG_DIMENSION, 0);
            return;
        }
        List<Structure> structures = includeSaved
                ? searchable.stream().map(id -> StructureUtils.getStructureForID(world, id)).toList() : List.of();
        List<Structure> generation = selection.eligible().stream().map(id -> StructureUtils.getStructureForID(world, id)).toList();
        Identifier firstId = searchable.get(0);
        String target = listed ? searchable.size() + " listed structures" : firstId.toString();
        Session session = beginSearch(world, player, stack, requestId, searchable, creativeBypass, target);
        try {
            session.availability = includeSaved ? selection : null;
            setSearching(stack, firstId);
            SearchAvailabilityPacket.send(player, requestId, includeSaved
                    ? new AvailabilitySelection(searchable, List.of(), 0, selection.locked()) : selection);
            session.worker = SearchWorker.create(world, player, stack, generation, structures, excludeVisited, creativeBypass, pos);
            if (session.worker == null || !session.worker.hasWork()) {
                fail(player, stack, false);
            } else {
                session.worker.setRadiusLimit(session.radiusLimit);
                session.worker.start();
                SearchProgressPacket.send(player, requestId, 0);
            }
        } catch (RuntimeException error) {
            if (!sessions.isActive(session)) throw error;
            sessions.abort(session, error);
        }
    }

    // Retain the packet entry point for older clients, but searches no longer have continuation stages.
    public void continueSearch(ServerWorld world, PlayerEntity player, ItemStack stack, int requestId) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            SearchResultPacket.send(serverPlayer, requestId, SearchResultPacket.REJECTED, 0);
        }
    }

	public void succeed(PlayerEntity player, ItemStack stack, Identifier structureID, ChunkPos startChunk, int x, int z, BlockBox bounds, int radius, int samples, boolean displayCoordinates, boolean displayDistance) {
        PartySharing.synchronize(player.getServer());
		Session session = sessions.forStack(player, stack);
		if (session == null) return;
        if (structureID == null) throw new IllegalStateException("Located structure has no registered ID");
        if (!session.isHeldBy(player)) { sessions.rejectDetached(session); return; }
		if (!(session.creativeBypass && player.getAbilities().creativeMode)
				&& !UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), structureID)) {
			cancelSearch(player, session.requestId);
			SearchResultPacket.send((ServerPlayerEntity) player, session.requestId, SearchResultPacket.REJECTED, 0);
			return;
		}
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        SearchCosts costs = SearchCosts.active();
        int cost = costs.cost(structureID);
        if (rejectIfUnaffordable(serverPlayer, stack, session.requestId, cost, false)) {
            cancelSearch(player, session.requestId);
            return;
        }
        List<Identifier> biomes = StructureDestination.sampleBiomes(session.world, bounds);
        var structure = StructureUtils.getStructureForID(session.world, structureID);
        int validBiomeMask = 0;
        if (structure != null) {
            var biomeRegistry = session.world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME);
            for (int i = 0; i < biomes.size(); i++) {
                var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME, biomes.get(i));
                if (biomeRegistry.getEntry(key).filter(structure.getValidBiomes()::contains).isPresent()) {
                    validBiomeMask |= 1 << i;
                }
            }
        }
        Boolean visited = ExplorersCompassConfig.allowExcludeVisited
                ? VisitedStructures.hasEffectiveVisit(session.world.getServer(), player.getUuid(),
                        session.world.getRegistryKey().getValue(), structureID, startChunk)
                : null;
        if (!ItemUtils.verifyNBT(stack)) throw new IllegalStateException("Compass unavailable while saving search result");
        costs.pay(serverPlayer, stack, cost);
        CompassSearchData.commitFound(stack.getNbt(), structureID, x, z, samples,
                player.getWorld().getRegistryKey().getValue(), startChunk.toLong(),
                session.creativeBypass, displayCoordinates, displayDistance);
        if (costs.type() == SearchCosts.Type.ITEMS) {
            serverPlayer.currentScreenHandler.sendContentUpdates();
        }
        if (player.getMainHandStack() == stack) {
            player.swingHand(Hand.MAIN_HAND, true);
        } else if (player.getOffHandStack() == stack) {
            player.swingHand(Hand.OFF_HAND, true);
        }
        if (sessions.detach(session)) {
            session.logFound(structureID, radius);
            SearchResultPacket.send(serverPlayer, session.requestId, SearchResultPacket.FOUND, 0,
                    structureID, x, z, displayCoordinates, displayDistance, biomes, validBiomeMask, visited);
        }
	}

	public void fail(PlayerEntity player, ItemStack stack, boolean reachedRadiusLimit) {
        Session session = sessions.forStack(player, stack);
        if (session == null) return;
        if (!session.isHeldBy(player)) { sessions.rejectDetached(session); return; }
        session.reachedRadiusLimit |= reachedRadiusLimit;
        CompassSearchData.restore(stack.getOrCreateNbt(), session.previousNbt);
        if (session.availability != null && session.availability.eligible().isEmpty()) {
            var selection = session.availability;
            if (!selection.disabled().isEmpty() && player instanceof ServerPlayerEntity serverPlayer
                    && ServerPlayNetworking.canSend(serverPlayer, SearchAvailabilityPacket.ID)) {
                if (sessions.detach(session)) session.logNotFound();
                SearchAvailabilityPacket.send(serverPlayer, session.requestId, selection);
                return;
            }
            if (selection.wrongDimension() > 0) {
                finishFailure(player, session, SearchResultPacket.WRONG_DIMENSION, 0);
                return;
            }
        }
        if (session.availability != null && player instanceof ServerPlayerEntity serverPlayer) {
            SearchAvailabilityPacket.send(serverPlayer, session.requestId, session.availability);
        }
        finishFailure(player, session, SearchResultPacket.NOT_FOUND, session.radiusLimit);
	}

	public boolean isActive(ItemStack stack) {
		return ItemUtils.verifyNBT(stack) && getState(stack) != CompassState.INACTIVE;
	}

	public void setSearching(ItemStack stack, Identifier structureID) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) CompassSearchData.writeSearching(data, structureID);
	}

	public void setFound(ItemStack stack, Identifier structureID, int x, int z, int samples) {
		clearSearchData(stack);
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) CompassSearchData.writeFound(data, structureID, x, z, samples);
	}

	public void setNotFound(ItemStack stack, int searchRadius, int samples) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) CompassSearchData.writeNotFound(data, searchRadius, samples);
	}

	public void setInactive(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putInt(CompassSearchData.STATE, CompassState.INACTIVE.getID());
	}

	public void setState(ItemStack stack, BlockPos pos, CompassState state) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putInt(CompassSearchData.STATE, state.getID());
	}

	public void setFoundStructureX(ItemStack stack, int x) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putInt(CompassSearchData.FOUND_X, x);
	}

	public void setFoundStructureZ(ItemStack stack, int z) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putInt(CompassSearchData.FOUND_Z, z);
	}

	public void setStructureKey(ItemStack stack, Identifier structureID) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putString(CompassSearchData.STRUCTURE_ID, structureID.toString());
	}

    public void reportSearchProgress(PlayerEntity player, ItemStack stack, int radius) {
        Session session = sessions.forStack(player, stack);
        if (session == null) return;
        int previousRadius = getSearchRadius(stack);
        setSearchRadius(stack, radius);
        int updatedRadius = getSearchRadius(stack);
        // Send progress only when the recorded radius increases.
        if (updatedRadius > previousRadius && player instanceof ServerPlayerEntity serverPlayer) {
            SearchProgressPacket.send(serverPlayer, session.requestId, updatedRadius);
        }
    }

	public void setSearchRadius(ItemStack stack, int searchRadius) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) {
			data.putInt(CompassSearchData.SEARCH_RADIUS, Math.max(data.getInt(CompassSearchData.SEARCH_RADIUS), searchRadius));
		}
	}

	public void setSamples(ItemStack stack, int samples) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putInt(CompassSearchData.SAMPLES, samples);
	}

	public void setDisplayCoordinates(ItemStack stack, boolean displayPosition) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putBoolean(CompassSearchData.DISPLAY_COORDINATES, displayPosition);
	}

	public void setDisplayDistance(ItemStack stack, boolean displayDistance) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) data.putBoolean(CompassSearchData.DISPLAY_DISTANCE, displayDistance);
	}

	public CompassState getState(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return data == null ? null : CompassState.fromID(data.getInt(CompassSearchData.STATE));
	}

	public int getFoundStructureX(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return data == null ? 0 : data.getInt(CompassSearchData.FOUND_X);
	}

	public int getFoundStructureZ(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return data == null ? 0 : data.getInt(CompassSearchData.FOUND_Z);
	}

	public Identifier getFoundDimension(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data == null) return null;
		if (data.contains(CompassSearchData.FOUND_DIMENSION)) {
			return Identifier.tryParse(data.getString(CompassSearchData.FOUND_DIMENSION));
		}
		Identifier structureId = Identifier.tryParse(data.getString(CompassSearchData.STRUCTURE_ID));
		if (structureId == null || ExplorersCompass.allowedStructureIDsToDimensionIDs == null) return null;
		List<Identifier> dimensions = ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureId);
		return dimensions.stream().distinct().count() == 1 ? dimensions.get(0) : null;
	}

	public boolean isInFoundDimension(ItemStack stack, World world) {
		Identifier dimension = getFoundDimension(stack);
		return dimension == null || dimension.equals(world.getRegistryKey().getValue());
	}

	public Identifier getStructureID(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return new Identifier(data == null ? "" : data.getString(CompassSearchData.STRUCTURE_ID));
	}

	public int getSearchRadius(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return data == null ? -1 : data.getInt(CompassSearchData.SEARCH_RADIUS);
	}

	public int getSamples(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return data == null ? -1 : data.getInt(CompassSearchData.SAMPLES);
	}

	/** Legacy name for the horizontal distance to the compass's structure target. */
	public int getDistanceToBiome(PlayerEntity player, ItemStack stack) {
		return StructureUtils.getHorizontalDistanceToLocation(player, getFoundStructureX(stack), getFoundStructureZ(stack));
	}

	public boolean shouldDisplayCoordinates(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		return data == null || !data.contains(CompassSearchData.DISPLAY_COORDINATES)
				|| data.getBoolean(CompassSearchData.DISPLAY_COORDINATES);
	}

	public boolean shouldDisplayDistance(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null && data.contains(CompassSearchData.DISPLAY_DISTANCE)) {
			return data.getBoolean(CompassSearchData.DISPLAY_DISTANCE);
		}
		return shouldDisplayCoordinates(stack);
	}

	private void clearSearchData(ItemStack stack) {
		var data = ItemUtils.getOrCreateCompassNbt(stack);
		if (data != null) CompassSearchData.clear(data);
	}
}
