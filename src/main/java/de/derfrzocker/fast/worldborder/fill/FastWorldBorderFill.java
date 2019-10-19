package de.derfrzocker.fast.worldborder.fill;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;


public class FastWorldBorderFill extends JavaPlugin {

    private WorldBorderFill worldBorderFill;

    private long starTime;
    private long finishTime;

    @Override
    public void onEnable() {
        new Metrics(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("status")) {
            if (worldBorderFill == null) {
                sender.sendMessage("No operation is present");
            }

            int totalRuns = 0;

            for (WorldBorderThread worldBorderThread : worldBorderFill.threadSet) {
                worldBorderThread.printStatus(getLogger());
                totalRuns += worldBorderThread.runs;
            }

            getLogger().info("----------Information----------");
            getLogger().info("To save size: " + worldBorderFill.toSave.size());
            getLogger().info("To save NBT size: " + worldBorderFill.toSaveNBTTagCompound.size());
            getLogger().info("To save Village place size: " + worldBorderFill.toSaveVillagePlace.size());
            getLogger().info("Cache size: " + worldBorderFill.cache.size());
            getLogger().info("Chunk Status: " + worldBorderFill.chunkStatus);
            getLogger().info("Total runs: " + totalRuns);
            getLogger().info("X: " + worldBorderFill.x);
            getLogger().info("Z: " + worldBorderFill.z);
            getLogger().info("XCap: " + worldBorderFill.xcap);
            getLogger().info("ZCap: " + worldBorderFill.zcap);
            getLogger().info("----------Information----------");

            sender.sendMessage("Status was print to console");

            return true;
        }

        if (worldBorderFill != null) {
            sender.sendMessage("World border fill already in run!");
            return true;
        }

        if (args.length != 6) {
            sender.sendMessage("Wrong amount of Arguments");
            sender.sendMessage("Use /fast-fill <threads-amount> <chunk-radius> <sleep-time> <x-chunk-start-point> <z-chunk-start-point> <world>");
            return true;
        }

        final int threadAmount;
        try {
            threadAmount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The Thread amount " + args[0] + " is not a valid number");
            return true;
        }

        final int chunkRadius;
        try {
            chunkRadius = Integer.parseInt(args[1]) + 50;
        } catch (NumberFormatException e) {
            sender.sendMessage("The Chunk radius " + args[1] + " is not a valid number");
            return true;
        }

        if (chunkRadius % 50 != 0) {
            sender.sendMessage("Chunk radius must be a multiply of 50");
            return true;
        }

        final long sleepTime;
        try {
            sleepTime = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The Sleep time " + args[2] + " is not a valid number");
            return true;
        }

        final int x;
        try {
            x = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The X start point " + args[3] + " is not a valid number");
            return true;
        }

        final int z;
        try {
            z = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The Z start point " + args[4] + " is not a valid number");
            return true;
        }

        final World world = Bukkit.getWorld(args[5]);
        if (world == null) {
            sender.sendMessage("World " + args[5] + " not found");
            return true;
        }
        final CraftWorld craftWorld = (CraftWorld) world;

        craftWorld.setAutoSave(false);

        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                starTime = System.currentTimeMillis();
                worldBorderFill = new WorldBorderFill(this, craftWorld, craftWorld.getHandle().getChunkProvider().playerChunkMap, threadAmount, getLogger(), chunkRadius, x, z, sleepTime);
                worldBorderFill.run();
                finishTime = System.currentTimeMillis();

                getLogger().info("Start Time: " + starTime);
                getLogger().info("Finish Time: " + finishTime + " -> " + TimeUnit.MILLISECONDS.toMinutes(finishTime - starTime));

            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }).start();

        return true;
    }

}
