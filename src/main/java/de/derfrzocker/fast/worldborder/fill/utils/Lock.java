package de.derfrzocker.fast.worldborder.fill.utils;

public class Lock {

    private volatile boolean locked = false;

    public synchronized boolean isLocked() {
        return locked;
    }

    public synchronized void unlock() {
        locked = false;
    }

    public synchronized void lock() {
        locked = true;
    }

}
