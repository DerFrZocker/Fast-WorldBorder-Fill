package de.derfrzocker.fast.worldborder.fill;

import java.util.logging.Logger;

public class WorldBorderThread extends Thread {

    private volatile Runnable runnable = null;

    private final Object toNotify = new Object();

    private final WorldBorderFill worldBorderFill;

    private final long sleepTime;

    public volatile int runs;

    public volatile String status;

    public int x;
    public int z;

    private String name;

    public WorldBorderThread(WorldBorderFill worldBorderFill, long sleepTime) {
        this.worldBorderFill = worldBorderFill;
        this.sleepTime = sleepTime;
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

                worldBorderFill.add(this);

            }
        }

    }

    public void setAndNotifyRunnable(Runnable runnable, int x, int z) {
        synchronized (toNotify) {

            this.runnable = runnable;
            this.x = x;
            this.z = z;

            toNotify.notify();
        }
    }

    public void printStatus(Logger logger) {
        logger.info("----------" + name + "----------");
        logger.info("Status: " + status);
        logger.info("X: " + x);
        logger.info("Z: " + z);
        logger.info("Runs: " + runs);
        logger.info("Contains: " + worldBorderFill.threads.contains(this));
        logger.info("----------" + name + "----------");
    }

    public void restart() {
        worldBorderFill.add(this);
    }

}
