package com.chaosthedude.explorerscompass.items;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashSet;
import com.chaosthedude.explorerscompass.util.CompassTransfer;
import com.chaosthedude.explorerscompass.util.CompassSearchData;
import com.chaosthedude.explorerscompass.unlocking.UnlockedStructures;
import com.chaosthedude.explorerscompass.unlocking.UnlockNotifications;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.gui.GuiWrapper;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.PlayerUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
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
import net.minecraft.util.math.BlockPos;
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
        if (!transfer.unlocks().isEmpty()) {
            int added = state.add(player.getUuid(), transfer.unlocks());
            UnlockNotifications.newlyAdded(player, added);
            if (added > 0) com.chaosthedude.explorerscompass.network.RulesChangedPacket.send(player);
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
	public static final int INITIAL_SEARCH_RADIUS = 10000;
	public static final int SEARCH_RADIUS_STEP = 20000;
	public static final int MAX_SEARCH_RADIUS = 50000;

	private final Map<UUID, SearchSession> searches = new HashMap<>();

    private static class SearchSession {
        final SearchWorkerManager workers = new SearchWorkerManager();
        final ItemStack stack;
        final NbtCompound previousNbt;
        final UUID token = UUID.randomUUID();
        final int requestId;
        final ServerWorld world;
        final Identifier searchId;
		final boolean creativeBypass;
        int radiusLimit = INITIAL_SEARCH_RADIUS;
        boolean awaitingContinuation;
        SearchSession(ServerWorld world, ItemStack stack, int requestId, Identifier searchId, boolean creativeBypass) {
            this.world = world;
            this.stack = stack;
            this.previousNbt = CompassSearchData.snapshot(stack.getOrCreateNbt());
            this.requestId = requestId;
            this.searchId = searchId;
			this.creativeBypass = creativeBypass;
        }
    }

    private SearchSession beginSearch(ServerWorld world, PlayerEntity player, ItemStack stack, int requestId, Identifier searchId, boolean creativeBypass) {
        cancelSearch(player, -1);
        ensureHolder((ServerPlayerEntity) player, stack);
        SearchSession session = new SearchSession(world, stack, requestId, searchId, creativeBypass);
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

	private void rejectWrongDimension(PlayerEntity player, int requestId) {
		cancelSearch(player, -1);
		SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.WRONG_DIMENSION, 0);
	}

    private void finishSearch(PlayerEntity player, int result, int radius) {
        finishSearch(player, result, radius, null, 0, 0, false, false);
    }

    private void finishSearch(PlayerEntity player, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance) {
        SearchSession session = searches.remove(player.getUuid());
        if (session != null) {
            session.workers.stop();
            session.workers.clear();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                SearchResultPacket.send(serverPlayer, session.requestId, result, radius, foundId, foundX, foundZ, displayCoordinates, displayDistance);
            }
        }
    }

    private List<Identifier> searchableInDimension(ServerWorld world, List<Identifier> ids) {
        List<Identifier> allowed = StructureUtils.getAllowedStructureIDs(world);
        List<Identifier> searchable = new ArrayList<>();
        for (Identifier id : ids) {
            Structure structure = StructureUtils.getStructureForID(world, id);
            if (allowed.contains(id) && structure != null && StructureUtils.getGeneratingDimensionIDs(world, structure).contains(world.getRegistryKey().getValue())) {
                searchable.add(id);
            }
        }
        return searchable;
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
        if (!world.isClient) ensureHolder((ServerPlayerEntity) player, player.getStackInHand(hand));
		if (!player.isSneaking()) {
			if (isBroken(player.getStackInHand(hand))) {
				return TypedActionResult.pass(player.getStackInHand(hand));
			}

			if (world.isClient) {
				final ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
				GuiWrapper.openGUI(world, player, stack);
			} else {
				syncScreen((ServerPlayerEntity) player);
			}
		} else {
			cancelSearch(player, -1);
			ItemStack stack = player.getStackInHand(hand);
			clearSearchData(stack);
		}

		return TypedActionResult.pass(player.getStackInHand(hand));
	}

	/** Re-evaluate and synchronize the compass when opened or returned to from the Quest Book. */
	public void syncScreen(ServerPlayerEntity player) {
        ItemStack held = ItemUtils.getHeldItem(player, this);
        if (!held.isEmpty()) validateTarget(player, held);
		ServerWorld world = player.getServerWorld();
		UnlockingRules.get().evaluateOnOpen(player);
        com.chaosthedude.explorerscompass.unlocking.PartySharing.synchronize(player.getServer());
		boolean canTeleport = PlayerUtils.canTeleport(player);
		boolean hasInfiniteXp = player.getAbilities().creativeMode;
		List<Identifier> allowedStructures = StructureUtils.getAllowedStructureIDs(world);
		ListMultimap<Identifier, Identifier> dimensionsForAllowedStructures = StructureUtils.getGeneratingDimensionIDsForAllowedStructures(world, allowedStructures);
		Map<Identifier, Identifier> structureIDsToGroupIDs = StructureUtils.getStructureIDsToGroupIDs(world);
		ServerPlayNetworking.send(player, SyncPacket.ID, new SyncPacket(canTeleport, ExplorersCompassConfig.allowExcludeVisited,
				hasInfiniteXp, ExplorersCompassConfig.defaultXpCost, ExplorersCompassConfig.allowSearchWithoutEnoughXp,
				allowedStructures, dimensionsForAllowedStructures, structureIDsToGroupIDs,
				UnlockingRules.get().lockedRequirements(player, allowedStructures),
                com.chaosthedude.explorerscompass.unlocking.UnlockedStructures.get(player.getServer()).noticeRevision(player.getUuid()),
                com.chaosthedude.explorerscompass.unlocking.UnlockedStructures.get(player.getServer()).noticeRelocked(player.getUuid()),
                getTrackedStructure(player)));
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

	public void searchForStructure(ServerWorld world, PlayerEntity player, BlockPos pos, Identifier structureOrGroupId, boolean isGroup, boolean excludeVisited, boolean creativeUnlocksAll, ItemStack stack, int requestId) {
        ensureHolder((ServerPlayerEntity) player, stack);
        boolean creativeBypass = creativeUnlocksAll && player.getAbilities().creativeMode;
        if (isGroup || (!creativeBypass && !UnlockingRules.isUnlocked(world.getServer(), player.getUuid(), structureOrGroupId))) {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
		if (!isBroken(stack)) {
            List<Identifier> structureIds = List.of(structureOrGroupId);

			List<Structure> structures = new ArrayList<Structure>();
            structureIds = searchableInDimension(world, structureIds);
            if (structureIds.isEmpty()) {
				rejectWrongDimension(player, requestId);
                return;
            }
			if (rejectIfInsufficientXp(player, requestId)) return;
            SearchSession session = beginSearch(world, player, stack, requestId, structureOrGroupId, creativeBypass);
            setSearching(stack, structureOrGroupId);
            for (Identifier id : structureIds) {
				structures.add(StructureUtils.getStructureForID(world, id));
			}
			session.workers.createWorkers(world, player, stack, structures, structureOrGroupId, excludeVisited, creativeBypass, pos);
			session.workers.setRadiusLimit(session.radiusLimit);
			boolean started = session.workers.start();
			if (!started) {
				fail(player, stack, structureOrGroupId, 0, 0);
			}

		} else {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
        }
	}

    public void searchForListedStructures(ServerWorld world, PlayerEntity player, BlockPos pos, List<Identifier> requestedIds, boolean excludeVisited, boolean creativeUnlocksAll, ItemStack stack, int requestId) {
        ensureHolder((ServerPlayerEntity) player, stack);
        if (isBroken(stack) || requestedIds.isEmpty() || requestedIds.size() > 32767) {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        List<Identifier> allowed = StructureUtils.getAllowedStructureIDs(world);
        if (!allowed.containsAll(requestedIds)) {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        List<Identifier> uniqueIds = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        boolean creativeBypass = creativeUnlocksAll && player.getAbilities().creativeMode;
        if (!creativeBypass) uniqueIds.removeIf(id -> !UnlockingRules.isUnlocked(world.getServer(), player.getUuid(), id));
        if (uniqueIds.isEmpty()) {
            SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
            return;
        }
        List<Identifier> searchable = searchableInDimension(world, uniqueIds);
        if (searchable.size() != uniqueIds.size()) {
            rejectWrongDimension(player, requestId);
            return;
        }
		if (rejectIfInsufficientXp(player, requestId)) return;
        List<Structure> structures = new ArrayList<>();
        for (Identifier id : searchable) structures.add(StructureUtils.getStructureForID(world, id));
        Identifier firstId = searchable.get(0);
        SearchSession session = beginSearch(world, player, stack, requestId, firstId, creativeBypass);
        setSearching(stack, firstId);
		session.workers.createWorkers(world, player, stack, structures, firstId, excludeVisited, creativeBypass, pos);
        session.workers.setRadiusLimit(session.radiusLimit);
        if (!session.workers.start()) fail(player, stack, firstId, 0, 0);
    }

	public void continueSearch(ServerWorld world, PlayerEntity player, ItemStack stack, int requestId) {
        ensureHolder((ServerPlayerEntity) player, stack);
		SearchSession session = searches.get(player.getUuid());
		if (session == null || !session.awaitingContinuation || session.requestId != requestId
				|| session.stack != stack || isBroken(stack) || session.radiusLimit >= MAX_SEARCH_RADIUS) {
			SearchResultPacket.send((ServerPlayerEntity) player, requestId, SearchResultPacket.REJECTED, 0);
			return;
		}
		if (session.world != world) {
			rejectWrongDimension(player, requestId);
			return;
		}
		if (rejectIfInsufficientXp(player, requestId)) {
			cancelSearch(player, requestId);
			return;
		}
        CompassSearchData.begin(stack.getOrCreateNbt(), session.token, session.previousNbt);
		session.awaitingContinuation = false;
		int previousRadiusLimit = session.radiusLimit;
		session.radiusLimit = Math.min(MAX_SEARCH_RADIUS, session.radiusLimit + SEARCH_RADIUS_STEP);
		session.workers.setRadiusLimit(session.radiusLimit);
		setSearching(stack, session.searchId);
		setSearchRadius(stack, previousRadiusLimit);
		if (!session.workers.start()) fail(player, stack, session.searchId, 0, 0);
	}

	public void succeed(PlayerEntity player, ItemStack stack, Identifier structureID, int x, int z, int samples, boolean displayCoordinates, boolean displayDistance) {
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
			stack.getNbt().putInt("Samples", samples);
			stack.getNbt().putBoolean("DisplayCoordinates", displayCoordinates);
			stack.getNbt().putBoolean("DisplayDistance", displayDistance);
		}
		damageCompass(stack);
		finishSearch(player, SearchResultPacket.FOUND, 0, structureID, x, z, displayCoordinates, displayDistance);
	}

	public void fail(PlayerEntity player, ItemStack stack, Identifier structureId, int radius, int samples) {
        SearchSession session = searches.get(player.getUuid());
        if (session == null || session.awaitingContinuation) return;
        if (!holdsSession(player, session)) { stopDetachedSearch(player.getUuid(), session); return; }
        session.workers.pop();
        boolean started = session.workers.start();
		if (!started) {
			CompassSearchData.restore(stack.getOrCreateNbt(), session.previousNbt);
			boolean canContinue = session.radiusLimit < MAX_SEARCH_RADIUS;
			if (canContinue) {
				session.awaitingContinuation = true;
				SearchResultPacket.send((ServerPlayerEntity) player, session.requestId, SearchResultPacket.NOT_FOUND, session.radiusLimit, true);
			} else {
				finishSearch(player, SearchResultPacket.NOT_FOUND, session.radiusLimit);
			}
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
