package de.derfrzocker.fast.worldborder.fill.api;

import org.jetbrains.annotations.NotNull;

public interface WorldBorderFillSetting {

    /**
     * Return how much chunks should get generated at the same time.
     * The returning value description not the amount of chunks to generated.
     * It description how long the batch should be, this means to get the real amount of chunks
     * you must multiply the value with it self.
     *
     * @return the size of the batch
     */
    int getBatchSize();

    /**
     * Return how much threads should generate at the same time
     *
     * @return the amount of threads
     */
    int getThreadsAmount();

    /**
     * Return the time a Thread should sleep, after a generation step.
     *
     * @return time to sleep in millisecond
     */
    long getThreadSleepTime();

    /**
     * @return the name of the world which get generated
     */
    @NotNull
    String getWorldName();

    /**
     * @return the Area which should get generated
     */
    @NotNull
    Region getRegion();

}
