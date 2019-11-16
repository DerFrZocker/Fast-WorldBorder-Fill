package de.derfrzocker.fast.worldborder.fill.api;

import de.derfrzocker.spigot.utils.ChunkCoordIntPair;
import org.jetbrains.annotations.NotNull;

public interface Region {

    /**
     * @return the middle point chunk, of the WorldBorder
     */
    @NotNull
    ChunkCoordIntPair getMiddleChunk();

    /**
     * The amount of chunks, to generate along the x-axis
     *
     * @return amount of chunks
     */
    int getXRadius();

    /**
     * The amount of chunks, to generate along the z-axis
     *
     * @return amount of chunks
     */
    int getZRadius();


    /**
     * Return true if the chunk on the given position should get generated or false for not.
     * This can be used for custom shapes, for example a circle.
     *
     * @param chunkCoordIntPair to check
     * @return true to generate false, for not generate
     * @throws IllegalArgumentException if chunkCoordIntPair is null
     */
    boolean shouldGenerate(@NotNull ChunkCoordIntPair chunkCoordIntPair);

}
