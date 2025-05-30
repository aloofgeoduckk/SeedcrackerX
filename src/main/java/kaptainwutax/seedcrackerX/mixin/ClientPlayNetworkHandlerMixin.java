package kaptainwutax.seedcrackerX.mixin;

import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.config.StructureSave;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import kaptainwutax.seedcrackerX.finder.FinderQueue;
import kaptainwutax.seedcrackerX.finder.ReloadFinders;
import kaptainwutax.seedcrackerX.util.Database;
import kaptainwutax.seedcrackerX.util.Log;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
* The `SeedCrackerNetworkHandler` mixin is a sophisticated Minecraft mod component designed to enhance 
* seed tracking and analysis by intercepting critical network events during chunk loading, 
* game joining, and player respawning. It extends the game's network handling capabilities to support the SeedCrackerX 
* mod's advanced seed cracking mechanisms. The mixin integrates closely with several key dependencies like 
* `SeedCracker`, `HashedSeedData`, and `Database` to dynamically track and process seed-related information. It enables 
* real-time seed data collection by intercepting network events, reloading finders based on dimension changes, and attempting to 
* retrieve full world seeds from a remote database. Key functionalities include capturing hashed 
* seed data during critical game state transitions, managing finder reloading through `ReloadFinders`, and facilitating 
* seed information submission and retrieval via the `Database` class. The implementation provides a flexible, event-driven approach 
* to seed analysis, allowing the mod to gather and process seed-related data across different Minecraft game contexts.
* By leveraging Minecraft's network handler and integrating with the mod's configuration and data management systems, this 
* mixin serves as a crucial bridge between game events and seed cracking functionality, enabling more sophisticated world generation analysis and seed discovery techniques.
*/

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow
    private ClientWorld world;

    @Shadow public abstract ClientConnection getConnection();

    @Inject(method = "onChunkData", at = @At(value = "TAIL"))
    private void onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        int chunkX = packet.getChunkX();
        int chunkZ = packet.getChunkZ();
        FinderQueue.get().onChunkData(this.world, new ChunkPos(chunkX, chunkZ));
    }

    @Inject(method = "onGameJoin", at = @At(value = "TAIL"))
    public void onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        newDimension(new HashedSeedData(packet.commonPlayerSpawnInfo().seed()), false);
        tryDatabase();
        var preloaded = StructureSave.loadStructures();
        if (!preloaded.isEmpty()) {
            Log.warn("foundRestorableStructures", preloaded.size());
        }
    }

    @Inject(method = "onPlayerRespawn", at = @At(value = "TAIL"))
    public void onPlayerRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        newDimension(new HashedSeedData(packet.commonPlayerSpawnInfo().seed()), true);
        tryDatabase();
    }

    @Unique
    private void newDimension(HashedSeedData hashedSeedData, boolean dimensionChange) {
        DimensionType dimension = MinecraftClient.getInstance().world.getDimension();
        ReloadFinders.reloadHeight(dimension.minY(), dimension.minY() + dimension.logicalHeight());

        if (SeedCracker.get().getDataStorage().addHashedSeedData(hashedSeedData, DataAddedEvent.POKE_BIOMES) && Config.get().active && dimensionChange) {
            Log.error(Log.translate("fetchedHashedSeed"));
            if (Config.get().debug) {
                Log.error("Hashed seed [" + hashedSeedData.getHashedSeed() + "]");
            }
        }
    }

    @Unique
    private void tryDatabase() {
        Long seed = Database.getSeed(this.getConnection().getAddress().toString(), SeedCracker.get().getDataStorage().hashedSeedData.getHashedSeed());
        if (seed == null) {
            return;
        }
        Log.printSeed("tmachine.foundWorldSeedFromDatabase", seed);
    }
}
