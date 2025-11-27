package net.nullcoil.scg.cugo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;

import java.util.ArrayList;
import java.util.List;

public class CugoController {
    private final List<CugoBehavior> behaviors = new ArrayList<>();

    public void add(CugoBehavior behavior) {
        behaviors.add(behavior);
    }

    public void tick(CopperGolem golem, ServerLevel level) {
        for(var b : behaviors) {
            b.tick(golem, level);
        }
    }
}
