package net.nullcoil.scg.cugo;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CugoChestMemory {
    private final CopperGolem golem;
    private final BlockPos chestPos;
    private final BlockPos openPos;
    private final List<ItemStack> inventory = new ArrayList<>();
    private final boolean isCopper;

    public CugoChestMemory(CopperGolem golem, BlockPos chestPos, BlockPos openLoc) {
        this.golem = golem;
        this.chestPos = chestPos;
        this.openPos = openLoc;
        this.isCopper = golem.level().getBlockState(chestPos).is(BlockTags.COPPER_CHESTS);
        refreshMemory();
    }

    public CugoChestMemory(CopperGolem golem, BlockPos chestPos) {
        this(golem, chestPos, golem.getOnPos());
    }

    public BlockPos getChestPos() {
        return this.chestPos;
    }

    public BlockPos getOpenPos() {
        return this.openPos;
    }

    public void refreshMemory() {
        if(golem.level().getBlockEntity(chestPos) instanceof Container c) {
            inventory.clear();
            for(int i = 0; i < c.getContainerSize(); i++) {
                ItemStack slot = c.getItem(i);
                inventory.add(slot.copy());
            }
        }
    }

    public List<ItemStack> getInventory() {
        return inventory;
    }

    public boolean rememberedHasItem(Item item) {
        for(ItemStack stack : inventory) if(!stack.isEmpty() && stack.is(item)) return true;
        return false;
    }

    public void decrementRememberedItem(Item item) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if(!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                if(stack.isEmpty()) {
                    inventory.set(i, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    public void incrementRememberedItem(ItemStack added) {
        Item item = added. getItem();
        int count = added.getCount();

        for(ItemStack stack : inventory) {
            if(!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, added)) {
                int transferable = Math.min(count, stack.getMaxStackSize() - stack.getCount());
                if(transferable > 0) {
                    stack.grow(transferable);
                    count-=transferable;
                    if(count<=0) return;
                }
            }
        }
    }
}
