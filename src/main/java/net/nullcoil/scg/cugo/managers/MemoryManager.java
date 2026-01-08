package net.nullcoil.scg.cugo.managers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.HashMap;
import java.util.Map;

public class MemoryManager {

    // Map BlockPos -> A snapshot of the items inside
    private final Map<BlockPos, NonNullList<ItemStack>> chestMemory = new HashMap<>();

    public MemoryManager() {}

    /**
     * Callable Method: Adds or Updates a memory of a chest.
     * @param pos The position of the container.
     * @param contents The list of items currently inside.
     */
    public void rememberChest(BlockPos pos, NonNullList<ItemStack> contents) {
        // We create a DEEP COPY of the list.
        // If we don't copy, the memory will point to the live items in the chest,
        // effectively giving the golem omnificence (instant updates).
        // We want him to rely on his memory, even if it's outdated.
        NonNullList<ItemStack> snapshot = NonNullList.withSize(contents.size(), ItemStack.EMPTY);

        for (int i = 0; i < contents.size(); i++) {
            snapshot.set(i, contents.get(i).copy());
        }

        // Put (or replace) the memory
        chestMemory.put(pos, snapshot);

        System.out.println("Cugo Memory: Updated memory for chest at " + pos + ". Items remembered: " + countItems(snapshot));
    }

    /**
     * Callable Method: Checks if a remembered chest still exists.
     * If the block is broken or no longer a container, the memory is wiped.
     * @param level The level to check in.
     * @param pos The position to check.
     */
    public void validateMemory(Level level, BlockPos pos) {
        if (!chestMemory.containsKey(pos)) return;

        boolean isValid = false;

        // simple check: does the block entity still exist and is it a container?
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RandomizableContainerBlockEntity) {
            isValid = true;
        }

        if (!isValid) {
            chestMemory.remove(pos);
            System.out.println("Cugo Memory: Chest at " + pos + " is broken or missing. Memory wiped.");
        }
    }

    /**
     * Helper to retrieve memory (for future behaviors)
     */
    public NonNullList<ItemStack> getMemoryAt(BlockPos pos) {
        return chestMemory.get(pos);
    }

    /**
     * Debug helper to count total items in a list
     */
    private int countItems(NonNullList<ItemStack> list) {
        int count = 0;
        for (ItemStack stack : list) {
            if (!stack.isEmpty()) count += stack.getCount();
        }
        return count;
    }
}