// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.test;

import com.example.emiautocrafting.EmiAutocrafting;
import com.example.emiautocrafting.core.JobController;
import com.example.emiautocrafting.emi.EmiBridge;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.bom.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.util.*;

@Mod(value="autocrafting_test", dist=Dist.CLIENT)
public class RuntimeTests {
    static {
        Path config = Path.of("autocrafting-test.properties");
        if (Files.exists(config)) {
            try (var input = Files.newInputStream(config)) {
                Properties values = new Properties(); values.load(input);
                values.forEach((key, value) -> {
                    if (key.toString().startsWith("emiautocrafting.")) System.setProperty(key.toString(), value.toString());
                });
            } catch (Exception error) { throw new IllegalStateException("Cannot load test configuration", error); }
        }
    }
    private static final Minecraft MC=Minecraft.getInstance();
    private static final BlockPos TABLE=new BlockPos(1,100,0);
    private record Scenario(String name,String recipe,Item output,int target,int expected,String blocked,List<String> setup,boolean inventory) {}
    private static final List<Scenario> CASES=new ArrayList<>(List.of(
        new Scenario("logs_to_pickaxe","wooden_pickaxe",Items.WOODEN_PICKAXE,1,1,null,List.of("give @s oak_log 2"),false),
        new Scenario("existing_intermediates","wooden_pickaxe",Items.WOODEN_PICKAXE,1,1,null,List.of("give @s oak_planks 3","give @s stick 2"),false),
        new Scenario("existing_final_total","wooden_pickaxe",Items.WOODEN_PICKAXE,2,2,null,List.of("give @s wooden_pickaxe","give @s oak_log 2"),false),
        new Scenario("multi_output_surplus","stick",Items.STICK,5,8,null,List.of("give @s oak_log 1"),false),
        new Scenario("missing_base","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"Missing",List.of(),false),
        new Scenario("returned_buckets","cake",Items.CAKE,1,1,null,List.of("give @s milk_bucket 3","give @s wheat 3","give @s sugar 2","give @s egg"),false),
        new Scenario("player_2x2","stick",Items.STICK,4,4,null,List.of("give @s oak_log 1"),true),
        new Scenario("modded_components_alternative","autocrafting_test:workshop_token",Items.COMPASS,1,2,null,List.of("give @s birch_planks 3","give @s copper_ingot 1","give @s compass 1"),false),
        new Scenario("full_inventory","oak_planks",Items.OAK_PLANKS,4,0,"space",List.of("give @s oak_log 1"),false),
        new Scenario("preexisting_grid","oak_planks",Items.OAK_PLANKS,4,0,"Clear the crafting grid",List.of("give @s oak_log 2"),false),
        new Scenario("occupied_cursor","oak_planks",Items.OAK_PLANKS,4,0,"cursor",List.of("give @s oak_log 2"),false),
        new Scenario("single_step","wooden_pickaxe",Items.OAK_PLANKS,1,4,null,List.of("give @s oak_log 2"),false),
        new Scenario("changed_tree","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"CANCELLED",List.of("give @s oak_log 2"),false),
        new Scenario("closed_gui","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"CANCELLED",List.of("give @s oak_log 2"),false),
        new Scenario("server_rejects","oak_planks",Items.OAK_PLANKS,4,0,"timed out",List.of("give @s oak_log 1","gamerule doLimitedCrafting true","recipe take @s *"),false),
        new Scenario("player_four_input_shapeless","autocrafting_test:four_input_shapeless",Items.CLOCK,1,1,null,List.of("give @s oak_planks 1","give @s birch_planks 1","give @s stick 1","give @s copper_ingot 1"),true),
        new Scenario("player_requires_3x3","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"3×3",List.of("give @s oak_planks 3","give @s stick 2"),true),
        new Scenario("tree_smelting_blocked","repeater",Items.REPEATER,1,0,"Requires",List.of("give @s cobblestone 3","give @s redstone_torch 2","give @s redstone 1"),false),
        new Scenario("tree_stored_stone","repeater",Items.REPEATER,1,1,null,List.of("give @s stone 3","give @s redstone_torch 2","give @s redstone 1"),false),
        new Scenario("tree_unhearted_stone","repeater",Items.REPEATER,1,0,"Missing",List.of("give @s cobblestone 3","give @s redstone_torch 2","give @s redstone 1"),false),
        new Scenario("tree_local_stone_choice","repeater",Items.REPEATER,1,0,"Requires",List.of("give @s cobblestone 3","give @s redstone_torch 2","give @s redstone 1"),false)
    ));
    static {
        if (Boolean.getBoolean("emiautocrafting.quarkCompatibility")) {
            // Quark ticks compass components after crafting; this is a synchronization
            // guard case, distinct from the deterministic component fixture without Quark.
            CASES.removeIf(c -> c.name().equals("modded_components_alternative"));
            CASES.add(new Scenario("quark_compass_components_stop", "autocrafting_test:workshop_token", Items.COMPASS, 1, 2,
                    "timed out", List.of("give @s birch_planks 3", "give @s copper_ingot 1", "give @s compass 1"), false));
            CASES.add(new Scenario("quark_mixed_chests", "quark:building/crafting/chests/mixed_chest_wood", Items.CHEST, 4, 4, null,
                    List.of("give @s oak_log 7", "give @s birch_log 1"), false));
            CASES.add(new Scenario("quark_same_wood", "quark:building/crafting/chests/mixed_chest_wood", Items.CHEST, 4, 0, "mixed-material",
                    List.of("give @s oak_log 8"), false));
            CASES.add(new Scenario("quark_factory_manager", "sfm:manager", null, 1, 1, null,
                    List.of("give @s oak_log 7", "give @s birch_log 1", "give @s sfm:cable 4", "give @s repeater 1"), false));
            CASES.add(new Scenario("quark_exclusion_glass", "quark:tweaks/crafting/glass/mixed_dirty_glass", null, 1, 1, null,
                    List.of("give @s quark:red_shard 4", "give @s quark:blue_shard 1"), false));
            if (Boolean.getBoolean("emiautocrafting.storageCompatibility"))
                for (String menu : List.of("lectern", "station"))
                    CASES.add(new Scenario("storage_" + menu + "_quark_manager", "sfm:manager", null, 1, 1, null, List.of(), false));
        }
    }
    static {
        if(Boolean.getBoolean("emiautocrafting.toolFixtures")) {
            CASES.add(new Scenario("reusable_tool","autocrafting_test:reusable_tool",Items.GOLD_NUGGET,4,4,null,List.of("give @s autocrafting_test:test_stamp 1","give @s iron_ingot 4"),false));
            CASES.add(new Scenario("damageable_tool_breaks","autocrafting_test:damageable_tool",Items.GOLD_NUGGET,4,3,"Missing",List.of("give @s autocrafting_test:test_hammer 1","give @s iron_ingot 4"),false));
        }
    }
    static {
        if (Boolean.getBoolean("emiautocrafting.kubejsFixtures")) {
            CASES.add(new Scenario("kubejs_scripted_remainder","autocrafting_test:scripted_remainder",Items.GOLD_NUGGET,1,0,"unsupported",
                List.of("give @s iron_ingot 1","give @s stick 1"),false));
            CASES.add(new Scenario("kubejs_scripted_output","autocrafting_test:scripted_output",Items.DIAMOND,1,0,"unsupported",
                List.of("give @s iron_ingot 1","give @s coal 1"),false));
            CASES.add(new Scenario("kubejs_plain_shapeless","autocrafting_test:plain_kubejs_shapeless",Items.EMERALD,1,1,null,
                List.of("give @s copper_ingot 1","give @s coal 1"),true));
        }
    }
    static {
        if (Boolean.getBoolean("emiautocrafting.storageCompatibility")) {
            CASES.add(new Scenario("speed_planks", "oak_planks", Items.OAK_PLANKS, 128, 128, null, List.of("give @s oak_log 32"), false));
            CASES.add(new Scenario("storage_station_sophisticated_chain", "wooden_pickaxe", Items.WOODEN_PICKAXE, 1, 1, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_oversized", "oak_planks", Items.OAK_PLANKS, 4, 4, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_hidden_chain", "wooden_pickaxe", Items.WOODEN_PICKAXE, 1, 1, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_background_chain", "wooden_pickaxe", Items.WOODEN_PICKAXE, 1, 1, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_background_oversized", "oak_planks", Items.OAK_PLANKS, 4, 4, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_cap_boundary", "chest", Items.CHEST, 8, 8, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_cap_midbatch", "chest", Items.CHEST, 8, 8, null, List.of(), false));
            CASES.add(new Scenario("storage_station_sophisticated_cap_multiple", "chest", Items.CHEST, 16, 16, null, List.of(), false));
            for (String menu : List.of("station", "lectern", "ae2")) {
                CASES.add(new Scenario("storage_"+menu+"_chain", "wooden_pickaxe", Items.WOODEN_PICKAXE, 1, 1, null, List.of(), false));
                CASES.add(new Scenario("storage_"+menu+"_missing", "wooden_pickaxe", Items.WOODEN_PICKAXE, 1, 0, "Missing", List.of(), false));
                CASES.add(new Scenario("storage_"+menu+"_batch", "oak_planks", Items.OAK_PLANKS, 192, 192, null, List.of(), false));
                CASES.add(new Scenario("storage_"+menu+"_multi_missing", "autocrafting_test:storage_shortages", Items.PAPER, 1, 0, "Missing", List.of(), false));
                CASES.add(new Scenario("storage_"+menu+"_buckets", "cake", Items.CAKE, 1, 1, null, List.of(), false));
                if (!menu.equals("station")) {
                    CASES.add(new Scenario("storage_"+menu+"_exact20", "chest", Items.CHEST, 20, 20, null, List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_existing_total", "chest", Items.CHEST, 20, 13, null, List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_single_step", "chest", Items.CHEST, 20, 1, null, List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_grid_clear", "oak_planks", Items.OAK_PLANKS, 20, 20, null, List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_grid_reuse", "oak_planks", Items.OAK_PLANKS, 20, 20, null, List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_grid_full", "oak_planks", Items.OAK_PLANKS, 20, 0, "space", List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_three_cakes", "cake", Items.CAKE, 3, 3, null, List.of(), false));
                    CASES.add(new Scenario("storage_"+menu+"_grid_uneven", "chest", Items.CHEST, 5, 5, null, List.of(), false));
                }
            }
        }
        CASES.add(new Scenario("recipe_reload","wooden_pickaxe",Items.WOODEN_PICKAXE,100,0,"CANCELLED",List.of("give @s oak_log 200"),false));
        CASES.add(new Scenario("disconnect","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"CANCELLED",List.of("give @s oak_log 2"),false));
        String filter=System.getProperty("emiautocrafting.testFilter","all");
        if(!filter.equals("all")) { var allowed=Set.of(filter.split(",")); CASES.removeIf(c->!allowed.contains(c.name())); }
    }
    private long ticks, at;
    private int stage, test;
    private boolean launched;
    private int worldAttempts;
    private final String mode=System.getProperty("emiautocrafting.testMode","integrated");
    private final List<String> reports=new ArrayList<>();
    private boolean reportedPath, testedControls, testedGroup, disconnecting, reloadSent;
    private boolean staleStorageInjected;
    private boolean seededGrid;
    private java.util.concurrent.CompletableFuture<?> fixture;
    public RuntimeTests(net.neoforged.bus.api.IEventBus bus){
        if(Boolean.getBoolean("emiautocrafting.integration"))NeoForge.EVENT_BUS.addListener(this::tick);
        if(Boolean.getBoolean("emiautocrafting.integration"))NeoForge.EVENT_BUS.addListener(StorageFixtures::observeBackgroundMetadata);
        if(Boolean.getBoolean("emiautocrafting.staleStorageUpdate"))
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, this::staleStorageUpdate);
        if(Boolean.getBoolean("emiautocrafting.toolFixtures")) {
            var items=net.neoforged.neoforge.registries.DeferredRegister.createItems("autocrafting_test");
            items.register("test_stamp",()->new Item(new Item.Properties().stacksTo(1)) {
                public boolean hasCraftingRemainingItem(ItemStack stack){return true;}
                public ItemStack getCraftingRemainingItem(ItemStack stack){return stack.copyWithCount(1);}
            });
            items.register("test_hammer",()->new Item(new Item.Properties().durability(3)) {
                public boolean hasCraftingRemainingItem(ItemStack stack){return true;}
                public ItemStack getCraftingRemainingItem(ItemStack stack){
                    if(stack.getDamageValue()+1>=stack.getMaxDamage())return ItemStack.EMPTY;
                    ItemStack result=stack.copyWithCount(1);result.setDamageValue(stack.getDamageValue()+1);return result;
                }
            });
            items.register(bus);
        }
    }
    private void staleStorageUpdate(ClientTickEvent.Post event) {
        if (staleStorageInjected || MC.player == null || MC.getSingleplayerServer() == null
                || !MC.getSingleplayerServer().getWorldData().getLevelName().equals("Autocrafting verification")
                || EmiAutocrafting.state() != JobController.State.WAITING) return;
        var menu = MC.player.containerMenu;
        if (!menu.getClass().getName().equals("com.leclowndu93150.craftingstationjei.menu.CraftingStationMenu")) return;
        if (menu.slots.size() > 9 && menu.getSlot(1).getItem().is(Items.OAK_LOG) && menu.getCarried().isEmpty()) {
            // Model a delayed block-entity inventory refresh after the menu's transfer update.
            // Only the disposable world's client-side backing inventory changes; the server keeps
            // its real post-transfer quantity. Native menu packets must remain authoritative.
            StorageFixtures.staleClientStorage(MC.level);
            staleStorageInjected = true;
            report("INJECT delayed client block-entity inventory: restore pre-transfer log count");
        }
    }
    private void tick(ClientTickEvent.Post e){
        if (stage == 99) return; // MC.stop() can take several ticks; report completion only once.
        ticks++;
        if(MC.options!=null) { MC.options.pauseOnLostFocus=false; if(ticks==1) { MC.options.enableVsync().set(false); MC.options.framerateLimit().set(30); MC.options.renderDistance().set(2); MC.options.simulationDistance().set(5); } }
        if (ticks % 200 == 0) {
            System.out.println("[AUTOCRAFT HARNESS] stage="+stage+" screen="+(MC.screen==null?"none":MC.screen.getClass().getSimpleName()));
            if (launched) screenshot("progress");
            if (Files.exists(Path.of("autocrafting-test-stop"))) { finish(); return; }
        }
        try {
            if (reportedPath && MC.screen instanceof DisconnectedScreen && !disconnecting) {
                report("HARNESS ERROR unexpected disconnect during fixture " + (test<CASES.size()?CASES.get(test).name():"completion"));
                finish(); return;
            }
            if (launched && !reportedPath && MC.screen instanceof TitleScreen && MC.level == null && ticks-at>400) {
                if (worldAttempts >= 3) { report("HARNESS ERROR world loading failed after three attempts"); finish(); return; }
                report("RETRY returned to title before world loading completed"); launched=false;
            }
            if (!launched && MC.screen instanceof AccessibilityOnboardingScreen) MC.setScreen(new TitleScreen());
            if(!launched && MC.screen instanceof TitleScreen && MC.getOverlay()==null){
                launched=true; worldAttempts++;
                report("PASS title_screen mods="+net.neoforged.fml.ModList.get().size());
                screenshot("title");
                if(mode.startsWith("dedicated")){
                    ConnectScreen.startConnecting(MC.screen,MC,ServerAddress.parseString("127.0.0.1:"+System.getProperty("emiautocrafting.testPort","25565")),new ServerData("Autocrafting test","127.0.0.1:"+System.getProperty("emiautocrafting.testPort","25565"),ServerData.Type.OTHER),false,null);
                }else{
                    MC.createWorldOpenFlows().createFreshLevel("autocrafting-test-"+System.currentTimeMillis(),
                        new LevelSettings("Autocrafting verification",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),MC.screen);
                }
                at=ticks;return;
            }
            if(disconnecting) {
                if(EmiAutocrafting.state()==JobController.State.CANCELLED) { report("PASS disconnect state=CANCELLED; future actions stopped"); finish(); }
                return;
            }
            if(MC.player==null||MC.level==null||MC.getConnection()==null)return;
            if (Boolean.getBoolean("emiautocrafting.clientFillOnly")) dev.emi.emi.platform.EmiClient.onServer=false;
            if(test>=CASES.size()){finish();return;}
            if(EmiApi.getRecipeManager().getRecipe(ResourceLocation.withDefaultNamespace("wooden_pickaxe"))==null)return;
            if(!reportedPath) {
                com.example.emiautocrafting.EmiAutocraftingConfig.DIAGNOSTICS.set(true);
                if (System.getProperty("emiautocrafting.testPace") != null)
                    com.example.emiautocrafting.EmiAutocraftingConfig.PACE.set(Integer.getInteger("emiautocrafting.testPace"));
                reportedPath=true; report("PATH mode="+mode+" EMI server="+dev.emi.emi.platform.EmiClient.onServer);
                if(Boolean.getBoolean("emiautocrafting.packagedTest")) {
                    String source=EmiAutocrafting.class.getProtectionDomain().getCodeSource().getLocation().toString();
                    String version = net.neoforged.fml.ModList.get().getModContainerById("emiautocrafting").orElseThrow().getModInfo().getVersion().toString();
                    if(!source.contains("emiautocrafting-neoforge-1.21.1-"+version+".jar"))
                        throw new IllegalStateException("Expected packaged addon, loaded from "+source);
                    report("PASS packaged_addon_loaded source="+source);
                }
            }
            Scenario c=CASES.get(test);
            if(stage==0){
                seededGrid=false;
                reloadSent=false;
                fixture=null;
                MC.player.closeContainer();MC.setScreen(null);
                command("gamemode survival @s");command("gamerule doLimitedCrafting false");command("gamerule doMobSpawning false");command("gamerule doDaylightCycle false");
                command("fill -2 99 -2 3 99 3 stone");command("tp @s 0.5 100 0.5");
                if (Boolean.getBoolean("emiautocrafting.storageCompatibility")) command("fill 1 100 -1 3 102 1 air");
                command("setblock 1 100 0 "+menuBlock(c));
                command("clear @s");for(String cmd:c.setup())command(cmd);
                if(c.name().equals("full_inventory")) {
                    for(int i=1;i<9;i++)command("item replace entity @s hotbar."+i+" with stone 64");
                    for(int i=0;i<27;i++)command("item replace entity @s inventory."+i+" with stone 64");
                }
                stage=1;at=ticks;
            }else if(stage==1&&ticks-at>50
                    &&MC.player.distanceToSqr(0.5,100,0.5)<4
                    &&(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(TABLE).getBlock()).toString().equals(menuBlock(c)) || c.name().contains("ae2_") && fixture==null)){
                if (c.name().startsWith("storage_")) {
                    if (fixture == null) {
                        var id = MC.player.getUUID();
                        fixture = MC.getSingleplayerServer().submit(() -> StorageFixtures.setup(MC.getSingleplayerServer().getPlayerList().getPlayer(id), c.name()));
                        at=ticks; return;
                    }
                    fixture.join();
                    if (MC.player.getInventory().items.stream().anyMatch(s -> !s.isEmpty())) throw new IllegalStateException("Storage-only fixture has player items");
                }
                if(c.inventory())MC.setScreen(new InventoryScreen(MC.player));
                else if(c.name().startsWith("storage_ae2")) {
                    var id=MC.player.getUUID(); MC.getSingleplayerServer().execute(() -> StorageFixtures.open(MC.getSingleplayerServer().getPlayerList().getPlayer(id)));
                }
                else MC.gameMode.useItemOn(MC.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TABLE),Direction.UP,TABLE,false));
                stage=2;at=ticks;
            }else if(stage==2&&ticks-at>20&&EmiBridge.screen()!=null){
                if (c.name().contains("_grid_") && c.name().startsWith("storage_") && !seededGrid) {
                    seededGrid=true;
                    var id=MC.player.getUUID();
                    MC.getSingleplayerServer().submit(() -> StorageFixtures.seedGrid(MC.getSingleplayerServer().getPlayerList().getPlayer(id), c.name())).join();
                    at=ticks; return;
                }
                EmiRecipe recipe=EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(c.recipe().contains(":")?c.recipe():"minecraft:"+c.recipe()));
                if (c.name().startsWith("kubejs_")) {
                    var raw=EmiBridge.rawRecipe(recipe);
                    System.out.println("[KUBEJS RECIPE] "+recipe.getId()+" raw="+(raw==null?"missing":raw.value().getClass().getName()));
                }
                var preferredIds = new ArrayList<>(List.of("minecraft:oak_planks","minecraft:stick"));
                if (c.name().startsWith("tree_")) preferredIds.add("minecraft:stone");
                if (c.name().contains("quark_")) preferredIds.add("quark:building/crafting/chests/mixed_chest_wood");
                for(String id:preferredIds){
                    var preferred=EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(id));if(preferred!=null)BoM.addRecipe(preferred);
                }
                EmiBridge.prepare(new EmiBridge.Selection(recipe,recipe.getOutputs().getFirst(),c.target()),c.target());
                if (c.name().contains("quark_")) resolveChests(BoM.tree.goal, new HashSet<>());
                else if(!c.name().equals("modded_components_alternative"))resolve(BoM.tree.goal,new HashSet<>());
                if (c.name().equals("tree_unhearted_stone") || c.name().equals("tree_local_stone_choice")) {
                    var smelting = EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse("minecraft:stone"));
                    if (c.name().equals("tree_local_stone_choice")) BoM.tree.addResolution(EmiStack.of(Items.STONE), smelting);
                    BoM.removeRecipe(smelting);
                }
                if(c.name().equals("preexisting_grid")||c.name().equals("occupied_cursor")) {
                    int id=MC.player.containerMenu.containerId;
                    MC.gameMode.handleInventoryMouseClick(id,37,0,ClickType.PICKUP,MC.player);
                    if(c.name().equals("preexisting_grid")) {
                        MC.gameMode.handleInventoryMouseClick(id,1,1,ClickType.PICKUP,MC.player);
                        MC.gameMode.handleInventoryMouseClick(id,37,0,ClickType.PICKUP,MC.player);
                    }
                }
                if (!testedControls) { testedControls=true; testControls(recipe); }
                int startKey=c.name().endsWith("single_step")?78:67;
                int startMods=c.name().endsWith("single_step")?0:2;
                // Storage menus may open with their search box focused; mimic clicking out of it.
                if (c.name().startsWith("storage_")) {
                    for (var child : MC.screen.children()) child.setFocused(false);
                    MC.screen.setFocused(null);
                    new com.example.emiautocrafting.client.MenuPort(EmiBridge.screen(), EmiBridge.freeze());
                }
                var keyEvent=new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(MC.screen,startKey,0,startMods);
                NeoForge.EVENT_BUS.post(keyEvent);
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.KeyReleased.Pre(MC.screen,startKey,0,startMods));
                if(!keyEvent.isCanceled()||EmiAutocrafting.state()!=JobController.State.PLANNING)
                    throw new IllegalStateException("Start shortcut did not start "+c.name()+": "+EmiAutocrafting.status());
                stage=3;at=ticks;
            }else if(stage==2&&ticks-at>100&&EmiBridge.screen()==null){
                stage=1;at=ticks-51;
            }else if(stage==3){
                if (!testedGroup && ticks-at>2 && com.example.emiautocrafting.emi.JobSidebar.bounds(MC.screen)!=null) {
                    testedGroup=true; testGroup(); screenshot("grouped-batch");
                }
                var state=EmiAutocrafting.state();
                if (state==JobController.State.WAITING && c.name().equals("disconnect")) {
                    disconnecting=true;
                    // Match PauseScreen's exit sequence: close the level connection before waiting for the integrated server.
                    MC.level.disconnect(); MC.disconnect(new TitleScreen()); return;
                }
                if (state==JobController.State.WAITING && c.name().equals("recipe_reload") && !reloadSent) { reloadSent=true; command("reload"); }
                if (state==JobController.State.WAITING && c.name().equals("closed_gui")) { MC.player.closeContainer(); MC.setScreen(null); }
                if (state==JobController.State.WAITING && c.name().equals("changed_tree")) BoM.tree.batches++;
                if(state==JobController.State.COMPLETED||state==JobController.State.BLOCKED||state==JobController.State.FAILED||state==JobController.State.CANCELLED){
                    Item output = c.output() == null ? BoM.tree.goal.ingredient.getEmiStacks().getFirst().getItemStack().getItem() : c.output();
                    long count=MC.player.getInventory().items.stream().filter(s->s.is(output)).mapToLong(ItemStack::getCount).sum();
                    boolean pass=c.blocked()==null?state==JobController.State.COMPLETED&&count==c.expected():c.blocked().equals("CANCELLED")?state==JobController.State.CANCELLED:state==JobController.State.BLOCKED&&EmiAutocrafting.status().toLowerCase(Locale.ROOT).contains(c.blocked().toLowerCase(Locale.ROOT));
                    if (c.name().equals("tree_smelting_blocked") || c.name().equals("tree_local_stone_choice")) {
                        var problem = EmiAutocrafting.problem();
                        pass &= problem != null && problem.recipeId().equals("minecraft:stone") && problem.item().equals("Stone")
                                && problem.path().equals(List.of("Redstone Repeater", "Stone"))
                                && MC.screen instanceof com.example.emiautocrafting.client.CraftingProblemScreen;
                        if (c.name().equals("tree_local_stone_choice")) pass &= problem != null && problem.selection().contains("inside this tree");
                        pass &= MC.player.getInventory().countItem(Items.COBBLESTONE) == 3;
                    }
                    if (c.name().equals("tree_unhearted_stone")) pass &= EmiAutocrafting.problem() == null;
                    if (c.name().equals("quark_same_wood")) {
                        pass &= MC.player.getInventory().countItem(Items.OAK_LOG) == 8
                                && EmiAutocrafting.problem() != null
                                && EmiAutocrafting.problem().recipeId().equals(c.recipe())
                                && MC.player.containerMenu.slots.stream().skip(1).limit(9).allMatch(s -> s.getItem().isEmpty());
                    }
                    if(c.name().equals("damageable_tool_breaks"))pass&=count==3;
                    if(c.name().startsWith("storage_")) {
                        var jobField=EmiAutocrafting.class.getDeclaredField("JOB"); jobField.setAccessible(true);
                        Object job=jobField.get(null);
                        var operations=JobController.class.getDeclaredField("operations"); operations.setAccessible(true);
                        long dispatches=operations.getLong(job);
                        if (!c.name().contains("station")) {
                            if (c.name().endsWith("batch") || c.name().endsWith("three_cakes")) pass &= dispatches==3;
                            if (c.name().endsWith("exact20") || c.name().endsWith("existing_total") || c.name().endsWith("single_step") || c.name().endsWith("grid_reuse") || c.name().endsWith("grid_clear")) pass &= dispatches==1;
                            report("METRIC "+c.name()+" craftConfirmations="+dispatches);
                        }
                        var id=MC.player.getUUID();
                        String conservation=MC.getSingleplayerServer().submit(() -> StorageFixtures.check(MC.getSingleplayerServer().getPlayerList().getPlayer(id), c.name())).join();
                        report(conservation);
                        pass &= conservation.startsWith("PASS");
                        if(c.name().endsWith("missing")) pass &= MC.screen instanceof com.example.emiautocrafting.client.MissingItemsScreen;
                        if (c.name().endsWith("multi_missing")) {
                            var field = com.example.emiautocrafting.client.MissingItemsScreen.class.getDeclaredField("items");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked") var shortages = (List<Map.Entry<String,Long>>) field.get(MC.screen);
                            pass &= shortages.size()==2 && shortages.stream().anyMatch(v -> v.getKey().startsWith("Diamond") && v.getValue()==2)
                                    && shortages.stream().anyMatch(v -> v.getKey().startsWith("Redstone Dust") && v.getValue()==3);
                        }
                    }
                    if(c.name().startsWith("kubejs_") && c.blocked()!=null) {
                        pass&=count==c.expected();
                        if(c.name().startsWith("kubejs_scripted_")) {
                            pass&=MC.player.getInventory().items.stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum()==1;
                            pass&=MC.player.containerMenu.slots.stream().skip(1).limit(c.inventory()?4:9).allMatch(s->s.getItem().isEmpty());
                        }
                    }
                    if(c.name().equals("reusable_tool")) {
                        var stamp=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("autocrafting_test:test_stamp"));
                        long tools=MC.player.getInventory().items.stream().filter(s->s.is(stamp)).mapToInt(ItemStack::getCount).sum()
                            +MC.player.containerMenu.slots.stream().limit(10).filter(s->s.getItem().is(stamp)).mapToInt(s->s.getItem().getCount()).sum();
                        pass&=tools==1;
                    }
                    if(c.name().equals("returned_buckets"))pass&=MC.player.getInventory().items.stream().filter(s->s.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum()+MC.player.containerMenu.slots.stream().limit(10).filter(s->s.getItem().is(Items.BUCKET)).mapToInt(s->s.getItem().getCount()).sum()>=3;
                    if (c.name().equals("quark_compass_components_stop")) {
                        pass &= count==2 && MC.player.containerMenu.getCarried().isEmpty()
                                && MC.player.getInventory().countItem(Items.COPPER_INGOT)==0
                                && MC.player.getInventory().countItem(Items.BIRCH_PLANKS)==0;
                    }
                    report((pass?"PASS ":"FAIL ")+c.name()+" state="+state+" count="+count+" ticks="+(ticks-at)+" status="+EmiAutocrafting.status());
                    stage=4;at=ticks; // Let the final/shortage screen render before capturing it.
                }else if(ticks-at>1800)throw new IllegalStateException("Test timeout: "+c.name()+" "+state+" "+EmiAutocrafting.status());
            }else if(stage==4 && ticks-at>=4) {
                screenshot(c.name());test++;stage=0;at=ticks;
            }
        }catch(Throwable error){report("HARNESS ERROR "+error);error.printStackTrace();finish();}
    }
    private void resolve(MaterialNode n,Set<EmiRecipe> path){
        if(n.recipe!=null&&!path.add(n.recipe))return;
        if(n.ingredient.getEmiStacks().size()>1){
            for(Item preferred:List.of(Items.OAK_PLANKS,Items.OAK_LOG)){
                EmiStack chosen=EmiStack.of(preferred);
                if(n.ingredient.getEmiStacks().stream().anyMatch(s->s.isEqual(chosen))){BoM.tree.addResolution(n.ingredient,new EmiResolutionRecipe(n.ingredient,chosen));break;}
            }
        }
        if(n.children!=null)for(MaterialNode child:List.copyOf(n.children))resolve(child,new HashSet<>(path));
    }
    private void resolveChests(MaterialNode node, Set<EmiRecipe> path) {
        if (node.recipe != null && !path.add(node.recipe)) return;
        if (node.ingredient.getEmiStacks().size() > 1 && node.ingredient.getEmiStacks().stream().anyMatch(s -> s.getItemStack().is(Items.CHEST)))
            BoM.tree.addResolution(node.ingredient, new EmiResolutionRecipe(node.ingredient, EmiStack.of(Items.CHEST)));
        if (node.children != null) for (var child : List.copyOf(node.children)) resolveChests(child, new HashSet<>(path));
    }

    private void testControls(EmiRecipe recipe) {
        Screen parent = MC.screen;
        var previous = parent.getFocused();
        var text = new net.minecraft.client.gui.components.EditBox(MC.font, 0, 0, 100, 20, net.minecraft.network.chat.Component.literal("test"));
        text.setValue("selection"); parent.setFocused(text); text.setFocused(true);
        for (int key : List.of(65,67)) {
            var event = new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(parent,key,0,2);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) throw new IllegalStateException("Text control shortcut was intercepted");
        }
        parent.setFocused(previous);
        dev.emi.emi.screen.EmiScreenManager.search.setFocused(true);
        var event = new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(parent,65,0,2);
        NeoForge.EVENT_BUS.post(event);
        dev.emi.emi.screen.EmiScreenManager.search.setFocused(false);
        if(event.isCanceled())throw new IllegalStateException("EMI search select-all was intercepted");
        report("PASS keyboard_event_text_focus (vanilla EditBox and EMI search)");
        var dialog = new com.example.emiautocrafting.client.QuantityScreen(parent,new EmiBridge.Selection(recipe,recipe.getOutputs().getFirst(),1));
        var saved = BoM.tree;
        MC.setScreen(dialog);
        ((net.minecraft.client.gui.components.EditBox) dialog.getFocused()).setValue("5");
        dialog.keyPressed(257,0,0);
        if (EmiBridge.freeze().total()!=5 || BoM.tree.goal.recipe!=recipe)throw new IllegalStateException("Quantity dialog lost recipe/total");
        BoM.tree=saved; MC.setScreen(parent);
        report("PASS quantity_dialog_enter_keeps_recipe_and_item_total");
    }
    private void testGroup() {
        var marker = new dev.emi.emi.runtime.EmiFavorite(EmiStack.of(Items.DIAMOND), null);
        dev.emi.emi.runtime.EmiFavorites.favorites.add(marker);
        var favourites = List.copyOf(dev.emi.emi.runtime.EmiFavorites.favorites);
        try {
            dev.emi.emi.runtime.EmiFavorites.updateSynthetic(new dev.emi.emi.api.recipe.EmiPlayerInventory(List.of()));
            if (!dev.emi.emi.runtime.EmiFavorites.syntheticFavorites.isEmpty() || !dev.emi.emi.runtime.EmiFavorites.favorites.equals(favourites))
                throw new IllegalStateException("Grouped batch changed ordinary favourites or left duplicate tree items");
            var b = com.example.emiautocrafting.emi.JobSidebar.bounds(MC.screen);
            if (b == null && !BoM.craftingMode) {
                report("PASS completed_batch follows EMI completion and hides the finished panel"); return;
            }
            if (b == null) throw new IllegalStateException("Active batch panel disappeared");
            var state = EmiAutocrafting.state();
            com.example.emiautocrafting.emi.JobSidebar.click(MC.screen, b.x()+5, b.y()+5, 0);
            if (com.example.emiautocrafting.emi.JobSidebar.bounds(MC.screen).height()!=22 || EmiAutocrafting.state()!=state)
                throw new IllegalStateException("Collapsing the batch changed crafting state");
            com.example.emiautocrafting.emi.JobSidebar.click(MC.screen, b.x()+5, b.y()+5, 0);
            report("PASS grouped_batch collapses without cancelling; ordinary favourites preserved; no duplicate synthetic entries");
        } finally { dev.emi.emi.runtime.EmiFavorites.favorites.remove(marker); }
    }
    private void command(String text){
        var server=MC.getSingleplayerServer();
        if(server==null) { MC.getConnection().sendCommand(text); return; }
        var id=MC.player.getUUID();
        server.execute(() -> {
            if(!server.getWorldData().getLevelName().equals("Autocrafting verification")) throw new IllegalStateException("Fixture commands require the disposable test world");
            var player=server.getPlayerList().getPlayer(id);
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),text);
        });
    }
    private String menuBlock(Scenario c) {
        if (c.name().contains("station_")) return "craftingstation:crafting_station";
        if (c.name().contains("lectern_")) return "ars_nouveau:storage_lectern";
        if (c.name().contains("ae2_")) return "ae2:cable_bus";
        return "minecraft:crafting_table";
    }
    private void screenshot(String name) {
        net.minecraft.client.Screenshot.grab(MC.gameDirectory, "autocrafting-"+mode+"-"+name+".png", MC.getMainRenderTarget(), message -> {});
    }
    private void report(String line){reports.add(line);System.out.println("[AUTOCRAFT TEST] "+line);try{Files.write(Path.of("runtime-tests-"+mode+".txt"),reports);}catch(Exception ignored){}}
    private void finish(){try{Files.write(Path.of("runtime-tests-"+mode+".txt"),reports);}catch(Exception ignored){} MC.stop();stage=99;test=CASES.size();}
}
