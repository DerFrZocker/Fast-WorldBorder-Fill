package de.derfrzocker.fast.worldborder.fill.impl;

import de.derfrzocker.fast.worldborder.fill.api.Region;
import de.derfrzocker.spigot.utils.ChunkCoordIntPair;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;

public class BasicRegion implements Region {

    @NotNull
    private final ChunkCoordIntPair middleChunk;
    private final int xRadius;
    private final int zRadius;

    /**
     * Create a new Basic Region with the given values.
     *
     * @param middleChunk the center chunk of the region
     * @param xRadius     to use, must higher than 26
     * @param zRadius     to use, must higher than 26
     * @throws IllegalArgumentException if middle chunk is null
     * @throws IllegalArgumentException if xRadius or zRadius is less than 26
     */
    public BasicRegion(@NotNull final ChunkCoordIntPair middleChunk, final int xRadius, final int zRadius) {
        Validate.notNull(middleChunk, "Middle chunk ChunkCoordIntPair can not be null");
        Validate.isTrue(xRadius > 25, "X-Radius can not be less than 26");
        Validate.isTrue(zRadius > 25, "Z-Radius can not be less than 26");

        this.middleChunk = middleChunk;
        this.xRadius = xRadius;
        this.zRadius = zRadius;
    }

    @NotNull
    @Override
    public ChunkCoordIntPair getMiddleChunk() {
        return this.middleChunk;
    }

    @Override
    public int getXRadius() {
        return this.xRadius;
    }

    @Override
    public int getZRadius() {
        return this.zRadius;
    }

    @Override
    public boolean shouldGenerate(@NotNull final ChunkCoordIntPair chunkCoordIntPair) {
        Validate.notNull(chunkCoordIntPair, "ChunkCoordIntPair can not be null");

        return true;
    }

}
