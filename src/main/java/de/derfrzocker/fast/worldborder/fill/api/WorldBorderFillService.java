package de.derfrzocker.fast.worldborder.fill.api;

import org.jetbrains.annotations.NotNull;

public interface WorldBorderFillService {

    /**
     * Create a new WorldBorderFillTask with the given WorldBorderFillSetting
     *
     * @param worldBorderFillSetting to use
     * @return a new WorldBorderFillTask task
     * @throws IllegalArgumentException if worldBorderFillSetting is null
     */
    @NotNull
    WorldBorderFillTask createWorldBorderFillTask(@NotNull WorldBorderFillSetting worldBorderFillSetting);

}
