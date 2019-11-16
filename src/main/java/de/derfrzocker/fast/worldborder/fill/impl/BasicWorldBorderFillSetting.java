package de.derfrzocker.fast.worldborder.fill.impl;

import de.derfrzocker.fast.worldborder.fill.api.Region;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillSetting;
import org.jetbrains.annotations.NotNull;

public class BasicWorldBorderFillSetting implements WorldBorderFillSetting {

    private final int batchSize;
    private final int threadsAmount;
    private final long threadSleepTime;
    @NotNull
    private final String worldName;
    @NotNull
    private final Region region;

    public BasicWorldBorderFillSetting(int batchSize, int threadsAmount, long threadSleepTime, @NotNull String worldName, @NotNull Region region) {
        this.batchSize = batchSize;
        this.threadsAmount = threadsAmount;
        this.threadSleepTime = threadSleepTime;
        this.worldName = worldName;
        this.region = region;
    }

    @Override
    public int getBatchSize() {
        return this.batchSize;
    }

    @Override
    public int getThreadsAmount() {
        return this.threadsAmount;
    }

    @Override
    public long getThreadSleepTime() {
        return this.threadSleepTime;
    }

    @NotNull
    @Override
    public String getWorldName() {
        return this.worldName;
    }

    @NotNull
    @Override
    public Region getRegion() {
        return this.region;
    }

}
