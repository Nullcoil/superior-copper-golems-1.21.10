package net.nullcoil.scg.cugo.managers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.scg.util.ModTags;

import java.util.*;

public class MemoryManager {

    private final Map<BlockPos, NonNullList<ItemStack>> knownChests = new HashMap<>();
    private final Set<BlockPos> seenChests = new HashSet<>();

    public MemoryManager() {}

    // --- NBT PERSISTENCE (CODEC EDITION) ---

    public CompoundTag createTag(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();

        // 1. Save SEEN Chests
        if (!seenChests.isEmpty()) {
            long[] seenArray = seenChests.stream().mapToLong(BlockPos::asLong).toArray();
            nbt.putLongArray("CugoSeenChests", seenArray);
        }

        // 2. Save KNOWN Chests
        if (!knownChests.isEmpty()) {
            ListTag knownList = new ListTag();

            // Create a Registry-Aware Ops context for saving Items
            var registryOps = provider.createSerializationContext(NbtOps.INSTANCE);

            for (Map.Entry<BlockPos, NonNullList<ItemStack>> entry : knownChests.entrySet()) {
                CompoundTag chestTag = new CompoundTag();
                chestTag.putLong("Pos", entry.getKey().asLong());

                ListTag itemList = new ListTag();
                NonNullList<ItemStack> stacks = entry.getValue();

                for (int i = 0; i < stacks.size(); i++) {
                    ItemStack stack = stacks.get(i);
                    if (!stack.isEmpty()) {
                        // Use CODEC to save item
                        Tag itemTag = ItemStack.CODEC.encodeStart(registryOps, stack)
                                .getOrThrow(e -> new IllegalStateException("Failed to encode item: " + e));

                        // We wrap it in a compound to store the slot index
                        CompoundTag slotTag = new CompoundTag();
                        slotTag.putByte("Slot", (byte)i);
                        slotTag.put("ItemData", itemTag);

                        itemList.add(slotTag);
                    }
                }
                chestTag.put("Items", itemList);
                knownList.add(chestTag);
            }
            nbt.put("CugoKnownChests", knownList);
        }

        return nbt;
    }

    public void loadTag(CompoundTag nbt, HolderLookup.Provider provider) {
        knownChests.clear();
        seenChests.clear();

        // Create a Registry-Aware Ops context for loading Items
        var registryOps = provider.createSerializationContext(NbtOps.INSTANCE);

        // 1. Load SEEN Chests (Handle Optional)
        nbt.getLongArray("CugoSeenChests").ifPresent(seenArray -> {
            for (long l : seenArray) {
                seenChests.add(BlockPos.of(l));
            }
        });

        // 2. Load KNOWN Chests (Handle Optional)
        nbt.getList("CugoKnownChests").ifPresent(knownList -> {
            for (int i = 0; i < knownList.size(); i++) {
                // Get Compound safely
                knownList.getCompound(i).ifPresent(chestTag -> {

                    long posLong = chestTag.getLong("Pos").orElse(0L);
                    BlockPos pos = BlockPos.of(posLong);

                    // Init list with air
                    NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

                    // Load Items
                    chestTag.getList("Items").ifPresent(itemList -> {
                        for (int j = 0; j < itemList.size(); j++) {
                            itemList.getCompound(j).ifPresent(slotTag -> {
                                int slot = slotTag.getByte("Slot").orElse((byte)0) & 255;

                                if (slot >= 0 && slot < items.size() && slotTag.contains("ItemData")) {
                                    Tag itemData = slotTag.get("ItemData");
                                    if (itemData != null) {
                                        // Use CODEC to parse item
                                        ItemStack.CODEC.parse(registryOps, itemData)
                                                .result()
                                                .ifPresent(stack -> items.set(slot, stack));
                                    }
                                }
                            });
                        }
                    });

                    knownChests.put(pos, items);
                });
            }
        });
    }

    // --- STANDARD METHODS (Unchanged) ---

    public void markAsSeen(BlockPos pos) {
        if (!knownChests.containsKey(pos) && !seenChests.contains(pos)) {
            seenChests.add(pos);
        }
    }

    public void updateMemory(BlockPos pos, NonNullList<ItemStack> contents) {
        seenChests.remove(pos);
        NonNullList<ItemStack> snapshot = NonNullList.withSize(contents.size(), ItemStack.EMPTY);
        for (int i = 0; i < contents.size(); i++) {
            snapshot.set(i, contents.get(i).copy());
        }
        knownChests.put(pos, snapshot);
    }

    public BlockPos findChestWithItem(Level level, ItemStack itemToMatch, BlockPos currentPos, Set<BlockPos> ignoreList) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, NonNullList<ItemStack>> entry : knownChests.entrySet()) {
            BlockPos pos = entry.getKey();
            if (ignoreList.contains(pos)) continue;
            if (!isValidOutput(level, pos)) continue;

            NonNullList<ItemStack> contents = entry.getValue();
            if (containsItem(contents, itemToMatch)) {
                if (canFitMore(contents, itemToMatch)) {
                    double dist = pos.distSqr(currentPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPos = pos;
                    }
                }
            }
        }
        return bestPos;
    }

    public BlockPos findEmptyChest(Level level, BlockPos currentPos, Set<BlockPos> ignoreList) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, NonNullList<ItemStack>> entry : knownChests.entrySet()) {
            BlockPos pos = entry.getKey();
            if (ignoreList.contains(pos)) continue;
            if (!isValidOutput(level, pos)) continue;

            NonNullList<ItemStack> contents = entry.getValue();
            if (isEmpty(contents)) {
                double dist = pos.distSqr(currentPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = pos;
                }
            }
        }
        return bestPos;
    }

    public BlockPos getNearestSeenChest(Level level, BlockPos currentPos, Set<BlockPos> ignoreList) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : seenChests) {
            if (ignoreList.contains(pos)) continue;
            if (!isValidOutput(level, pos)) continue;

            double dist = pos.distSqr(currentPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = pos;
            }
        }
        return bestPos;
    }

    private boolean isValidOutput(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        return level.getBlockState(pos).is(ModTags.Blocks.CUGO_CONTAINER_OUTPUTS);
    }
    private boolean containsItem(NonNullList<ItemStack> contents, ItemStack match) {
        for (ItemStack stack : contents) if (ItemStack.isSameItemSameComponents(stack, match)) return true;
        return false;
    }
    private boolean canFitMore(NonNullList<ItemStack> contents, ItemStack match) {
        for (ItemStack stack : contents) {
            if (ItemStack.isSameItemSameComponents(stack, match) && stack.getCount() < stack.getMaxStackSize()) return true;
            if (stack.isEmpty()) return true;
        }
        return false;
    }
    private boolean isEmpty(NonNullList<ItemStack> contents) {
        for (ItemStack stack : contents) if (!stack.isEmpty()) return false;
        return true;
    }
    public boolean hasMemory(BlockPos pos) {
        return knownChests.containsKey(pos) || seenChests.contains(pos);
    }
}