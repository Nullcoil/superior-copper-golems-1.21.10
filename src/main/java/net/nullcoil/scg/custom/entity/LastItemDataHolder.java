package net.nullcoil.scg.custom.entity;

import net.minecraft.world.item.ItemStack;

public interface LastItemDataHolder
{
    ItemStack getLastItemStack();

    void setLastItemStack(ItemStack lastItemStack);
}
