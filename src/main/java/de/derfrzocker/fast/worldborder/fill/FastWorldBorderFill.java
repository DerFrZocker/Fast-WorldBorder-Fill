package de.derfrzocker.fast.worldborder.fill;

import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillService;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillSetting;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillTask;
import de.derfrzocker.fast.worldborder.fill.impl.BasicRegion;
import de.derfrzocker.fast.worldborder.fill.impl.BasicWorldBorderFillSetting;
import de.derfrzocker.fast.worldborder.fill.impl.WorldBorderFillServiceImpl;
import de.derfrzocker.spigot.utils.ChunkCoordIntPair;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;


public class FastWorldBorderFill extends JavaPlugin {

    private WorldBorderFillTask worldBorderFill;
    private WorldBorderFillService worldBorderFillService;

    private long starTime;
    private long finishTime;

    @Override
    public void onEnable() {
        new Metrics(this);
        worldBorderFillService = new WorldBorderFillServiceImpl(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("status")) {
            if (worldBorderFill == null) {
                sender.sendMessage("No operation is present");
            }

            worldBorderFill.printStatus();

            sender.sendMessage("Status was print to console");

            return true;
        }

        if (worldBorderFill != null) {
            sender.sendMessage("World border fill already in run!");
            return true;
        }

        if (args.length != 8) {
            sender.sendMessage("Wrong amount of Arguments");
            sender.sendMessage("Use /fast-fill <threads-amount> <batch-size> <x-radius> <z-radius> <sleep-time> <x-start-point> <z-start-point> <world>");
            return true;
        }

        final int threadAmount;
        try {
            threadAmount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The Thread amount " + args[0] + " is not a valid number");
            return true;
        }

        final int batchSize;
        try {
            batchSize = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The batch size " + args[1] + " is not a valid number");
            return true;
        }

        final int xRadius;
        try {
            xRadius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The x radius " + args[2] + " is not a valid number");
            return true;
        }

        final int zRadius;
        try {
            zRadius = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The z radius " + args[3] + " is not a valid number");
            return true;
        }


        final long sleepTime;
        try {
            sleepTime = Long.parseLong(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The Sleep time " + args[4] + " is not a valid number");
            return true;
        }

        final int x;
        try {
            x = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The X start point " + args[5] + " is not a valid number");
            return true;
        }

        final int z;
        try {
            z = Integer.parseInt(args[6]);
        } catch (NumberFormatException e) {
            sender.sendMessage("The Z start point " + args[6] + " is not a valid number");
            return true;
        }

        final World world = Bukkit.getWorld(args[7]);
        if (world == null) {
            sender.sendMessage("World " + args[7] + " not found");
            return true;
        }

        world.setAutoSave(false);

        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            starTime = System.currentTimeMillis();

            final WorldBorderFillSetting worldBorderFillSetting = new BasicWorldBorderFillSetting(batchSize, threadAmount, sleepTime, world.getName(), new BasicRegion(new ChunkCoordIntPair(x / 16, z / 16), xRadius / 16, zRadius / 16));
            worldBorderFill = worldBorderFillService.createWorldBorderFillTask(worldBorderFillSetting);
            worldBorderFill.run();

            finishTime = System.currentTimeMillis();

            getLogger().info("Start Time: " + starTime);
            getLogger().info("Finish Time: " + finishTime + " -> " + TimeUnit.MILLISECONDS.toMinutes(finishTime - starTime));

        }).start();

        return true;
    }

}
