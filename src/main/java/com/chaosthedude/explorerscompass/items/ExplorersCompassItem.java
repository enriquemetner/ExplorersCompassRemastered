package com.chaosthedude.explorerscompass.items;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import com.chaosthedude.explorerscompass.util.CompassTransfer;
import com.chaosthedude.explorerscompass.util.CompassSearchData;
import com.chaosthedude.explorerscompass.unlocking.UnlockedStructures;
import com.chaosthedude.explorerscompass.unlocking.UnlockNotifications;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.availability.AvailabilitySelection;
import com.chaosthedude.explorerscompass.availability.StructureAvailability;
import com.chaosthedude.explorerscompass.unlocking.RequirementNode;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules;
import com.chaosthedude.explorerscompass.unlocking.PartySharing;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.gui.GuiWrapper;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.SearchAvailabilityPacket;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
import com.chaosthedude.explorerscompass.network.SearchProgressPacket;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.PlayerUtils;
import com.chaosthedude.explorerscompass.util.StructureDestination;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.workers.SearchWorkerManager;
import com.google.common.collect.ListMultimap;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

public class ExplorersCompassItem extends Item {
    private Identifier getTrackedStructure(ServerPlayerEntity player) {
        ItemStack held = ItemUtils.getHeldItem(player, this);
        return !held.isEmpty() && getState(held) == CompassState.FOUND ? getStructureID(held) : null;
    }
    public void validateTarget(ServerPlayerEntity player, ItemStack stack) {
        if (stack.getItem() != this) return;
        ensureHolder(player, stack);
        if (getState(stack) != CompassState.FOUND) return;
        Identifier id = Identifier.tryParse(stack.getOrCreateNbt().getString("StructureID"));
        if (id == null || !player.getServer().getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE).containsId(id)
                || StructureUtils.structureIdIsBlacklisted(id)
                || (!UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), id)
                    && !stack.getOrCreateNbt().getBoolean(CompassTransfer.INHERITED)
					&& !(stack.getNbt() != null && stack.getNbt().getBoolean("CreativeBypass")
							&& player.getAbilities().creativeMode))) clearCompassTarget(stack);
    }
    public void ensureHolder(ServerPlayerEntity player, ItemStack stack) {
        if (stack.getItem() != this) return;
        NbtCompound data = stack.getOrCreateNbt();
        if (data.containsUuid(CompassSearchData.TOKEN)) {
            UUID token = data.getUuid(CompassSearchData.TOKEN);
            SearchSession active = searches.get(player.getUuid());
            if (active == null || active.stack != stack || !active.token.equals(token)) {
                // Stop the old owner's workers before importing any unlocks or recording a new holder.
                for (UUID owner : List.copyOf(searches.keySet())) {
                    SearchSession old = searches.get(owner);
                    if (old.token.equals(token)) stopDetachedSearch(owner, old);
                }
                CompassSearchData.restore(data, token);
            }
        }
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        CompassTransfer.Result transfer = CompassTransfer.acquire(data, player.getUuid(),
                ExplorersCompassConfig.compassSharing, getState(stack) == CompassState.FOUND,
                state::getStructures, id -> player.getServer().getRegistryManager()
                        .get(net.minecraft.registry.RegistryKeys.STRUCTURE).containsId(id)
                        && !StructureUtils.structureIdIsBlacklisted(id));
        if (transfer.resetTarget()) clearCompassTarget(stack);
        boolean copiedVisits = false;
        if (ExplorersCompassConfig.visitsSharing && transfer.sharingSource() != null) {
            VisitedStructures visits = VisitedStructures.get(player.getServer());
            copiedVisits = visits.addVisits(player.getUuid(), visits.savedVisits(transfer.sharingSource())) > 0;
        }
        int added = copiedVisits
                ? UnlockingRules.get().evaluateSilently(player, transfer.unlocks())
                : state.add(player.getUuid(), transfer.unlocks());
        if (added > 0) {
            UnlockNotifications.newlyAdded(player, added);
            com.chaosthedude.explorerscompass.network.RulesChangedPacket.send(player);
        }
    }

    @Override public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player
                && com.chaosthedude.explorerscompass.unlocking.UnlockReloads.ready(player.getUuid())) validateTarget(player, stack);
    }

    private boolean holdsSession(PlayerEntity player, SearchSession session) {
        for (int i = 0; i < player.getInventory().size(); i++)
            if (player.getInventory().getStack(i) == session.stack && !session.stack.isEmpty()) return true;
        return false;
    }

    public void stopAllSearches() {
        for (UUID owner : List.copyOf(searches.keySet())) stopSearch(owner, searches.get(owner));
    }

    public void tickSearchOwnership(net.minecraft.server.MinecraftServer server) {
        for (UUID owner : List.copyOf(searches.keySet())) {
            SearchSession session = searches.get(owner);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
            if (player == null || !holdsSession(player, session)) stopDetachedSearch(owner, session);
        }
    }

    public void revalidateAccess(ServerPlayerEntity player) {
        SearchSession session = searches.get(player.getUuid());
        if (session != null && !(session.creativeBypass && player.getAbilities().creativeMode)
                && session.eligibleIds.stream().noneMatch(id -> UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), id))) {
            stopDetachedSearch(player.getUuid(), session);
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            validateTarget(player, player.getInventory().getStack(slot));
        }
    }

    private void stopDetachedSearch(UUID owner, SearchSession session) {
        stopSearch(owner, session);
        ServerPlayerEntity player = session.world.getServer().getPlayerManager().getPlayer(owner);
        if (player != null) SearchResultPacket.send(player, session.requestId, SearchResultPacket.REJECTED, 0);
    }

    private void stopSearch(UUID owner, SearchSession session) {
        session.workers.stop();
        session.workers.clear();
        searches.remove(owner);
        if (session.stack.hasNbt()) CompassSearchData.restore(session.stack.getNbt(), session.token);
    }


	public static final String NAME = "explorerscompass";

	private final Map<UUID, SearchSession> searches = new HashMap<>();

    private static class SearchSession {
        final SearchWorkerManager workers = new SearchWorkerManager();
        final ItemStack stack;
        final NbtCompound previousNbt;
        final UUID token = UUID.randomUUID();
        final int requestId;
        final ServerWorld world;
        final List<Identifier> eligibleIds;
		final boolean creativeBypass;
        AvailabilitySelection availability;
        final int radiusLimit = ExplorersCompassConfig.maxSearchRadius;
        SearchSession(ServerWorld world, ItemStack stack, int requestId, List<Identifier> eligibleIds, boolean creativeBypass) {
            this.world = world;
            this.stack = stack;
            this.previousNbt = CompassSearchData.snapshot(stack.getOrCreateNbt());
            this.requestId = requestId;
            this.eligibleIds = List.copyOf(eligibleIds);
			this.creativeBypass = creativeBypass;
        }
    }

    private SearchSession beginSearch(ServerWorld world, PlayerEntity player, ItemStack stack, int requestId, List<Identifier> eligibleIds, boolean creativeBypass) {
        cancelSearch(player, -1);
        ensureHolder((ServerPlayerEntity) player, stack);
        SearchSession session = new SearchSession(world, stack, requestId, eligibleIds, creativeBypass);
        CompassSearchData.begin(stack.getOrCreateNbt(), session.token, session.previousNbt);
        searches.put(player.getUuid(), session);
        return session;
    }

    public void cancelSearch(PlayerEntity player, int requestId) {
        SearchSession session = searches.get(player.getUuid());
        if (session != null && (requestId == -1 || session.requestId == requestId)) {
            stopSearch(player.getUuid(), session);
        }
    }

	public void clearCompassTarget(ItemStack stack) {
		clearSearchData(stack);
	}

    private void finishSearch(PlayerEntity player, int result, int radius) {
        finishSearch(player, result, radius, null, 0, 0, false, false, List.of(), 0, null);
    }

    private void finishSearch(PlayerEntity player, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, int validBiomeMask, Boolean visited) {
        SearchSession session = searches.remove(player.getUuid());
        if (session != null) {
            session.workers.stop();
            session.workers.clear();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                SearchResultPacket.send(serverPlayer, session.requestId, result, radius, foundId, foundX, foundZ, displayCoordinates, displayDistance, biomes, validBiomeMask, visited);
            }
        }
    }


	private boolean rejectIfInsufficientXp(PlayerEntity player, int requestId) {
		int cost = ExplorersCompassConfig.defaultXpCost;
		if (!ExplorersCompassConfig.allowSearchWithoutEnoughXp && !player.getAbilities().creativeMode && cost > player.experienceLevel) {
			SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.INSUFFICIENT_XP, cost);
			return true;
		}
		return false;
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
			cancelSearch(player, -1);
			clearSearchData(stack);
			return TypedActionResult.pass(stack);
		}
		if (isBroken(stack)) {
			return TypedActionResult.pass(stack);
		}

		if (world.isClient) {
			GuiWrapper.openGUI(world, player, stack);
		} else {
			syncScreen((ServerPlayerEntity) player);
		}
		return TypedActionResult.success(stack, world.isClient);
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
				hasInfiniteXp, ExplorersCompassConfig.defaultXpCost, ExplorersCompassConfig.allowSearchWithoutEnoughXp,
				allowedStructures, dimensionsForAllowedStructures, structureIDsToSetIDs,
				locked, party,
                com.chaosthedude.explorerscompass.unlocking.UnlockedStructures.get(player.getServer()).noticeRevision(player.getUuid()),
                com.chaosthedude.explorerscompass.unlocking.UnlockedStructures.get(player.getServer()).noticeRelocked(player.getUuid()),
                getTrackedStructure(player),
                UnlockedStructures.get(player.getServer()).latestUnlocked(player.getUuid()),
                UnlockingRules.get().hasUnlockingRequirements(player),
                world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE).getIds().stream()
                        .filter(StructureUtils::structureIdIsBlacklisted).sorted().toList()));
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0) {
			int damage = getDamage(stack);
			return damage > 0;
		}
		return false;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0) {
			int damage = getDamage(stack);
			return Math.round(13.0f * (1.0f - (float) damage / max));
		}
		return 13;
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		int damage = getDamage(stack);
		float f = max > 0 ? (float) damage / max : 0.0f;
		return MathHelper.hsvToRgb(Math.max(0.0F, (1.0F - f) / 3.0F), 1.0F, 1.0F);
	}

    public void searchForStructure(ServerWorld world, PlayerEntity player, BlockPos pos, Identifier structureOrSetId, boolean isSet, boolean excludeVisited, boolean creativeUnlocksAll, ItemStack stack, int requestId) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        if (isSet || isBroken(stack)) {
            SearchResultPacket.send(serverPlayer, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        if (StructureUtils.structureIdIsBlacklisted(structureOrSetId)
                && StructureUtils.getStructureForID(world, structureOrSetId) != null) {
            SearchResultPacket.sendBlacklisted(serverPlayer, requestId, structureOrSetId);
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
        if (isBroken(stack) || requestedIds.isEmpty() || requestedIds.size() > 32767
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
        if (rejectIfInsufficientXp(player, requestId)) return;
        if (searchable.isEmpty()) {
            cancelSearch(player, -1);
            SearchResultPacket.send(player, requestId, SearchResultPacket.WRONG_DIMENSION, 0);
            return;
        }
        List<Structure> structures = includeSaved
                ? searchable.stream().map(id -> StructureUtils.getStructureForID(world, id)).toList() : List.of();
        List<Structure> generation = selection.eligible().stream().map(id -> StructureUtils.getStructureForID(world, id)).toList();
        Identifier firstId = searchable.get(0);
        SearchSession session = beginSearch(world, player, stack, requestId, searchable, creativeBypass);
        session.availability = includeSaved ? selection : null;
        setSearching(stack, firstId);
        SearchAvailabilityPacket.send(player, requestId, includeSaved
                ? new AvailabilitySelection(searchable, List.of(), 0, selection.locked()) : selection);
        session.workers.createWorkers(world, player, stack, generation, structures, firstId, excludeVisited, creativeBypass, pos);
        session.workers.setRadiusLimit(session.radiusLimit);
        if (session.workers.start()) {
            SearchProgressPacket.send(player, requestId, 0);
        } else {
            fail(player, stack, firstId, 0, 0);
        }
    }

    // Retain the packet entry point for older clients, but searches no longer have continuation stages.
    public void continueSearch(ServerWorld world, PlayerEntity player, ItemStack stack, int requestId) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            SearchResultPacket.send(serverPlayer, requestId, SearchResultPacket.REJECTED, 0);
        }
    }

	public void succeed(PlayerEntity player, ItemStack stack, Identifier structureID, ChunkPos startChunk, int x, int z, BlockBox bounds, int samples, boolean displayCoordinates, boolean displayDistance) {
        PartySharing.synchronize(player.getServer());
		SearchSession session = searches.get(player.getUuid());
		if (session == null || session.stack != stack || structureID == null) return;
        if (!holdsSession(player, session)) { stopDetachedSearch(player.getUuid(), session); return; }
		if (!(session.creativeBypass && player.getAbilities().creativeMode)
				&& !UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), structureID)) {
			cancelSearch(player, session.requestId);
			SearchResultPacket.send((ServerPlayerEntity) player, session.requestId, SearchResultPacket.REJECTED, 0);
			return;
		}
		int xpLevels = ExplorersCompassConfig.defaultXpCost;
		if (!ExplorersCompassConfig.allowSearchWithoutEnoughXp && !player.getAbilities().creativeMode && xpLevels > player.experienceLevel) {
			cancelSearch(player, session.requestId);
			SearchResultPacket.send((ServerPlayerEntity) player, session.requestId, SearchResultPacket.INSUFFICIENT_XP, xpLevels);
			return;
		}
		if (!player.getAbilities().creativeMode && xpLevels > 0) player.addExperienceLevels(-xpLevels);
		clearSearchData(stack);
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
			if (session.creativeBypass) stack.getNbt().putBoolean("CreativeBypass", true);
			stack.getNbt().putInt("State", CompassState.FOUND.getID());
			stack.getNbt().putInt("FoundX", x);
			stack.getNbt().putInt("FoundZ", z);
			stack.getNbt().putString("FoundDimension", player.getWorld().getRegistryKey().getValue().toString());
            stack.getNbt().putLong(CompassSearchData.FOUND_START_CHUNK, startChunk.toLong());
			stack.getNbt().putInt("Samples", samples);
			stack.getNbt().putBoolean("DisplayCoordinates", displayCoordinates);
			stack.getNbt().putBoolean("DisplayDistance", displayDistance);
		}
		damageCompass(stack);
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
		finishSearch(player, SearchResultPacket.FOUND, 0, structureID, x, z, displayCoordinates, displayDistance, biomes, validBiomeMask, visited);
	}

	public void fail(PlayerEntity player, ItemStack stack, Identifier structureId, int radius, int samples) {
        SearchSession session = searches.get(player.getUuid());
        if (session == null) return;
        if (!holdsSession(player, session)) { stopDetachedSearch(player.getUuid(), session); return; }
        session.workers.pop();
        boolean started = session.workers.start();
		if (!started) {
			CompassSearchData.restore(stack.getOrCreateNbt(), session.previousNbt);
            if (session.availability != null && session.availability.eligible().isEmpty()) {
                var selection = session.availability;
                if (!selection.disabled().isEmpty() && player instanceof ServerPlayerEntity serverPlayer
                        && ServerPlayNetworking.canSend(serverPlayer, SearchAvailabilityPacket.ID)) {
                    cancelSearch(player, session.requestId);
                    SearchAvailabilityPacket.send(serverPlayer, session.requestId, selection);
                    return;
                }
                if (selection.wrongDimension() > 0) {
                    finishSearch(player, SearchResultPacket.WRONG_DIMENSION, 0);
                    return;
                }
            }
            if (session.availability != null && player instanceof ServerPlayerEntity serverPlayer) {
                var original = session.availability;
                SearchAvailabilityPacket.send(serverPlayer, session.requestId, original);
            }
            finishSearch(player, SearchResultPacket.NOT_FOUND, session.radiusLimit);
		}
	}

	public boolean isActive(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return getState(stack) != CompassState.INACTIVE;
		}

		return false;
	}

	public void setSearching(ItemStack stack, Identifier structureID) {
		if (ItemUtils.verifyNBT(stack)) {
            stack.getNbt().remove(CompassTransfer.INHERITED);
			stack.getNbt().putString("StructureID", structureID.toString());
			stack.getNbt().putInt("State", CompassState.SEARCHING.getID());
			stack.getNbt().putInt("SearchRadius", 0);
			stack.getNbt().putInt("Samples", 0);
		}
	}

	public void setFound(ItemStack stack, Identifier structureID, int x, int z, int samples) {
		clearSearchData(stack);
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
			stack.getNbt().putInt("State", CompassState.FOUND.getID());
			stack.getNbt().putInt("FoundX", x);
			stack.getNbt().putInt("FoundZ", z);
			stack.getNbt().putInt("Samples", samples);
		}
	}

	public void setNotFound(ItemStack stack, int searchRadius, int samples) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("State", CompassState.NOT_FOUND.getID());
			stack.getNbt().putInt("SearchRadius", searchRadius);
			stack.getNbt().putInt("Samples", samples);
		}
	}

	public void setInactive(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("State", CompassState.INACTIVE.getID());
		}
	}

	public void setState(ItemStack stack, BlockPos pos, CompassState state) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("State", state.getID());
		}
	}

	public void setFoundStructureX(ItemStack stack, int x) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("FoundX", x);
		}
	}

	public void setFoundStructureZ(ItemStack stack, int z) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("FoundZ", z);
		}
	}

	public void setStructureKey(ItemStack stack, Identifier structureID) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putString("StructureID", structureID.toString());
		}
	}

    public void reportSearchProgress(PlayerEntity player, ItemStack stack, int radius) {
        SearchSession session = searches.get(player.getUuid());
        if (session == null || session.stack != stack) return;
        int previousRadius = getSearchRadius(stack);
        setSearchRadius(stack, radius);
        int updatedRadius = getSearchRadius(stack);
        // Multiple placement workers can report smaller distances; only send a new high-water mark.
        if (updatedRadius > previousRadius && player instanceof ServerPlayerEntity serverPlayer) {
            SearchProgressPacket.send(serverPlayer, session.requestId, updatedRadius);
        }
    }

	public void setSearchRadius(ItemStack stack, int searchRadius) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("SearchRadius", Math.max(stack.getNbt().getInt("SearchRadius"), searchRadius));
		}
	}

	public void setSamples(ItemStack stack, int samples) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putInt("Samples", samples);
		}
	}

	public void setDisplayCoordinates(ItemStack stack, boolean displayPosition) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putBoolean("DisplayCoordinates", displayPosition);
		}
	}

	public void setDisplayDistance(ItemStack stack, boolean displayDistance) {
		if (ItemUtils.verifyNBT(stack)) {
			stack.getNbt().putBoolean("DisplayDistance", displayDistance);
		}
	}

	public CompassState getState(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return CompassState.fromID(stack.getNbt().getInt("State"));
		}

		return null;
	}

	public int getFoundStructureX(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("FoundX");
		}

		return 0;
	}

	public int getFoundStructureZ(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("FoundZ");
		}

		return 0;
	}

	public Identifier getFoundDimension(ItemStack stack) {
		if (!ItemUtils.verifyNBT(stack)) return null;
		if (stack.getNbt().contains("FoundDimension")) return Identifier.tryParse(stack.getNbt().getString("FoundDimension"));
		Identifier structureId = Identifier.tryParse(stack.getNbt().getString("StructureID"));
		if (structureId == null || ExplorersCompass.allowedStructureIDsToDimensionIDs == null) return null;
		List<Identifier> dimensions = ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureId);
		return dimensions.stream().distinct().count() == 1 ? dimensions.get(0) : null;
	}

	public boolean isInFoundDimension(ItemStack stack, World world) {
		Identifier dimension = getFoundDimension(stack);
		return dimension == null || dimension.equals(world.getRegistryKey().getValue());
	}

	public Identifier getStructureID(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return new Identifier(stack.getNbt().getString("StructureID"));
		}

		return new Identifier("");
	}

	public int getSearchRadius(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("SearchRadius");
		}

		return -1;
	}

	public int getSamples(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack)) {
			return stack.getNbt().getInt("Samples");
		}

		return -1;
	}

	public int getDistanceToBiome(PlayerEntity player, ItemStack stack) {
		return StructureUtils.getHorizontalDistanceToLocation(player, getFoundStructureX(stack), getFoundStructureZ(stack));
	}

	public boolean shouldDisplayCoordinates(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack) && stack.getNbt().contains("DisplayCoordinates")) {
			return stack.getNbt().getBoolean("DisplayCoordinates");
		}

		return true;
	}

	public boolean shouldDisplayDistance(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack) && stack.getNbt().contains("DisplayDistance")) {
			return stack.getNbt().getBoolean("DisplayDistance");
		}

		return shouldDisplayCoordinates(stack);
	}

	public int getDamage(ItemStack stack) {
		if (ItemUtils.verifyNBT(stack) && stack.getNbt().contains("CompassDamage")) {
			return stack.getNbt().getInt("CompassDamage");
		}
		return 0;
	}

	public boolean isBroken(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0) {
			return getDamage(stack) >= max;
		}
		return false;
	}

	private void damageCompass(ItemStack stack) {
		int max = ExplorersCompassConfig.compassDurability;
		if (max > 0 && ItemUtils.verifyNBT(stack)) {
			int damage = getDamage(stack) + 1;
			stack.getNbt().putInt("CompassDamage", damage);
		}
	}

	private void clearSearchData(ItemStack stack) {
        if (ItemUtils.verifyNBT(stack)) CompassSearchData.clear(stack.getNbt());
    }

}
