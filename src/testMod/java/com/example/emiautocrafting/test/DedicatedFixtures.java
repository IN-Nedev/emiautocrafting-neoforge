// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.test;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Out-of-band setup and assertions for a disposable LOCAL server. Never handles crafting. */
@Mod(value="autocrafting_test", dist=Dist.DEDICATED_SERVER)
public final class DedicatedFixtures {
    private static final Path DIRECTORY = Path.of(System.getProperty("emiautocrafting.fixtureDirectory", ".local-testing/fixture-exchange"));

    public DedicatedFixtures() {
        if (Boolean.getBoolean("emiautocrafting.serverFixtures")) NeoForge.EVENT_BUS.addListener(this::tick);
    }

    static String perform(ServerPlayer player, String action, String scenario) {
        if (player == null || !player.getGameProfile().getName().equals("AutocraftTest")
                || !player.server.getWorldData().getLevelName().equals("Autocrafting verification"))
            throw new IllegalStateException("Fixtures require AutocraftTest in the disposable verification world");
        return switch (action) {
            case "setup" -> { StorageFixtures.setup(player, scenario); yield "ready"; }
            case "open" -> { StorageFixtures.open(player); yield "opened"; }
            case "grid" -> { StorageFixtures.seedGrid(player, scenario); yield "seeded"; }
            case "check" -> StorageFixtures.check(player, scenario);
            default -> throw new IllegalArgumentException("Unknown fixture action");
        };
    }

    static CompletableFuture<String> request(UUID player, String action, String scenario) {
        return CompletableFuture.supplyAsync(() -> {
            String id = UUID.randomUUID().toString();
            Path request = DIRECTORY.resolve(id + ".request"), response = DIRECTORY.resolve(id + ".response");
            try {
                Files.createDirectories(DIRECTORY);
                Properties data = new Properties();
                data.setProperty("player", player.toString()); data.setProperty("action", action); data.setProperty("scenario", scenario);
                write(request, data);
                long deadline = System.nanoTime() + 15_000_000_000L;
                while (!Files.exists(response)) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("Local fixture server did not answer " + action);
                    Thread.sleep(25);
                }
                try (var input = Files.newInputStream(response)) { data.clear(); data.load(input); }
                if (data.containsKey("error")) throw new IllegalStateException(data.getProperty("error"));
                return data.getProperty("result");
            } catch (Exception e) { throw new IllegalStateException("Fixture exchange failed", e); }
            finally { try { Files.deleteIfExists(request); Files.deleteIfExists(response); } catch (Exception ignored) { } }
        });
    }

    private void tick(ServerTickEvent.Post event) {
        if (!Files.isDirectory(DIRECTORY)) return;
        try (var files = Files.newDirectoryStream(DIRECTORY, "*.request")) {
            for (Path request : files) {
                Path response = request.resolveSibling(request.getFileName().toString().replace(".request", ".response"));
                if (Files.exists(response)) continue;
                Properties data = new Properties(), result = new Properties();
                try {
                    if (!event.getServer().getLocalIp().equals("127.0.0.1")) throw new IllegalStateException("Fixture server must bind to loopback");
                    try (var input = Files.newInputStream(request)) { data.load(input); }
                    var player = event.getServer().getPlayerList().getPlayer(UUID.fromString(data.getProperty("player")));
                    result.setProperty("result", perform(player, data.getProperty("action"), data.getProperty("scenario")));
                } catch (Exception e) { e.printStackTrace(); result.setProperty("error", e.toString()); }
                write(response, result);
            }
        } catch (Exception e) { throw new IllegalStateException("Local fixture exchange failed", e); }
    }

    private static void write(Path path, Properties data) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (var output = Files.newOutputStream(temporary)) { data.store(output, "Local test fixture"); }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
    }
}
