package net.nullcoil.scg.cugo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.nullcoil.scg.cugo.managers.MemoryManager;
import net.nullcoil.scg.cugo.managers.NavigationController;
import org.jetbrains.annotations.NotNull;

public class CugoBrain implements CugoBehavior {

    private NavigationController navController;
    private MemoryManager memoryManager; // New Memory System

    public CugoBrain() {
    }

    public void onAttach(@NotNull CopperGolem golem) {
        // Initialize managers
        this.memoryManager = new MemoryManager();
        this.navController = new NavigationController(golem);

        System.out.println("CugoBrain: Attached. MemoryManager initialized.");
    }

    @Override
    public void tick(CopperGolem golem, @NotNull ServerLevel level) {
        // The Brain just keeps the heart beating
        if(navController != null) {
            navController.tick();
        }
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public NavigationController getNavigationController() {
        return navController;
    }
}