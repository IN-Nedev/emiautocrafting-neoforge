// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.lang.reflect.*;

/** Test-world-only fixtures using real chests, Bookwyrm links and a finite AE2 storage cell. */
final class StorageFixtures {
    private static final BlockPos TABLE = new BlockPos(1, 100, 0), CHEST = TABLE.east(), SECOND_CHEST = TABLE.north();
    private static final java.util.Set<net.minecraft.world.inventory.AbstractContainerMenu> observedMenus =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static void observeBackgroundMetadata(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var server = event.getServer();
        if (!server.getWorldData().getLevelName().equals("Autocrafting verification")) return;
        for (var player : server.getPlayerList().getPlayers()) {
            var menu = player.containerMenu;
            if (!menu.getClass().getName().equals("com.leclowndu93150.craftingstationjei.menu.CraftingStationMenu")
                    || observedMenus.contains(menu)) continue;
            Object chest = player.serverLevel().getBlockEntity(CHEST);
            if (chest == null || !chest.getClass().getName().startsWith("net.p3pp3rf1y.sophisticatedstorage.")) continue;
            Object inventory = call(call(chest, "getStorageWrapper"), "getInventoryHandler");
            if (!((ItemStack) call(inventory, "getStackInSlot", 1)).is(Items.COMPASS)) continue;
            observedMenus.add(menu);
            menu.addSlotListener(new net.minecraft.world.inventory.ContainerListener() {
                private int revision;
                public void slotChanged(net.minecraft.world.inventory.AbstractContainerMenu m, int index, ItemStack stack) {
                    if (index < 0 || index > 9) return;
                    // A real server-side metadata change on an UNUSED stored item, delivered
                    // in the same native synchronization as the grid/result change.
                    var compass = ((ItemStack) call(inventory, "getStackInSlot", 1)).copy();
                    var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("revision", ++revision);
                    compass.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                    call(inventory, "setStackInSlot", 1, compass);
                }
                public void dataChanged(net.minecraft.world.inventory.AbstractContainerMenu m, int index, int value) { }
            });
        }
    }
    static void staleClientStorage(net.minecraft.world.level.Level level) {
        if (!level.isClientSide) throw new IllegalStateException("Stale refresh fixture is client-only");
        Object wrapper = call(level.getBlockEntity(CHEST), "getStorageWrapper");
        call(call(wrapper, "getInventoryHandler"), "setStackInSlot", 0, new ItemStack(Items.OAK_LOG, 2));
    }
    static void setup(ServerPlayer player, String scenario) {
        if (!player.server.getWorldData().getLevelName().equals("Autocrafting verification"))
            throw new IllegalStateException("Storage fixtures require the disposable verification world");
        int logs = scenario.endsWith("missing") ? 1 : scenario.endsWith("batch") ? 48 : 2;
        if (scenario.endsWith("oversized")) logs = 128;
        var seed = scenario.endsWith("buckets") ? java.util.List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.WHEAT, 3), new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG))
                : java.util.List.of(new ItemStack(Items.OAK_LOG, logs - logs / 2), new ItemStack(Items.OAK_LOG, logs / 2));
        if (scenario.endsWith("quark_manager")) seed = java.util.List.of(new ItemStack(Items.OAK_LOG, 7),
                new ItemStack(Items.BIRCH_LOG), new ItemStack(item("sfm:cable"), 4), new ItemStack(Items.REPEATER));
        if (scenario.endsWith("exact20") || scenario.endsWith("single_step") || scenario.endsWith("existing_total"))
            seed = java.util.List.of(new ItemStack(Items.OAK_PLANKS,64), new ItemStack(Items.OAK_PLANKS,64),
                    new ItemStack(Items.OAK_PLANKS,64), new ItemStack(Items.OAK_PLANKS,64));
        if (scenario.endsWith("existing_total")) {
            seed = new java.util.ArrayList<>(seed); seed.add(new ItemStack(Items.CHEST,7));
        }
        if (scenario.contains("_grid_")) seed = java.util.List.of(new ItemStack(Items.OAK_LOG,32));
        if (scenario.endsWith("uneven")) seed = java.util.List.of(new ItemStack(Items.OAK_PLANKS,16));
        if (scenario.endsWith("three_cakes")) {
            seed = new java.util.ArrayList<>();
            for (int i=0;i<9;i++) seed.add(new ItemStack(Items.MILK_BUCKET));
            seed.add(new ItemStack(Items.WHEAT,9)); seed.add(new ItemStack(Items.SUGAR,6)); seed.add(new ItemStack(Items.EGG,3));
        }
        var level = player.serverLevel();
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(TABLE).inflate(10)).forEach(net.minecraft.world.entity.Entity::discard);
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastFullState();
        if (scenario.contains("ae2")) {
            level.setBlockAndUpdate(TABLE, block("ae2:cable_bus").defaultBlockState());
            level.setBlockAndUpdate(CHEST, block("ae2:drive").defaultBlockState());
            level.setBlockAndUpdate(CHEST.above(), block("ae2:creative_energy_cell").defaultBlockState());
            Object host = level.getBlockEntity(TABLE);
            call(host, "addPart", item("ae2:fluix_glass_cable"), null, player);
            call(host, "addPart", item("ae2:crafting_terminal"), Direction.NORTH, player);
            ItemStack cell = new ItemStack(item("ae2:item_storage_cell_1k"));
            Object storage = call(type("appeng.api.storage.StorageCells"), "getCellInventory", cell, null);
            Object mode = field("appeng.api.config.Actionable", "MODULATE");
            Object source = call(type("appeng.api.networking.security.IActionSource"), "empty");
            for (ItemStack stack : seed) if (!stack.isEmpty()) {
                Object key = call(type("appeng.api.stacks.AEItemKey"), "of", stack);
                if ((long) call(storage, "insert", key, (long) stack.getCount(), mode, source) != stack.getCount())
                    throw new IllegalStateException("Could not seed finite AE2 cell");
            }
            call(storage, "persist");
            Object drive = level.getBlockEntity(CHEST);
            call(call(drive, "getInternalInventory"), "setItemDirect", 0, cell);
        } else {
            level.setBlockAndUpdate(CHEST, scenario.contains("sophisticated") ? block(scenario.contains("hidden")
                    ? "sophisticatedstorage:netherite_chest" : "sophisticatedstorage:chest").defaultBlockState() : Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(SECOND_CHEST, Blocks.CHEST.defaultBlockState());
            if (scenario.contains("sophisticated")) {
                Object wrapper = call(level.getBlockEntity(CHEST), "getStorageWrapper");
                if (scenario.endsWith("oversized") || scenario.contains("_cap_")) call(call(wrapper, "getUpgradeHandler"), "setStackInSlot", 0, new ItemStack(item("sophisticatedstorage:stack_upgrade_tier_1")));
                call(call(wrapper, "getInventoryHandler"), "setStackInSlot", scenario.contains("hidden") ? 100 : 0, new ItemStack(Items.OAK_LOG, logs));
                if (scenario.contains("_cap_")) {
                    Object inventory = call(wrapper, "getInventoryHandler");
                    call(inventory, "setStackInSlot", 0, new ItemStack(Items.OAK_PLANKS, scenario.endsWith("midbatch") ? 122 : 70));
                    if (scenario.endsWith("multiple")) call(inventory, "setStackInSlot", 1, new ItemStack(Items.OAK_PLANKS, 70));
                }
                if (scenario.contains("background")) call(call(wrapper, "getInventoryHandler"), "setStackInSlot", 1, new ItemStack(Items.COMPASS));
            } else for (int i = 0; i < seed.size(); i++)
                ((Container) level.getBlockEntity(i % 2 == 0 ? CHEST : SECOND_CHEST)).setItem(i / 2, seed.get(i));
            if (scenario.contains("lectern")) {
                Object lectern = level.getBlockEntity(TABLE);
                call(lectern, "addBookwyrm");
                call(lectern, "onFinishedConnectionLast", CHEST, Direction.UP, null, player);
                call(lectern, "onFinishedConnectionLast", SECOND_CHEST, Direction.UP, null, player);
                call(lectern, "updateItems");
            }
        }
    }

    static void open(ServerPlayer player) {
        Object host = player.serverLevel().getBlockEntity(TABLE);
        Object part = call(host, "getPart", Direction.NORTH);
        Object locator = call(type("appeng.menu.locator.MenuLocators"), "forPart", part);
        Object menuType = field("appeng.menu.me.items.CraftingTermMenu", "TYPE");
        if (!(boolean) call(type("appeng.menu.MenuOpener"), "open", menuType, player, locator))
            throw new IllegalStateException("AE2 terminal did not open");
    }

    static void seedGrid(ServerPlayer player, String scenario) {
        if (!player.server.getWorldData().getLevelName().equals("Autocrafting verification")) throw new IllegalStateException("Not a test world");
        int filled=0;
        for (var slot : player.containerMenu.slots) {
            boolean grid=scenario.contains("ae2") ? slot.getClass().getName().equals("appeng.menu.slot.CraftingMatrixSlot") : slot.index>0 && slot.index<10;
            if (grid) {
                if (scenario.endsWith("uneven")) slot.set(filled==4 ? ItemStack.EMPTY : new ItemStack(Items.OAK_PLANKS, filled==0 ? 20 : 1));
                else if (!scenario.endsWith("reuse")) slot.set(new ItemStack(Items.COBBLESTONE,32));
                else if (filled==0) slot.set(new ItemStack(Items.OAK_LOG,20));
                filled++;
            }
        }
        if (scenario.endsWith("full")) for (int i=0;i<35;i++) player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        player.containerMenu.broadcastFullState();
    }

    static String check(ServerPlayer player, String scenario) {
        if (scenario.endsWith("uneven")) {
            long chests=amount(player,scenario,Items.CHEST), planks=amount(player,scenario,Items.OAK_PLANKS);
            return (chests==5 && planks==3 ? "PASS " : "FAIL ")+scenario+" conservation chests="+chests+" planks="+planks;
        }
        if (scenario.endsWith("exact20") || scenario.endsWith("single_step") || scenario.endsWith("existing_total")) {
            long chests=amount(player,scenario,Items.CHEST), planks=amount(player,scenario,Items.OAK_PLANKS);
            int expected=scenario.endsWith("single_step")?1:20;
            int made=scenario.endsWith("existing_total")?13:expected;
            return (chests==expected && planks==256-8*made ? "PASS " : "FAIL ")+scenario+" conservation chests="+chests+" planks="+planks;
        }
        if (scenario.contains("_grid_")) {
            boolean full=scenario.endsWith("full"), reuse=scenario.endsWith("reuse");
            long logs=amount(player,scenario,Items.OAK_LOG), planks=amount(player,scenario,Items.OAK_PLANKS), cobble=amount(player,scenario,Items.COBBLESTONE);
            boolean pass=logs==(full?32:reuse?47:27) && planks==(full?0:20) && cobble==(reuse?0:288);
            if (full) pass &= amount(player,scenario,Items.STONE)==35*64;
            return (pass?"PASS ":"FAIL ")+scenario+" conservation logs="+logs+" planks="+planks+" cobble="+cobble;
        }
        if (scenario.contains("_cap_")) {
            long chests = amount(player, scenario, Items.CHEST), planks = amount(player, scenario, Items.OAK_PLANKS);
            int stacks = scenario.endsWith("multiple") ? 2 : 1;
            boolean pass = chests == 8L * stacks && planks == (scenario.endsWith("midbatch") ? 58L : 6L * stacks);
            return (pass ? "PASS " : "FAIL ") + scenario + " conservation chests=" + chests + " planks=" + planks;
        }
        if (scenario.endsWith("quark_manager")) {
            long managers = amount(player, scenario, item("sfm:manager"));
            boolean pass = managers == 1 && java.util.List.of(Items.OAK_LOG, Items.BIRCH_LOG, Items.CHEST, Items.REPEATER, item("sfm:cable"))
                    .stream().allMatch(item -> amount(player, scenario, item) == 0);
            return (pass ? "PASS " : "FAIL ") + scenario + " conservation managers=" + managers + " ingredients consumed once";
        }
        if (scenario.endsWith("buckets") || scenario.endsWith("three_cakes")) {
            long cake = amount(player, scenario, Items.CAKE), buckets = amount(player, scenario, Items.BUCKET);
            int expected=scenario.endsWith("three_cakes")?3:1;
            boolean pass = cake == expected && buckets == expected*3 && java.util.List.of(Items.MILK_BUCKET, Items.WHEAT, Items.SUGAR, Items.EGG).stream()
                    .allMatch(item -> amount(player, scenario, item) == 0);
            return (pass ? "PASS " : "FAIL ") + scenario + " conservation cake="+cake+" returnedBuckets="+buckets;
        }
        long logs = amount(player, scenario, Items.OAK_LOG), planks = amount(player, scenario, Items.OAK_PLANKS);
        long sticks = amount(player, scenario, Items.STICK), picks = amount(player, scenario, Items.WOODEN_PICKAXE);
        boolean pass = scenario.endsWith("missing") ? logs == 1 && planks == 0 && sticks == 0 && picks == 0
                : scenario.endsWith("oversized") ? logs == 127 && planks == 4 && sticks == 0 && picks == 0
                : scenario.endsWith("batch") ? logs == 0 && planks == 192 && sticks == 0 && picks == 0
                : logs == 0 && planks == 3 && sticks == 2 && picks == 1;
        if (scenario.endsWith("batch")) pass &= player.getInventory().items.stream().filter(s -> !s.isEmpty()).count() == 3;
        String background = "";
        if (scenario.contains("background")) {
            Object inventory = call(call(player.serverLevel().getBlockEntity(CHEST), "getStorageWrapper"), "getInventoryHandler");
            ItemStack compass = (ItemStack) call(inventory, "getStackInSlot", 1);
            int revision = compass.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("revision");
            pass &= compass.is(Items.COMPASS) && compass.getCount() == 1 && revision > 0;
            background = " backgroundCompass="+compass.getCount()+" metadataRevision="+revision;
        }
        return (pass ? "PASS " : "FAIL ") + scenario + " conservation logs="+logs+" planks="+planks+" sticks="+sticks+" pickaxes="+picks+background;
    }

    private static long amount(ServerPlayer player, String scenario, Item item) {
        long result = player.getInventory().items.stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum();
        for (var slot : player.containerMenu.slots) {
            boolean grid = scenario.contains("ae2") ? slot.getClass().getName().equals("appeng.menu.slot.CraftingMatrixSlot") : slot.index > 0 && slot.index < 10;
            if (grid && slot.getItem().is(item)) result += slot.getItem().getCount();
        }
        if (scenario.contains("ae2")) {
            Object storage = call(player.serverLevel().getBlockEntity(CHEST), "getOriginalCellInventory", 0);
            Object stacks = call(storage, "getAvailableStacks");
            Object key = call(type("appeng.api.stacks.AEItemKey"), "of", new ItemStack(item));
            result += (long) call(stacks, "get", key);
        } else {
            for (BlockPos pos : java.util.List.of(CHEST, SECOND_CHEST)) {
                if (pos.equals(CHEST) && scenario.contains("sophisticated")) {
                    Object inventory = call(call(player.serverLevel().getBlockEntity(pos), "getStorageWrapper"), "getInventoryHandler");
                    for (int i = 0; i < (int) call(inventory, "getSlots"); i++) {
                        ItemStack stack = (ItemStack) call(inventory, "getStackInSlot", i);
                        if (stack.is(item)) result += stack.getCount();
                    }
                    continue;
                }
                Container chest = (Container) player.serverLevel().getBlockEntity(pos);
                for (int i=0;i<chest.getContainerSize();i++) if(chest.getItem(i).is(item)) result += chest.getItem(i).getCount();
            }
        }
        return result;
    }

    private static net.minecraft.world.level.block.Block block(String name) {
        var result = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name));
        if (result == Blocks.AIR) throw new IllegalStateException("Missing fixture block " + name);
        return result;
    }
    private static Item item(String name) {
        Item result = BuiltInRegistries.ITEM.get(ResourceLocation.parse(name));
        if (result == Items.AIR) throw new IllegalStateException("Missing fixture item " + name);
        return result;
    }
    private static Class<?> type(String name) {
        try { return Class.forName(name); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Object field(String name, String field) {
        try { return type(name).getField(field).get(null); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Object call(Object receiver, String name, Object... args) {
        Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes(); boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> param = parameters[i] == int.class ? Integer.class : parameters[i] == long.class ? Long.class : parameters[i] == boolean.class ? Boolean.class : parameters[i];
                if (args[i] != null && !param.isInstance(args[i])) compatible = false;
            }
            if (!compatible) continue;
            try { return method.invoke(receiver instanceof Class<?> ? null : receiver, args); }
            catch (Exception e) { throw new IllegalStateException(type.getName() + "." + name, e); }
        }
        throw new IllegalStateException("Missing fixture API " + type.getName() + "." + name);
    }
}
