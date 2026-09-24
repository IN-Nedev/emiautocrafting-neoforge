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
    private static final BlockPos THIRD_CHEST = TABLE.south();
    private static java.util.List<ItemStack> cellMaterials(String scenario) {
        var seed = new java.util.ArrayList<ItemStack>();
        var materials = scenario.endsWith("housings200") ? java.util.Map.of("minecraft:redstone", 600, "minecraft:iron_ingot", 400, "minecraft:copper_ingot", 200, "ae2:certus_quartz_dust", 500, "minecraft:glass", 400) : java.util.Map.of("minecraft:redstone", 1400, "ae2:certus_quartz_crystal", 800,
                "ae2:logic_processor", 200, "minecraft:iron_ingot", 400, "minecraft:copper_ingot", 200,
                "ae2:certus_quartz_dust", 500, "minecraft:glass", 400);
        materials.forEach((name, count) -> {
            for (int left=count; left>0; left-=64) seed.add(new ItemStack(item(name), Math.min(64,left)));
        });
        return seed;
    }
    private static java.util.List<BlockPos> chests(String scenario) {
        return (scenario.endsWith("cells200") || scenario.endsWith("housings200")) ? java.util.List.of(CHEST, SECOND_CHEST, THIRD_CHEST) : java.util.List.of(CHEST, SECOND_CHEST);
    }
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
        if ((scenario.endsWith("cells200") || scenario.endsWith("housings200"))) seed = cellMaterials(scenario);
        if (scenario.endsWith("upgrade_named")) {
            ItemStack base = new ItemStack(item("sophisticatedstorage:void_upgrade"), 2);
            base.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Preserved filter"));
            seed = java.util.List.of(base, new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.GOLD_INGOT, 4), new ItemStack(Items.REDSTONE, 6));
        }
        if (scenario.contains("grid_player_")) seed = scenario.endsWith("mixed") || scenario.endsWith("partial")
                ? java.util.List.of(new ItemStack(Items.OAK_PLANKS,64),new ItemStack(Items.OAK_PLANKS,16)) : java.util.List.of();
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
            ItemStack cell = new ItemStack(item((scenario.endsWith("cells200") || scenario.endsWith("housings200")) ? "ae2:item_storage_cell_16k" : "ae2:item_storage_cell_1k"));
            Object storage = call(type("appeng.api.storage.StorageCells"), "getCellInventory", cell, null);
            Object mode = field("appeng.api.config.Actionable", "MODULATE");
            Object source = call(type("appeng.api.networking.security.IActionSource"), "empty");
            for (ItemStack stack : seed) if (!stack.isEmpty()) {
                Object key = call(type("appeng.api.stacks.AEItemKey"), "of", stack);
                if ((long) call(storage, "insert", key, (long) stack.getCount(), mode, source) != stack.getCount())
                    throw new IllegalStateException("Could not seed finite AE2 cell");
            }
            if (scenario.endsWith("grid_overflow") || scenario.endsWith("grid_partial_overflow")) {
                Object filler = call(type("appeng.api.stacks.AEItemKey"), "of", new ItemStack(Items.DIRT));
                for (int attempts = 0; attempts < 1000; attempts++) {
                    long inserted = (long) call(storage, "insert", filler, 64L, mode, source);
                    if (inserted == 0) break;
                    if (attempts == 999) throw new IllegalStateException("Could not fill finite AE2 cell");
                }
                if (scenario.endsWith("grid_partial_overflow")) {
                    long freed = (long) call(storage, "extract", filler, 128L, mode, source);
                    if (freed != 128L) throw new IllegalStateException("Could not free partial AE2 cell capacity");
                }
            }
            call(storage, "persist");
            Object drive = level.getBlockEntity(CHEST);
            call(call(drive, "getInternalInventory"), "setItemDirect", 0, cell);
        } else {
            level.setBlockAndUpdate(CHEST, scenario.contains("sophisticated") ? block(scenario.contains("hidden")
                    ? "sophisticatedstorage:netherite_chest" : "sophisticatedstorage:chest").defaultBlockState() : Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(SECOND_CHEST, Blocks.CHEST.defaultBlockState());
            if ((scenario.endsWith("cells200") || scenario.endsWith("housings200"))) level.setBlockAndUpdate(THIRD_CHEST, Blocks.CHEST.defaultBlockState());
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
            } else for (int i = 0; i < seed.size(); i++) {
                var chests = chests(scenario);
                ((Container) level.getBlockEntity(chests.get(i % chests.size()))).setItem(i / chests.size(), seed.get(i));
            }
            if (scenario.contains("lectern")) {
                Object lectern = level.getBlockEntity(TABLE);
                call(lectern, "addBookwyrm");
                call(lectern, "onFinishedConnectionLast", CHEST, Direction.UP, null, player);
                call(lectern, "onFinishedConnectionLast", SECOND_CHEST, Direction.UP, null, player);
                if ((scenario.endsWith("cells200") || scenario.endsWith("housings200"))) call(lectern, "onFinishedConnectionLast", THIRD_CHEST, Direction.UP, null, player);
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
        if (scenario.contains("grid_player_")) {
            Item item = scenario.endsWith("smallstacks") ? Items.SNOWBALL : Items.OAK_PLANKS;
            int count = scenario.endsWith("only") ? 256 : scenario.endsWith("partial") ? 79 : 80;
            for (int i=0;count>0;i++) {
                int stackSize=Math.min(count,new ItemStack(item).getMaxStackSize());
                player.getInventory().setItem(i,new ItemStack(item,stackSize)); count-=stackSize;
            }
            if (scenario.endsWith("partial"))
                player.containerMenu.slots.stream().filter(slot -> slot.getClass().getName().equals("appeng.menu.slot.CraftingMatrixSlot"))
                        .findFirst().orElseThrow().set(new ItemStack(Items.OAK_PLANKS));
            player.containerMenu.broadcastFullState(); return;
        }
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
        if (scenario.endsWith("upgrade_named")) {
            Item upgraded = item("sophisticatedstorage:advanced_void_upgrade");
            long produced = amount(player, scenario, upgraded);
            boolean settings = player.getInventory().items.stream().filter(stack -> stack.is(upgraded))
                    .allMatch(stack -> stack.getHoverName().getString().equals("Preserved filter"));
            long base = amount(player, scenario, item("sophisticatedstorage:void_upgrade"));
            long diamonds = amount(player, scenario, Items.DIAMOND);
            long gold = amount(player, scenario, Items.GOLD_INGOT);
            long redstone = amount(player, scenario, Items.REDSTONE);
            boolean pass = produced == 2 && settings && base == 0 && diamonds == 0 && gold == 0 && redstone == 0;
            return (pass ? "PASS " : "FAIL ") + scenario + " output=" + produced + " preservedSettings=" + settings
                    + " base=" + base + " diamonds=" + diamonds + " gold=" + gold + " redstone=" + redstone;
        }
        if (scenario.contains("grid_player_")) {
            boolean snow=scenario.endsWith("smallstacks");
            long output=amount(player,scenario,snow?Items.SNOW_BLOCK:Items.CHEST), left=amount(player,scenario,snow?Items.SNOWBALL:Items.OAK_PLANKS);
            boolean pass=output==20 && left==(scenario.endsWith("only")?96:0) && player.containerMenu.getCarried().isEmpty();
            return (pass?"PASS ":"FAIL ")+scenario+" conservation output="+output+" ingredients="+left;
        }
        if ((scenario.endsWith("cells200") || scenario.endsWith("housings200"))) {
            long cells = amount(player, scenario, item("ae2:item_storage_cell_1k"));
            var remaining = new java.util.LinkedHashMap<String,Long>();
            for (var stack : cellMaterials(scenario)) {
                long count=amount(player,scenario,stack.getItem());
                if(count>0) remaining.put(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),count);
            }
            for (String name : java.util.List.of("ae2:cell_component_1k", "ae2:quartz_glass")) {
                long count=amount(player,scenario,item(name)); if(count>0) remaining.put(name,count);
            }
            boolean noDrops=player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(TABLE).inflate(10)).isEmpty();
            boolean pass;
            if (scenario.endsWith("housings200")) {
                cells=amount(player,scenario,item("ae2:item_cell_housing"));
                pass=cells==200 && remaining.isEmpty();
            } else {
                long parts=amount(player,scenario,item("ae2:cell_component_1k")), glass=amount(player,scenario,item("ae2:quartz_glass"));
                pass=cells>0 && cells<200
                        && amount(player,scenario,item("ae2:logic_processor"))+parts+cells==200
                        && amount(player,scenario,item("ae2:certus_quartz_crystal"))+4*parts+4*cells==800
                        && amount(player,scenario,Items.REDSTONE)+4*parts+7*cells==1400
                        && amount(player,scenario,Items.IRON_INGOT)+2*cells==400
                        && amount(player,scenario,Items.COPPER_INGOT)+cells==200
                        && 4*amount(player,scenario,item("ae2:certus_quartz_dust"))+5*glass+10*cells==2000
                        && amount(player,scenario,Items.GLASS)+glass+2*cells==400;
            }
            pass &= noDrops && player.containerMenu.getCarried().isEmpty();
            return (pass?"PASS ":"FAIL ")+scenario+" conservation output="+cells+" remaining="+remaining+" noDrops="+noDrops;
        }
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
            String placement = "";
            if (scenario.equals("storage_ae2_grid_clear") || scenario.equals("storage_ae2_grid_overflow")
                    || scenario.equals("storage_ae2_grid_partial_overflow")) {
                Object cell = call(player.serverLevel().getBlockEntity(CHEST), "getOriginalCellInventory", 0);
                Object available = call(cell, "getAvailableStacks");
                Object key = call(type("appeng.api.stacks.AEItemKey"), "of", new ItemStack(Items.COBBLESTONE));
                long networkCobble = (long) call(available, "get", key);
                long playerCobble = player.getInventory().items.stream().filter(stack -> stack.is(Items.COBBLESTONE))
                        .mapToLong(ItemStack::getCount).sum();
                pass &= scenario.endsWith("partial_overflow") ? networkCobble > 0 && networkCobble < 288
                        && playerCobble == 288 - networkCobble
                        : scenario.endsWith("overflow") ? networkCobble == 0 && playerCobble == 288
                        : networkCobble == 288 && playerCobble == 0;
                placement = " networkCobble=" + networkCobble + " playerCobble=" + playerCobble;
            }
            return (pass?"PASS ":"FAIL ")+scenario+" conservation logs="+logs+" planks="+planks+" cobble="+cobble+placement;
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
            for (BlockPos pos : chests(scenario)) {
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
