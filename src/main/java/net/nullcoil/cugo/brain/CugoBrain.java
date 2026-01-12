package net.nullcoil.cugo.brain;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.nullcoil.cugo.brain.managers.MemoryManager;
import net.nullcoil.cugo.brain.managers.NavigationController;
import org.jetbrains.annotations.NotNull;

public class CugoBrain implements CugoBehavior {

    private NavigationController navController;
    private MemoryManager memoryManager;

    public CugoBrain() {}

    public void onAttach(@NotNull CopperGolem golem) {
        this.memoryManager = new MemoryManager();
        this.navController = new NavigationController(golem);
        System.out.println("CugoBrain: Attached. MemoryManager initialized.");
    }

    @Override
    public void tick(CopperGolem golem, @NotNull ServerLevel level) {
        if(navController != null) {
            navController.tick();
        }
    }

    // --- NBT PASS-THROUGH ---

    public CompoundTag createMemoryTag(HolderLookup.Provider provider) {
        if (memoryManager != null) {
            return memoryManager.createTag(provider);
        }
        return new CompoundTag();
    }

    public void loadMemoryTag(CompoundTag nbt, HolderLookup.Provider provider) {
        if (memoryManager != null) {
            memoryManager.loadTag(nbt, provider);
        }
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }
    public NavigationController getNavigationController() {
        return navController;
    }
}