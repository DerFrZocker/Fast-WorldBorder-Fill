package de.derfrzocker.fast.worldborder.fill.api;

import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.logging.Logger;

public class WorldBorderThread extends Thread {

    private final Object toNotify = new Object();
    @NotNull
    private final WorldBorderFillTask worldBorderFillTask;
    @NotNull
    private final Consumer<WorldBorderThread> finishConsumer;
    private final long sleepTime;
    private volatile Runnable runnable = null;
    private volatile int runs;
    private volatile String status;
    private int x;
    private int z;

    private String name;

    public WorldBorderThread(@NotNull final WorldBorderFillTask worldBorderFillTask, final long sleepTime, @NotNull final Consumer<WorldBorderThread> finishConsumer) {
        Validate.notNull(worldBorderFillTask, "WorldBorderFillTask can not be null");
        Validate.notNull(finishConsumer, "FinishConsumer can not be null");

        this.worldBorderFillTask = worldBorderFillTask;
        this.sleepTime = sleepTime;
        this.finishConsumer = finishConsumer;
    }

    @Override
    public void run() {
        name = getName();

        while (true) {

            try {
                synchronized (toNotify) {
                    if (runnable == null) {
                        status = "WAITING";
                        toNotify.wait();
                        status = "WAITING FINISH";
                    }
                    if (status.equals("CRASH")) {
                        toNotify.wait();
                    }

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                status = "CRASH";
                continue;
            }

            synchronized (toNotify) {
                try {
                    status = "RUNNING";
                    runs++;
                    runnable.run();
                } catch (Exception e) {
                    e.printStackTrace();
                    status = "CRASH";
                    continue;
                }
                status = "RUNNING FINISH";
                runnable = null;
                if (sleepTime > 0) {
                    status = "SLEEP";
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        status = "CRASH";
                        continue;
                    }
                }

                finishConsumer.accept(this);

            }
        }

    }

    public void setAndNotifyRunnable(@NotNull final Runnable runnable, final int x, final int z) {
        synchronized (toNotify) {

            this.runnable = runnable;
            this.x = x;
            this.z = z;

            toNotify.notify();
        }
    }

    public void printStatus() {
        final Logger logger = worldBorderFillTask.getLogger();
        logger.info("----------" + name + "----------");
        logger.info("Status: " + status);
        logger.info("X: " + x);
        logger.info("Z: " + z);
        logger.info("Runs: " + runs);
        logger.info("Contains: " + worldBorderFillTask.getWaitingWorldBorderThreads().contains(this));
        logger.info("----------" + name + "----------");
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.status;
    }

}
