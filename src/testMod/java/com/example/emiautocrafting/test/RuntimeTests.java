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
        new Scenario("player_requires_3x3","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"3×3",List.of("give @s oak_planks 3","give @s stick 2"),true)
    ));
    static {
        if(Boolean.getBoolean("emiautocrafting.toolFixtures")) {
            CASES.add(new Scenario("reusable_tool","autocrafting_test:reusable_tool",Items.GOLD_NUGGET,4,4,null,List.of("give @s autocrafting_test:test_stamp 1","give @s iron_ingot 4"),false));
            CASES.add(new Scenario("damageable_tool_breaks","autocrafting_test:damageable_tool",Items.GOLD_NUGGET,4,3,"Missing",List.of("give @s autocrafting_test:test_hammer 1","give @s iron_ingot 4"),false));
        }
    }
    static {
        if (Boolean.getBoolean("emiautocrafting.packCompatibility")) {
            CASES.add(new Scenario("pack_furnace_shared_stock","furnace",Items.FURNACE,1,1,null,
                List.of("give @s cobblestone 40","give @s coal 9"),false));
            CASES.add(new Scenario("pack_furnace_existing","furnace",Items.FURNACE,2,2,null,
                List.of("give @s furnace 1","give @s cobblestone 4","give @s allthecompressed:cobblestone_1x 4","give @s coal_block 1"),false));
            CASES.add(new Scenario("pack_piston_chain","piston",Items.PISTON,3,3,null,
                List.of("give @s piston 1","give @s oak_log 2","give @s cobblestone 8","give @s redstone 2","give @s create:andesite_alloy 2"),false));
            CASES.add(new Scenario("pack_missing_machine","piston",Items.PISTON,1,0,"unsupported machine",
                List.of("give @s oak_planks 3","give @s cobblestone 4","give @s redstone 1"),false));
            CASES.add(new Scenario("pack_scripted_remainder","autocrafting_test:scripted_remainder",Items.GOLD_NUGGET,1,0,"unsupported",
                List.of("give @s iron_ingot 1","give @s stick 1"),false));
            CASES.add(new Scenario("pack_scripted_output","autocrafting_test:scripted_output",Items.DIAMOND,1,0,"unsupported",
                List.of("give @s iron_ingot 1","give @s coal 1"),false));
            CASES.add(new Scenario("pack_plain_shapeless","autocrafting_test:plain_kubejs_shapeless",Items.EMERALD,1,1,null,
                List.of("give @s copper_ingot 1","give @s coal 1"),true));
            CASES.add(new Scenario("pack_station_guard","oak_planks",Items.OAK_PLANKS,4,0,"vanilla",
                List.of("give @s oak_log 1"),false));
        }
    }
    static {
        CASES.add(new Scenario("recipe_reload","wooden_pickaxe",Items.WOODEN_PICKAXE,100,0,"CANCELLED",List.of("give @s oak_log 200"),false));
        CASES.add(new Scenario("disconnect","wooden_pickaxe",Items.WOODEN_PICKAXE,1,0,"CANCELLED",List.of("give @s oak_log 2"),false));
        String filter=System.getProperty("emiautocrafting.testFilter","all");
        if(!filter.equals("all")) { var allowed=Set.of(filter.split(",")); CASES.removeIf(c->!allowed.contains(c.name())); }
    }
    private long ticks, at;
    private int stage, test;
    private boolean launched;
    private final String mode=System.getProperty("emiautocrafting.testMode","integrated");
    private final List<String> reports=new ArrayList<>();
    private boolean reportedPath, testedControls, disconnecting, reloadSent;
    public RuntimeTests(net.neoforged.bus.api.IEventBus bus){
        if(Boolean.getBoolean("emiautocrafting.integration"))NeoForge.EVENT_BUS.addListener(this::tick);
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
    private void tick(ClientTickEvent.Post e){
        if (stage == 99) return; // MC.stop() can take several ticks; report completion only once.
        ticks++;
        if(MC.options!=null) { MC.options.pauseOnLostFocus=false; if(ticks==1) { MC.options.framerateLimit().set(30); MC.options.renderDistance().set(2); MC.options.simulationDistance().set(5); } }
        if (ticks % 200 == 0) System.out.println("[AUTOCRAFT HARNESS] stage="+stage+" screen="+(MC.screen==null?"none":MC.screen.getClass().getSimpleName()));
        try {
            if (!launched && MC.screen instanceof AccessibilityOnboardingScreen) MC.setScreen(new TitleScreen());
            if(!launched && MC.screen instanceof TitleScreen && MC.getOverlay()==null){
                launched=true;
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
            if(test>=CASES.size()){finish();return;}
            if(EmiApi.getRecipeManager().getRecipe(ResourceLocation.withDefaultNamespace("wooden_pickaxe"))==null)return;
            if(!reportedPath) {
                reportedPath=true; report("PATH mode="+mode+" EMI server="+dev.emi.emi.platform.EmiClient.onServer);
                if(Boolean.getBoolean("emiautocrafting.packagedTest")) {
                    String source=EmiAutocrafting.class.getProtectionDomain().getCodeSource().getLocation().toString();
                    if(!source.contains("emiautocrafting-neoforge-1.21.1-2.0.0-beta.2.jar"))
                        throw new IllegalStateException("Expected packaged addon, loaded from "+source);
                    report("PASS packaged_addon_loaded source="+source);
                }
            }
            Scenario c=CASES.get(test);
            if(stage==0){
                reloadSent=false;
                MC.player.closeContainer();MC.setScreen(null);
                command("gamemode survival @s");command("gamerule doLimitedCrafting false");command("gamerule doMobSpawning false");command("gamerule doDaylightCycle false");
                command("fill -2 99 -2 3 99 3 stone");command("tp @s 0.5 100 0.5");command("setblock 1 100 0 "+(c.name().equals("pack_station_guard") ? "craftingstation:crafting_station" : "minecraft:crafting_table"));
                command("clear @s");for(String cmd:c.setup())command(cmd);
                if(c.name().equals("full_inventory")) {
                    for(int i=1;i<9;i++)command("item replace entity @s hotbar."+i+" with stone 64");
                    for(int i=0;i<27;i++)command("item replace entity @s inventory."+i+" with stone 64");
                }
                stage=1;at=ticks;
            }else if(stage==1&&ticks-at>50
                    &&MC.player.distanceToSqr(0.5,100,0.5)<4
                    &&net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(TABLE).getBlock()).toString().equals(c.name().equals("pack_station_guard") ? "craftingstation:crafting_station" : "minecraft:crafting_table")){
                if(c.inventory())MC.setScreen(new InventoryScreen(MC.player));
                else MC.gameMode.useItemOn(MC.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TABLE),Direction.UP,TABLE,false));
                stage=2;at=ticks;
            }else if(stage==2&&ticks-at>20&&EmiBridge.screen()!=null){
                EmiRecipe recipe=EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(c.recipe().contains(":")?c.recipe():"minecraft:"+c.recipe()));
                if (c.name().startsWith("pack_")) {
                    var raw=EmiBridge.rawRecipe(recipe);
                    System.out.println("[PACK RECIPE] "+recipe.getId()+" raw="+(raw==null?"missing":raw.value().getClass().getName()));
                }
                var preferredIds = new ArrayList<>(List.of("minecraft:oak_planks","minecraft:stick"));
                if (Boolean.getBoolean("emiautocrafting.packCompatibility")) preferredIds.addAll(List.of(
                    "minecraft:coal_block", "allthecompressed:compress/cobblestone_1x", "create:shaft", "jei:/create/mixing/andesite_alloy"));
                for(String id:preferredIds){
                    var preferred=EmiApi.getRecipeManager().getRecipe(ResourceLocation.parse(id));if(preferred!=null)BoM.addRecipe(preferred);
                }
                EmiBridge.prepare(new EmiBridge.Selection(recipe,recipe.getOutputs().getFirst(),c.target()),c.target());
                if(!c.name().equals("modded_components_alternative"))resolve(BoM.tree.goal,new HashSet<>());
                if(c.name().equals("preexisting_grid")||c.name().equals("occupied_cursor")) {
                    int id=MC.player.containerMenu.containerId;
                    MC.gameMode.handleInventoryMouseClick(id,37,0,ClickType.PICKUP,MC.player);
                    if(c.name().equals("preexisting_grid")) {
                        MC.gameMode.handleInventoryMouseClick(id,1,1,ClickType.PICKUP,MC.player);
                        MC.gameMode.handleInventoryMouseClick(id,37,0,ClickType.PICKUP,MC.player);
                    }
                }
                if (!testedControls) { testedControls=true; testControls(recipe); }
                if (c.name().equals("pack_station_guard")) {
                    try { new com.example.emiautocrafting.client.MenuPort(EmiBridge.screen(),EmiBridge.freeze());
                        throw new IllegalStateException("Unverified station was accepted");
                    } catch (IllegalArgumentException expected) {
                        if (!expected.getMessage().contains("vanilla")) throw expected;
                        report("PASS pack_station_guard rejected unsupported station before any inventory operation");
                        test++;stage=0;at=ticks;return;
                    }
                }
                int startKey=c.name().equals("single_step")?78:67;
                int startMods=c.name().equals("single_step")?0:2;
                var keyEvent=new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(MC.screen,startKey,0,startMods);
                NeoForge.EVENT_BUS.post(keyEvent);
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.KeyReleased.Pre(MC.screen,startKey,0,startMods));
                if(!keyEvent.isCanceled()||EmiAutocrafting.state()!=JobController.State.PLANNING)
                    throw new IllegalStateException("Start shortcut did not start "+c.name()+": "+EmiAutocrafting.status());
                stage=3;at=ticks;
            }else if(stage==2&&ticks-at>100&&EmiBridge.screen()==null){
                stage=1;at=ticks-51;
            }else if(stage==3){
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
                    long count=MC.player.getInventory().items.stream().filter(s->s.is(c.output())).mapToLong(ItemStack::getCount).sum();
                    boolean pass=c.blocked()==null?state==JobController.State.COMPLETED&&count==c.expected():c.blocked().equals("CANCELLED")?state==JobController.State.CANCELLED:state==JobController.State.BLOCKED&&EmiAutocrafting.status().toLowerCase(Locale.ROOT).contains(c.blocked().toLowerCase(Locale.ROOT));
                    if(c.name().equals("damageable_tool_breaks"))pass&=count==3;
                    if(c.name().startsWith("pack_") && c.blocked()!=null) {
                        pass&=count==c.expected();
                        if(c.name().startsWith("pack_scripted_")) {
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
                    report((pass?"PASS ":"FAIL ")+c.name()+" state="+state+" count="+count+" status="+EmiAutocrafting.status());
                    test++;stage=0;at=ticks;
                }else if(ticks-at>1800)throw new IllegalStateException("Test timeout: "+c.name()+" "+state+" "+EmiAutocrafting.status());
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
    private void command(String text){MC.getConnection().sendCommand(text);}
    private void report(String line){reports.add(line);System.out.println("[AUTOCRAFT TEST] "+line);try{Files.write(Path.of("runtime-tests-"+mode+".txt"),reports);}catch(Exception ignored){}}
    private void finish(){try{Files.write(Path.of("runtime-tests-"+mode+".txt"),reports);}catch(Exception ignored){} MC.stop();stage=99;test=CASES.size();}
}
