package de.derfrzocker.fast.worldborder.fill;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.derfrzocker.spigot.utils.Pair;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class WorldBorderFill {
    public final BlockingQueue<IChunkAccess> toSave = new LinkedBlockingQueue<>(150000);
    public final BlockingQueue<Pair<ChunkCoordIntPair, NBTTagCompound>> toSaveNBTTagCompound = new LinkedBlockingQueue<>(150000);
    public final BlockingQueue<ChunkCoordIntPair> toSaveVillagePlace = new LinkedBlockingQueue<>(150000);

    public final BlockingQueue<WorldBorderThread> threads;
    public final LoadingCache<ChunkCoordIntPair, IChunkAccess> cache = CacheBuilder.newBuilder()
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
                            synchronized (playerChunkMap.cache) {
                                NBTTagCompound nbtTagCompound = playerChunkMap.read(chunkCoordIntPair);

                                if (nbtTagCompound == null)
                                    return new ProtoChunk(chunkCoordIntPair, ChunkConverter.a);

                                nbtTagCompound = playerChunkMap.getChunkData(craftWorld.getHandle().getWorldProvider().getDimensionManager(), supplier, nbtTagCompound, chunkCoordIntPair, craftWorld.getHandle());

                                return ChunkRegionLoader.loadChunk(craftWorld.getHandle(), definedStructureManager, villagePlace, chunkCoordIntPair, nbtTagCompound);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    throw new RuntimeException();
                }
            });

    private final CraftWorld craftWorld;
    private final PlayerChunkMap playerChunkMap;
    private final Supplier<WorldPersistentData> supplier;
    private final DefinedStructureManager definedStructureManager;
    private final VillagePlace villagePlace;
    private final LightEngineThreaded lightEngineThreaded;
    private final int threadCount;
    private final Logger logger;
    private final int size;
    private final int xStart;
    private final int zStart;
    private final long sleepTime;
    private final JavaPlugin javaPlugin;
    private final Object object = new Object();
    private final Map<BukkitTask, Object> bukkitTasks = Collections.synchronizedMap(new WeakHashMap<>());
    public final Set<WorldBorderThread> threadSet = new HashSet<>();

    public int x;
    public int z;
    public int xcap;
    public int zcap;
    public int batch = 0;
    public ChunkStatus chunkStatus;
    private volatile boolean wait = false;

    public WorldBorderFill(JavaPlugin javaPlugin, CraftWorld craftWorld, PlayerChunkMap playerChunkMap, int threadCount, Logger logger, int size, int xStart, int zStart, long sleepTime) throws NoSuchFieldException, IllegalAccessException {
        this.craftWorld = craftWorld;
        this.playerChunkMap = playerChunkMap;
        this.threadCount = threadCount;
        this.logger = logger;
        this.size = size;
        this.xStart = xStart;
        this.zStart = zStart;
        this.sleepTime = sleepTime;
        this.javaPlugin = javaPlugin;
        this.threads = new LinkedBlockingQueue<>(this.threadCount);

        {
            Field field = PlayerChunkMap.class.getDeclaredField("m");
            field.setAccessible(true);
            this.supplier = (Supplier<WorldPersistentData>) field.get(playerChunkMap);
        }

        {
            Field field = PlayerChunkMap.class.getDeclaredField("definedStructureManager");
            field.setAccessible(true);
            this.definedStructureManager = (DefinedStructureManager) field.get(playerChunkMap);
        }

        {
            Field field = PlayerChunkMap.class.getDeclaredField("n");
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
        new Thread(this::save).start();
        new Thread(this::saveNBT).start();
        new Thread(this::saveVillagePlace).start();

        for (int i = 0; i < this.threadCount; i++) {
            WorldBorderThread worldBorderThread = new WorldBorderThread(this, sleepTime);
            worldBorderThread.start();
            threadSet.add(worldBorderThread);
            add(worldBorderThread);
        }

        boolean xfirst = true;
        boolean zfirst = true;

        for (int i = -size; i < size; i = i + 50) {
            zfirst = true;
            for (int i2 = -size; i2 < size; i2 = i2 + 50) {
                batch++;
                logger.info(batch + ": Begin with STRUCTURE_STARTS");
                wait = true;
                generate(ChunkStatus.STRUCTURE_STARTS, 100, xStart + i, zStart + i2, xfirst, zfirst);
                wait = false;
                logger.info(batch + ": End STRUCTURE_STARTS");
                logger.info(batch + ": Begin with STRUCTURE_REFERENCES");
                generate(ChunkStatus.STRUCTURE_REFERENCES, 84, xStart + i + 8, zStart + i2 + 8, xfirst, zfirst);
                logger.info(batch + ": End STRUCTURE_REFERENCES");
                for (int x = 0; x < 100; x++) {
                    for (int z = 0; z < 8; z++) {
                        cache.invalidate(new ChunkCoordIntPair(x + xStart + i, zStart + z + i2));
                    }
                }
                logger.info(batch + ": Begin with BIOMES");
                generate(ChunkStatus.BIOMES, 84, xStart + i + 8, zStart + i2 + 8, xfirst, zfirst);
                logger.info(batch + ": End BIOMES");
                logger.info(batch + ": Begin with NOISE");
                generate(ChunkStatus.NOISE, 68, xStart + i + 16, zStart + i2 + 16, xfirst, zfirst);
                for (int x = 0; x < 100; x++) {
                    for (int z = 0; z < 8; z++) {
                        cache.invalidate(new ChunkCoordIntPair(x + xStart + i, 8 + zStart + z + i2));
                    }
                }
                logger.info(batch + ": End NOISE");
                logger.info(batch + ": Begin with SURFACE, CARVERS, LIQUID_CARVERS");
                generate(ChunkStatus.SURFACE, 68, xStart + i + 16, zStart + i2 + 16, xfirst, zfirst, ChunkStatus.CARVERS, ChunkStatus.LIQUID_CARVERS);
                logger.info(batch + ": End SURFACE, CARVERS, LIQUID_CARVERS");
                logger.info(batch + ": Begin with FEATURES");
                generate(ChunkStatus.FEATURES, 52, xStart + i + 24, zStart + i2 + 24, xfirst, zfirst);
                logger.info(batch + ": End FEATURES");
                for (int x = 0; x < 100; x++) {
                    for (int z = 0; z < 9; z++) {
                        cache.invalidate(new ChunkCoordIntPair(x + xStart + i, 16 + zStart + z + i2));
                    }
                }
                // logger.info(batch + ": Begin with LIGHT");
                //generate(ChunkStatus.LIGHT, 50, xStart + i + 25, zStart +i2 + 25, xfirst, zfirst);
                //logger.info(batch + ": End LIGHT");
                logger.info(batch + ": Begin with SPAWN, HEIGHTMAPS");
                generate(ChunkStatus.SPAWN, 50, xStart + i + 25, zStart + i2 + 25, xfirst, zfirst, ChunkStatus.HEIGHTMAPS);
                logger.info(batch + ": End SPAWN, HEIGHTMAPS");
                // logger.info(batch + ": Begin with FULL");
                //generate(ChunkStatus.FULL, 50, xStart + i + 25, zStart + i2 + 25, xfirst, zfirst);
                //logger.info(batch + ": End FULL");
                for (int x = 0; x < 100; x++) {
                    for (int z = 0; z < 25; z++) {
                        cache.invalidate(new ChunkCoordIntPair(x + xStart + i, 25 + zStart + z + i2));
                    }
                }
                //logger.info(batch + ": End FULL");
                zfirst = false;
            }
            cleanUp();
            waitForToSave();
            xfirst = false;
        }
        waitForToSave();
    }

    private void generate(ChunkStatus chunkStatus, int value, final int xPoint, final int zPoint, boolean xFirst, boolean zFirst, ChunkStatus... others) {
        this.chunkStatus = chunkStatus;
        do {
            final Map<ChunkCoordIntPair, Lock> map = new HashMap<>();
            final int cap = chunkStatus.f() * 2;
            for (int zCap = 0; zCap <= cap; zCap++) {
                for (int xCap = 0; xCap <= cap; xCap++) {
                    for (int zChunk = 0; zChunk < value; zChunk = zChunk + cap + 1) {
                        for (int xChunk = 0; xChunk < value; xChunk = xChunk + cap + 1) {
                            this.xcap = xCap;
                            this.zcap = zCap;
                            int chunkCoordX = xChunk + xCap;
                            int chunkCoordZ = zChunk + zCap;

                            if (chunkCoordX >= value) {
                                continue;
                            }

                            if (chunkCoordZ >= value) {
                                continue;
                            }

                            final ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(chunkCoordX + xPoint, chunkCoordZ + zPoint);
                            this.x = chunkCoordIntPair.x;
                            this.z = chunkCoordIntPair.z;

                            try {
                                WorldBorderThread worldBorderThread = this.threads.take();

                                worldBorderThread.setAndNotifyRunnable(() -> {
                                    try {
                                        final List<ChunkCoordIntPair> chunkCoordIntPairs = new LinkedList<>();

                                        worldBorderThread.status = "WAIT FOR ICHUNKACCESS";
                                        IChunkAccess iChunkAccess = getIChunkAccess(chunkCoordIntPair);

                                        if (iChunkAccess.getChunkStatus().b(chunkStatus)) {
                                            return;
                                        }

                                        Lock newLock = new Lock();
                                        List<IChunkAccess> iChunkAccesses = new LinkedList<>();

                                        worldBorderThread.status = "COLLECT OTHER ChunkCoordIntPair";
                                        for (int z = -chunkStatus.f(); z <= chunkStatus.f(); z++) {
                                            for (int x = -chunkStatus.f(); x <= chunkStatus.f(); x++) {
                                                final ChunkCoordIntPair pos = new ChunkCoordIntPair(chunkCoordIntPair.x + x, chunkCoordIntPair.z + z);
                                                chunkCoordIntPairs.add(pos);
                                            }
                                        }

                                        while (true) {
                                            Lock lock = null;
                                            synchronized (map) {
                                                worldBorderThread.status = "CHECK LOOKS";
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
                                                    worldBorderThread.status = "COLLECT OTHER ICHUNKACCESS";
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
                                                    worldBorderThread.status = "WAIT ON LOCK";
                                                    lock.wait();
                                                }
                                            }
                                        }
                                        if (chunkStatus != ChunkStatus.FULL) {
                                            worldBorderThread.status = "RUN FIRST CHUNKSTATUS";
                                            chunkStatus.a(craftWorld.getHandle(), playerChunkMap.chunkGenerator, definedStructureManager, lightEngineThreaded, null, iChunkAccesses);
                                            worldBorderThread.status = "RUN SECOND CHUNKSTATUS";
                                            chunkStatus.a(craftWorld.getHandle(), definedStructureManager, lightEngineThreaded, null, iChunkAccess);
                                            iChunkAccesses.forEach(iChunkAccess1 -> iChunkAccess1.setNeedsSaving(true));

                                            worldBorderThread.status = "RUN OTHER CHUNKSTATUS";

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
                                                worldBorderThread.status = "UNLOCK AND NOTIFY";
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
        } while (!waitForThreads());

    }

    private IChunkAccess getIChunkAccess(final ChunkCoordIntPair position) {
        try {
            return cache.get(position);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);

        }
    }

    boolean waitForThreads() {
        boolean reRun = false;
        int size = 0;
        Set<WorldBorderThread> threads = new HashSet<>();
        while (threadCount > (this.threads.size() + size)) {
            try {
                for (WorldBorderThread worldBorderThread : threadSet) {
                    if (worldBorderThread.status.equals("CRASH") && !threads.contains(worldBorderThread)) {
                        threads.add(worldBorderThread);
                        reRun = true;
                        size++;
                    }
                }

                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (reRun) {
            logger.warning("Detect Crashing Thread! re-run last batch");
            threads.forEach(WorldBorderThread::restart);
            return false;
        }

        return true;
    }

    void cleanUp() {
        cache.invalidateAll();
    }

    void waitForToSave() {
        while (!toSave.isEmpty() || !toSaveNBTTagCompound.isEmpty() || !toSaveVillagePlace.isEmpty() || checkPendingTask()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    boolean checkPendingTask() {
        final List<BukkitTask> pending = Bukkit.getScheduler().getPendingTasks();

        for (BukkitTask bukkitTask : bukkitTasks.keySet()) {
            if (pending.contains(bukkitTask))
                return true;
        }

        return false;
    }

    void add(WorldBorderThread worldBorderThread) {
        if (!threads.offer(worldBorderThread)) {
            System.out.println("EROROR");
            throw new RuntimeException();
        }
    }

    private void save() {
        try {
            while (true) {
                IChunkAccess iChunkAccess = toSave.take();

                if (iChunkAccess instanceof ProtoChunkExtension)
                    return;

                if (iChunkAccess instanceof Chunk && ((Chunk) iChunkAccess).needsDecoration) {
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
                    bukkitTasks.put(task, object);

                    return;
                }

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
                    synchronized (playerChunkMap.cache) {
                        try {
                            playerChunkMap.write(pair.getFirst(), pair.getSecond());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
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
