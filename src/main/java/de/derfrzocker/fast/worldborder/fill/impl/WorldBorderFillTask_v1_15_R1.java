package de.derfrzocker.fast.worldborder.fill.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.derfrzocker.fast.worldborder.fill.api.Region;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillSetting;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderFillTask;
import de.derfrzocker.fast.worldborder.fill.api.WorldBorderThread;
import de.derfrzocker.fast.worldborder.fill.utils.Lock;
import de.derfrzocker.spigot.utils.Pair;
import net.minecraft.server.v1_15_R1.*;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class WorldBorderFillTask_v1_15_R1 implements WorldBorderFillTask {
    private final static Object DUMMY_OBJECT = new Object();
    private final Logger logger;
    private final JavaPlugin javaPlugin;
    private final WorldBorderFillSetting worldBorderFillSetting;
    private final Region region;
    private final CraftWorld craftWorld;
    private final PlayerChunkMap playerChunkMap;
    private final int threadCount;
    private final int xRadius;
    private final int zRadius;
    private final int xStart;
    private final int zStart;
    private final int batchSize;
    private final long sleepTime;
    private final BlockingQueue<WorldBorderThread> threads;
    private final Set<WorldBorderThread> threadSet = new HashSet<>();
    private final Supplier<WorldPersistentData> supplier;
    private final DefinedStructureManager definedStructureManager;
    private final VillagePlace villagePlace;
    private final LightEngineThreaded lightEngineThreaded;
    private final BlockingQueue<IChunkAccess> toSave = new LinkedBlockingQueue<>(150000);
    private final BlockingQueue<Pair<ChunkCoordIntPair, NBTTagCompound>> toSaveNBTTagCompound = new LinkedBlockingQueue<>(150000);
    private final BlockingQueue<ChunkCoordIntPair> toSaveVillagePlace = new LinkedBlockingQueue<>(150000);
    private final LoadingCache<ChunkCoordIntPair, IChunkAccess> cache = CacheBuilder.newBuilder()
            .maximumSize(150000)
            .removalListener(removalNotification -> {
                IChunkAccess iChunkAccess = (IChunkAccess) removalNotification.getValue();

                if (!iChunkAccess.isNeedsSaving())
                    return;

                while (true) {
                    try {
                        if (toSave.offer(iChunkAccess, 5, TimeUnit.MINUTES))
                            break;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            })
            .build(new CacheLoader<ChunkCoordIntPair, IChunkAccess>() {
                @Override
                public IChunkAccess load(ChunkCoordIntPair chunkCoordIntPair) {
                    try {
                        synchronized (playerChunkMap) {
                            NBTTagCompound nbtTagCompound = playerChunkMap.read(chunkCoordIntPair);

                            if (nbtTagCompound == null)
                                return new ProtoChunk(chunkCoordIntPair, ChunkConverter.a);

                            nbtTagCompound = playerChunkMap.getChunkData(craftWorld.getHandle().getWorldProvider().getDimensionManager(), supplier, nbtTagCompound, chunkCoordIntPair, craftWorld.getHandle());

                            return ChunkRegionLoader.loadChunk(craftWorld.getHandle(), definedStructureManager, villagePlace, chunkCoordIntPair, nbtTagCompound);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    throw new RuntimeException();
                }
            });
    private final Map<BukkitTask, Object> bukkitTasks = Collections.synchronizedMap(new WeakHashMap<>());
    private boolean run = false;
    private int batch = 0;
    private ChunkStatus chunkStatus;
    private volatile boolean wait = false;

    public WorldBorderFillTask_v1_15_R1(@NotNull final JavaPlugin javaPlugin, @NotNull final Logger logger, @NotNull final WorldBorderFillSetting worldBorderFillSetting) throws NoSuchFieldException, IllegalAccessException {
        Validate.notNull(javaPlugin, "JavaPlugin can not be null");
        Validate.notNull(logger, "Logger can not be null");
        Validate.notNull(worldBorderFillSetting, "WorldBorderFillSetting can not be null");

        final org.bukkit.World world = javaPlugin.getServer().getWorld(worldBorderFillSetting.getWorldName());

        if (world == null)
            throw new IllegalStateException("World " + worldBorderFillSetting.getWorldName() + " does not exist / is not loaded");

        this.javaPlugin = javaPlugin;
        this.logger = logger;
        this.worldBorderFillSetting = worldBorderFillSetting;
        this.region = worldBorderFillSetting.getRegion();
        this.craftWorld = (CraftWorld) world;
        this.playerChunkMap = craftWorld.getHandle().getChunkProvider().playerChunkMap;
        this.threadCount = worldBorderFillSetting.getThreadsAmount();
        this.xRadius = region.getXRadius();
        this.zRadius = region.getZRadius();
        this.xStart = region.getMiddleChunk().getX();
        this.zStart = region.getMiddleChunk().getZ();
        this.batchSize = worldBorderFillSetting.getBatchSize();
        this.sleepTime = worldBorderFillSetting.getThreadSleepTime();
        this.threads = new LinkedBlockingQueue<>(this.threadCount);

        {
            Field field = PlayerChunkMap.class.getDeclaredField("l");
            field.setAccessible(true);
            this.supplier = (Supplier<WorldPersistentData>) field.get(playerChunkMap);
        }

        {
            Field field = PlayerChunkMap.class.getDeclaredField("definedStructureManager");
            field.setAccessible(true);
            this.definedStructureManager = (DefinedStructureManager) field.get(playerChunkMap);
        }

        {
            Field field = PlayerChunkMap.class.getDeclaredField("m");
            field.setAccessible(true);
            this.villagePlace = (VillagePlace) field.get(playerChunkMap);
        }

        {
            Field field = PlayerChunkMap.class.getDeclaredField("lightEngine");
            field.setAccessible(true);
            this.lightEngineThreaded = (LightEngineThreaded) field.get(playerChunkMap);
        }
    }

    public void run() {
        if (run)
            throw new IllegalStateException("WorldBorderFillTask already runs!");

        run = true;

        new Thread(this::save).start();
        new Thread(this::saveNBT).start();
        new Thread(this::saveVillagePlace).start();

        for (int i = 0; i < this.threadCount; i++) {
            final WorldBorderThread worldBorderThread = new WorldBorderThread(this, sleepTime, threads::offer);
            worldBorderThread.start();
            threadSet.add(worldBorderThread);
            threads.offer(worldBorderThread);
        }

        for (int x = -xRadius; x < xRadius; x = x + batchSize) {
            for (int z = -zRadius; z < zRadius; z = z + batchSize) {
                batch++;

                final int xPosition = xStart + x;
                final int zPosition = zStart + z;


                logger.info(batch + ": Begin with STRUCTURE_STARTS");
                wait = true;
                generate(ChunkStatus.STRUCTURE_STARTS, batchSize + 50, xPosition - 25, zPosition - 25);
                wait = false;
                logger.info(batch + ": End STRUCTURE_STARTS");
                logger.info(batch + ": Begin with STRUCTURE_REFERENCES");

                generate(ChunkStatus.STRUCTURE_REFERENCES, batchSize + 34, xPosition - 17, zPosition - 17);
                logger.info(batch + ": End STRUCTURE_REFERENCES");

                for (int x_ = 0; x_ < (batchSize + 50); x_++) {
                    for (int z_ = 0; z_ < 8; z_++) {
                        cache.invalidate(new ChunkCoordIntPair(x_ + xPosition - 25, z_ + zPosition - 25));
                    }
                }

                logger.info(batch + ": Begin with BIOMES");
                generate(ChunkStatus.BIOMES, batchSize + 34, xPosition - 17, zPosition - 17);
                logger.info(batch + ": End BIOMES");

                logger.info(batch + ": Begin with NOISE");
                generate(ChunkStatus.NOISE, batchSize + 18, xPosition - 9, zPosition - 9);
                logger.info(batch + ": End NOISE");

                for (int x_ = 0; x_ < (batchSize + 50); x_++) {
                    for (int z_ = 0; z_ < 8; z_++) {
                        cache.invalidate(new ChunkCoordIntPair(x_ + xPosition - 17, z_ + zPosition - 17));
                    }
                }

                logger.info(batch + ": Begin with SURFACE, CARVERS, LIQUID_CARVERS");
                generate(ChunkStatus.SURFACE, batchSize + 18, xPosition - 9, zPosition - 9, ChunkStatus.CARVERS, ChunkStatus.LIQUID_CARVERS);
                logger.info(batch + ": End SURFACE, CARVERS, LIQUID_CARVERS");

                logger.info(batch + ": Begin with FEATURES");
                generate(ChunkStatus.FEATURES, batchSize + 2, xPosition - 1, zPosition - 1);
                logger.info(batch + ": End FEATURES");

                for (int x_ = 0; x_ < (batchSize + 50); x_++) {
                    for (int z_ = 0; z_ < 8; z_++) {
                        cache.invalidate(new ChunkCoordIntPair(x_ + xPosition - 9, z_ + zPosition - 9));
                    }
                }

                // logger.info(batch + ": Begin with LIGHT");
                //generate(ChunkStatus.LIGHT, 50, xStart + i + 25, zStart +i2 + 25, xfirst, zfirst);
                //logger.info(batch + ": End LIGHT");
                logger.info(batch + ": Begin with SPAWN, HEIGHTMAPS");
                generate(ChunkStatus.SPAWN, batchSize, xPosition, zPosition, ChunkStatus.HEIGHTMAPS);
                logger.info(batch + ": End SPAWN, HEIGHTMAPS");
                // logger.info(batch + ": Begin with FULL");
                //generate(ChunkStatus.FULL, 50, xStart + i + 25, zStart + i2 + 25, xfirst, zfirst);
                //logger.info(batch + ": End FULL");
                for (int x_ = 0; x_ < (batchSize + 50); x_++) {
                    for (int z_ = 0; z_ < (batchSize - 25); z_++) {
                        cache.invalidate(new ChunkCoordIntPair(x_ + xPosition, z_ + zPosition));
                    }
                }
                //logger.info(batch + ": End FULL");
            }
            cleanUp();
            waitForToSave();
        }
        waitForToSave();
    }

    private void generate(ChunkStatus chunkStatus, int value, final int xPoint, final int zPoint, ChunkStatus... others) {
        this.chunkStatus = chunkStatus;

        final Map<ChunkCoordIntPair, Lock> map = new HashMap<>();
        final int cap = chunkStatus.f() * 2;
        for (int zCap = 0; zCap <= cap; zCap++) {
            for (int xCap = 0; xCap <= cap; xCap++) {
                for (int zChunk = 0; zChunk < value; zChunk = zChunk + cap + 1) {
                    for (int xChunk = 0; xChunk < value; xChunk = xChunk + cap + 1) {
                        int chunkCoordX = xChunk + xCap;
                        int chunkCoordZ = zChunk + zCap;

                        if (chunkCoordX >= value) {
                            continue;
                        }

                        if (chunkCoordZ >= value) {
                            continue;
                        }

                        final ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(chunkCoordX + xPoint, chunkCoordZ + zPoint);

                        try {
                            WorldBorderThread worldBorderThread = this.threads.take();

                            worldBorderThread.setAndNotifyRunnable(() -> {
                                try {
                                    final List<ChunkCoordIntPair> chunkCoordIntPairs = new LinkedList<>();

                                    worldBorderThread.setStatus("WAIT FOR ICHUNKACCESS");
                                    IChunkAccess iChunkAccess = getIChunkAccess(chunkCoordIntPair);

                                    if (iChunkAccess.getChunkStatus().b(chunkStatus)) {
                                        return;
                                    }

                                    Lock newLock = new Lock();
                                    List<IChunkAccess> iChunkAccesses = new LinkedList<>();

                                    worldBorderThread.setStatus("COLLECT OTHER ChunkCoordIntPair");
                                    for (int z = -chunkStatus.f(); z <= chunkStatus.f(); z++) {
                                        for (int x = -chunkStatus.f(); x <= chunkStatus.f(); x++) {
                                            final ChunkCoordIntPair pos = new ChunkCoordIntPair(chunkCoordIntPair.x + x, chunkCoordIntPair.z + z);
                                            chunkCoordIntPairs.add(pos);
                                        }
                                    }

                                    while (true) {
                                        Lock lock = null;
                                        synchronized (map) {
                                            worldBorderThread.setStatus("CHECK LOOKS");
                                            for (final ChunkCoordIntPair chunkPair : chunkCoordIntPairs) {
                                                final Lock look = map.get(chunkPair);
                                                if (look != null) {
                                                    synchronized (look) {
                                                        if (look.isLocked()) {
                                                            lock = look;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            if (lock == null) {
                                                worldBorderThread.setStatus("COLLECT OTHER ICHUNKACCESS");
                                                for (final ChunkCoordIntPair chunkPair : chunkCoordIntPairs) {
                                                    IChunkAccess other = getIChunkAccess(chunkPair);
                                                    iChunkAccesses.add(other);

                                                    newLock.lock();
                                                    map.put(chunkPair, newLock);
                                                }
                                                break;
                                            }
                                        }

                                        synchronized (lock) {
                                            if (lock.isLocked()) {
                                                worldBorderThread.setStatus("WAIT ON LOCK");
                                                lock.wait();
                                            }
                                        }
                                    }
                                    if (chunkStatus != ChunkStatus.FULL) {
                                        worldBorderThread.setStatus("RUN FIRST CHUNKSTATUS");
                                        if(chunkStatus != ChunkStatus.FEATURES) {
                                            chunkStatus.a(craftWorld.getHandle(), playerChunkMap.chunkGenerator, definedStructureManager, lightEngineThreaded, null, iChunkAccesses);
                                        }else {
                                            ProtoChunk var8 = (ProtoChunk)iChunkAccess;
                                            var8.a(lightEngineThreaded);
                                            if (!iChunkAccess.getChunkStatus().b(chunkStatus)) {
                                                HeightMap.a(iChunkAccess, EnumSet.of(net.minecraft.server.v1_15_R1.HeightMap.Type.MOTION_BLOCKING, net.minecraft.server.v1_15_R1.HeightMap.Type.MOTION_BLOCKING_NO_LEAVES, net.minecraft.server.v1_15_R1.HeightMap.Type.OCEAN_FLOOR, net.minecraft.server.v1_15_R1.HeightMap.Type.WORLD_SURFACE));
                                                RegionLimitedWorldAccess regionLimitedWorldAccess = new RegionLimitedWorldAccess(craftWorld.getHandle(), iChunkAccesses);
                                                int i = regionLimitedWorldAccess.a();
                                                int j = regionLimitedWorldAccess.b();
                                                int k = i * 16;
                                                int l = j * 16;
                                                BlockPosition blockposition = new BlockPosition(k, 0, l);
                                                BiomeBase biomebase = regionLimitedWorldAccess.d().a(blockposition.b(8, 8, 8));
                                                SeededRandom seededrandom = new SeededRandom();
                                                long i1 = seededrandom.a(regionLimitedWorldAccess.getSeed(), k, l);
                                                WorldGenStage.Decoration[] aworldgenstage_decoration = WorldGenStage.Decoration.values();
                                                int j1 = aworldgenstage_decoration.length;

                                                for(int k1 = 0; k1 < j1; ++k1) {
                                                    WorldGenStage.Decoration worldgenstage_decoration = aworldgenstage_decoration[k1];

                                                    try {
                                                        int var7 = 0;

                                                        for(Iterator var9 = ((List)biomebase.a(worldgenstage_decoration)).iterator(); var9.hasNext(); ++var7) {
                                                            WorldGenFeatureConfigured<?, ?> var10 = (WorldGenFeatureConfigured)var9.next();
                                                            seededrandom.b(i1, var7, worldgenstage_decoration.ordinal());

                                                            try {
                                                                final String key = IRegistry.FEATURE.getKey(var10.b ).getKey();
                                                                if(key.equals("decorated_flower") || key.equals("decorated")){
                                                                    synchronized (DUMMY_OBJECT) {
                                                                        var10.a(regionLimitedWorldAccess, playerChunkMap.chunkGenerator, seededrandom, blockposition);
                                                                   }
                                                                }else {
                                                                    var10.a(regionLimitedWorldAccess, playerChunkMap.chunkGenerator, seededrandom, blockposition);
                                                                }
                                                            } catch (Exception var13) {
                                                                System.out.println(IRegistry.FEATURE.getKey(var10.b ).getKey());
                                                                System.out.println(((Object) var10.c).getClass().getName());
                                                                System.out.println(var10.b);
                                                                CrashReport var11 = CrashReport.a(var13, "Feature placement");
                                                                var11.a("Feature").a("Id", IRegistry.FEATURE.getKey(var10.b)).a("Description", () -> {
                                                                    return var10.b.toString();
                                                                });
                                                                throw new ReportedException(var11);
                                                            }
                                                        }
                                                    } catch (Exception var17) {
                                                        CrashReport crashreport = CrashReport.a(var17, "Biome decoration");
                                                        crashreport.a("Generation").a("CenterX", i).a("CenterZ", j).a("Step", worldgenstage_decoration).a("Seed", i1).a("Biome", IRegistry.BIOME.getKey(biomebase));
                                                        throw new ReportedException(crashreport);
                                                    }
                                                }
                                                var8.a(chunkStatus);
                                            }
                                        }

                                        worldBorderThread.setStatus("RUN SECOND CHUNKSTATUS");
                                        chunkStatus.a(craftWorld.getHandle(), definedStructureManager, lightEngineThreaded, null, iChunkAccess);
                                        iChunkAccesses.forEach(iChunkAccess1 -> iChunkAccess1.setNeedsSaving(true));

                                        worldBorderThread.setStatus("RUN OTHER CHUNKSTATUS");

                                        for (ChunkStatus other : others) {
                                            other.a(craftWorld.getHandle(), playerChunkMap.chunkGenerator, definedStructureManager, lightEngineThreaded, null, iChunkAccesses);
                                            other.a(craftWorld.getHandle(), definedStructureManager, lightEngineThreaded, null, iChunkAccess);
                                        }

                                    } else {
                                        Chunk chunk = new Chunk(craftWorld.getHandle(), (ProtoChunk) iChunkAccess);
                                        iChunkAccess.setNeedsSaving(false);
                                        cache.put(chunkCoordIntPair, chunk);
                                    }


                                    synchronized (map) {
                                        synchronized (newLock) {
                                            worldBorderThread.setStatus("UNLOCK AND NOTIFY");
                                            newLock.unlock();
                                            for (final ChunkCoordIntPair chunkPair : chunkCoordIntPairs)
                                                map.remove(chunkPair);
                                            newLock.notifyAll();
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }, chunkCoordIntPair.x, chunkCoordIntPair.z);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }

        waitForThreads();
    }

    private IChunkAccess getIChunkAccess(final ChunkCoordIntPair position) {
        try {
            return cache.get(position);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);

        }
    }

    boolean waitForThreads() {
        while (threadCount > threads.size()) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    @Override
    public void printStatus() {
        threadSet.forEach(WorldBorderThread::printStatus);

        getLogger().info("----------Information----------");
        getLogger().info("To save size: " + this.toSave.size());
        getLogger().info("To save NBT size: " + this.toSaveNBTTagCompound.size());
        getLogger().info("To save Village place size: " + this.toSaveVillagePlace.size());
        getLogger().info("Cache size: " + this.cache.size());
        getLogger().info("Chunk Status: " + this.chunkStatus);
        getLogger().info("Batch: " + this.batch);
        getLogger().info("----------Information----------");
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @NotNull
    @Override
    public Set<WorldBorderThread> getAllWorldBorderThreads() {
        return new HashSet<>(this.threadSet);
    }

    @NotNull
    @Override
    public Set<WorldBorderThread> getWaitingWorldBorderThreads() {
        return new HashSet<>(this.threads);
    }

    private void cleanUp() {
        cache.invalidateAll();
    }

    private void waitForToSave() {
        while (!toSave.isEmpty() || !toSaveNBTTagCompound.isEmpty() || !toSaveVillagePlace.isEmpty() || checkPendingTask()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkPendingTask() {
        final List<BukkitTask> pending = Bukkit.getScheduler().getPendingTasks();

        for (BukkitTask bukkitTask : bukkitTasks.keySet()) {
            if (pending.contains(bukkitTask))
                return true;
        }

        return false;
    }

    private void save() {
        try {
            while (true) {
                IChunkAccess iChunkAccess = toSave.take();

                if (iChunkAccess instanceof ProtoChunkExtension)
                    return;

               /* if (iChunkAccess instanceof Chunk && ((Chunk) iChunkAccess).needsDecoration) {
                    final BukkitTask task = Bukkit.getScheduler().runTask(javaPlugin, () -> {
                        final Chunk chunk = (Chunk) iChunkAccess;
                        chunk.A();
                        chunk.loadCallback();
                        try {
                            toSave.offer(chunk, 5, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });

                    //bukkitTasks.put(task, object);

                    return;
                }*/

                final ChunkCoordIntPair chunkcoordintpair = iChunkAccess.getPos();

                toSaveVillagePlace.offer(chunkcoordintpair, 5, TimeUnit.MINUTES);

                iChunkAccess.setLastSaved(craftWorld.getHandle().getTime());
                iChunkAccess.setNeedsSaving(false);

                final NBTTagCompound nbtTagCompound = ChunkRegionLoader.saveChunk(craftWorld.getHandle(), iChunkAccess);

                toSaveNBTTagCompound.offer(new Pair<>(chunkcoordintpair, nbtTagCompound), 5, TimeUnit.MINUTES);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void saveNBT() {
        try {
            while (true) {
                final Pair<ChunkCoordIntPair, NBTTagCompound> pair = toSaveNBTTagCompound.take();

                while (wait) {
                    Thread.sleep(10);
                }

                synchronized (playerChunkMap) {
                    playerChunkMap.a(pair.getFirst(), pair.getSecond());

                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void saveVillagePlace() {
        try {
            while (true) {
                final ChunkCoordIntPair chunkCoordIntPair = toSaveVillagePlace.take();

                while (wait) {
                    Thread.sleep(10);
                }

                villagePlace.a(chunkCoordIntPair);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
