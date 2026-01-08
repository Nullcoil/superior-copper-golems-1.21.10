package net.nullcoil.scg.cugo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.nullcoil.scg.cugo.managers.NavigationController;
import org.jetbrains.annotations.NotNull;

public class CugoBrain implements CugoBehavior {

    private NavigationController navController;

    public CugoBrain() {
    }

    public void onAttach(@NotNull CopperGolem golem) {
        this.navController = new NavigationController(golem);
    }

    @Override
    public void tick(CopperGolem golem, @NotNull ServerLevel level) {
        // The Brain just keeps the heart beating
        if(navController != null) {
            navController.tick();
        }
    }
}