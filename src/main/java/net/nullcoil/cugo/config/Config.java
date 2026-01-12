package net.nullcoil.cugo.config;

@SuppressWarnings("unused")
public class Config
{
    private final String _comment = "Download mod menu for better config experience" +
            " and option explanations with translation";

    public int maxStackSize = 64;

    public int yInteractRange = 5;   // for interacting with containers
    public int xzInteractRange = 1;  // for interacting with containers
    public int horizontalRange = 16; // for pathfinding and finding chests
    public int verticalRange = 5;    // for pathfinding and finding chests
    public int wanderDuration = 3;   // length of random wander
    public int searchDuration = 30;
    public int pingLineDensity = 2;

    public boolean rechargeableStatues = true;
    public boolean redstoneBoost = true;

    public boolean debugMode = true;

    public Config() {}
}
