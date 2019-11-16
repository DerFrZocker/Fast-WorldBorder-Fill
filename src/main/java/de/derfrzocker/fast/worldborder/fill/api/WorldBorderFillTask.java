package de.derfrzocker.fast.worldborder.fill.api;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.logging.Logger;

public interface WorldBorderFillTask extends Runnable {

    /**
     * prints the status of this WorldBorderFillTask to the console
     */
    void printStatus();

    Logger getLogger();

    /**
     * @return a new set with all WorldBorderThreads this WorldBorderFillTask use
     */
    @NotNull
    Set<WorldBorderThread> getAllWorldBorderThreads();


    /**
     * @return a new set with all waiting WorldBorderThreads
     */
    @NotNull
    Set<WorldBorderThread> getWaitingWorldBorderThreads();

}
