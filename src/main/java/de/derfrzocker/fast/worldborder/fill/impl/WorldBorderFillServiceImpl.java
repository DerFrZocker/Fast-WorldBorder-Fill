package de.derfrzocker.fast.worldborder.fill.impl;

import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillService;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillSetting;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class WorldBorderFillServiceImpl implements WorldBorderFillService {

    private final JavaPlugin javaPlugin;

    public WorldBorderFillServiceImpl(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }

    @NotNull
    @Override
    public WorldBorderFillTask createWorldBorderFillTask(@NotNull WorldBorderFillSetting worldBorderFillSetting) {
        try {
            return new WorldBorderFillTask_v1_15_R1(javaPlugin, javaPlugin.getLogger(), worldBorderFillSetting);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unexpected error while creating WorldBorderFillTask", e);
        }
    }

}
