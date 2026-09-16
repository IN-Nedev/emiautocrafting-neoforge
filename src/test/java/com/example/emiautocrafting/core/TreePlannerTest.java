// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TreePlannerTest {
    TreePlanner.Node<String,String> leaf(String key, long count) { return node(key, count, 1, null, List.of()); }
    TreePlanner.Node<String,String> node(String key, long amount, long yield, String recipe, List<TreePlanner.Node<String,String>> inputs) {
        return new TreePlanner.Node<>(key,List.of(key),amount,key,yield,recipe,inputs,List.of(),false,null);
    }
    TreePlanner.Node<String,String> planks(long count) { return node("plank",count,4,"planks",List.of(leaf("log",1))); }
    TreePlanner.Node<String,String> pickaxe() {
        return node("pickaxe",1,1,"pickaxe",List.of(planks(3),node("stick",2,4,"sticks",List.of(planks(2)))));
    }
    TreePlanner.Plan<String,String> plan(TreePlanner.Node<String,String> goal, long amount, Map<String,Long> stock) { return new TreePlanner<String,String>().plan(goal,amount,stock); }
    @Test void logsToPickaxePlan() {
        var p=plan(pickaxe(),1,Map.of("log",2L));
        assertTrue(p.missing().isEmpty()); assertNull(p.obstacle());
        assertEquals(List.of("planks","planks","sticks","pickaxe"),p.steps().stream().map(s->s.node().recipe()).toList());
    }
    @Test void actualResultsRecalculateWholeChain() {
        Map<String,Long> stock = new HashMap<>(Map.of("log",2L));
        int operations=0;
        while(true) {
            var p=plan(pickaxe(),1,stock); if(p.complete()) break;
            assertTrue(operations++<8); assertNull(p.obstacle()); assertTrue(p.missing().isEmpty());
            var step=p.steps().getFirst();
            for(var in:step.node().inputs()) stock.compute(in.output(),(k,v)->v-in.amount());
            stock.merge(step.node().output(),step.node().outputCount(),Long::sum);
        }
        assertEquals(4,operations); assertEquals(1L,stock.get("pickaxe"));
    }
    @Test void existingIntermediateSkipsProduction() {
        var p=plan(pickaxe(),1,Map.of("plank",3L,"stick",2L));
        assertEquals(List.of("pickaxe"),p.steps().stream().map(s->s.node().recipe()).toList());
    }
    @Test void existingFinalAndBatchSurplus() {
        var p=plan(planks(1),5,Map.of("plank",1L,"log",1L));
        assertEquals(1,p.steps().getFirst().batches());
        assertTrue(plan(pickaxe(),1,Map.of("pickaxe",1L)).complete());
    }
    @Test void sameStockCannotFundTwoBranches() {
        var goal=node("goal",1,1,"goal",List.of(leaf("iron",3),leaf("iron",4)));
        assertEquals(3L,plan(goal,1,Map.of("iron",4L)).missing().get("iron"));
    }
    @Test void earlierBranchReservationIsExcludedFromNextStepBudget() {
        var p=plan(pickaxe(),1,Map.of("plank",3L,"log",1L));
        assertEquals("planks",p.steps().getFirst().node().recipe());
        assertEquals(0L,p.steps().getFirst().available().get("plank"));
    }
    @Test void alternativesUseExistingStock() {
        var alternatives = new TreePlanner.Node<String,String>("planks",List.of("oak","birch"),2,"oak",1,null,List.of(),List.of(),false,null);
        assertTrue(plan(alternatives,2,Map.of("birch",2L)).complete());
    }
    @Test void unsupportedMachineOnlyBlocksWhenStockIsMissing() {
        var machine = new TreePlanner.Node<String,String>("plate",List.of("plate"),1,"plate",1,"machine",List.of(),List.of(),false,"Requires a machine");
        assertTrue(plan(machine,1,Map.of("plate",1L)).complete());
        assertEquals("Requires a machine",plan(machine,1,Map.of()).obstacle());
    }
    @Test void blockedRecipeKeepsItsDependencyPathAndFirstCause() {
        var stone = new TreePlanner.Node<String,String>("Stone", List.of("stone"), 3, "stone", 1,
                "smelting/stone", List.of(), List.of(), false, "Requires smelting");
        var coal = new TreePlanner.Node<String,String>("Charcoal", List.of("coal"), 1, "coal", 1,
                "smelting/charcoal", List.of(), List.of(), false, "Requires smelting");
        var root = node("Manager", 1, 1, "manager", List.of(node("Repeater", 1, 1, "repeater", List.of(stone)), coal));
        var p = plan(root, 1, Map.of());
        assertSame(stone, p.blockedNode());
        assertEquals(List.of("Manager", "Repeater", "Stone"), p.blockedPath());
        var supplied = plan(root, 1, Map.of("stone", 3L, "coal", 1L));
        assertNull(supplied.obstacle()); assertNull(supplied.blockedNode()); assertTrue(supplied.blockedPath().isEmpty());
    }
    @Test void unresolvedAndMissingAreExplicit() {
        assertEquals(2L,plan(leaf("iron",1),3,Map.of("iron",1L)).missing().get("iron"));
        var unresolved=new TreePlanner.Node<String,String>("plank",List.of("plank"),1,"plank",1,null,List.of(),List.of(),false,"Select a recipe");
        assertEquals("Select a recipe",plan(unresolved,1,Map.of()).obstacle());
    }
    @Test void repeatedRecipeInPathTerminates() {
        var child=node("child",1,1,"loop",List.of(leaf("missing",1)));
        var root=node("root",1,1,"loop",List.of(child));
        assertTrue(plan(root,1,Map.of()).obstacle().contains("Cyclic"));
    }
    @Test void excessiveArithmeticFailsClosed() {
        var root=node("root",1,1,"root",List.of(leaf("input",Long.MAX_VALUE)));
        assertThrows(ArithmeticException.class,()->plan(root,2,Map.of()));
    }
    @Test void returnedContainerFundsLaterBranch() {
        var cake=new TreePlanner.Node<String,String>("cake",List.of("cake"),1,"cake",1,"cake",List.of(leaf("milk",1)),List.of(new TreePlanner.Returned<>("bucket",1)),false,null);
        var goal=node("goal",1,1,"goal",List.of(cake,leaf("bucket",1)));
        assertTrue(plan(goal,1,Map.of("milk",1L)).missing().isEmpty());
    }

    @Test void overlappingAlternativesReassignEarlierChoices() {
        var ab = new TreePlanner.Node<String,String>("ab",List.of("a","b"),1,"a",1,null,List.of(),List.of(),false,null);
        var ac = new TreePlanner.Node<String,String>("ac",List.of("a","c"),1,"a",1,null,List.of(),List.of(),false,null);
        var p=plan(node("result",1,1,"result",List.of(ab,ac,ac)),1,Map.of("a",1L,"b",1L,"c",1L));
        assertTrue(p.missing().isEmpty()); assertEquals(1,p.steps().size());
    }
    @Test void reusableToolIsNotMultipliedByBatchCount() {
        var tool=new TreePlanner.Node<String,String>("tool",List.of("tool"),1,"tool",1,null,List.of(),List.of(),true,null);
        var recipe=new TreePlanner.Node<String,String>("part",List.of("part"),1,"part",1,"part",List.of(tool,leaf("ore",1)),List.of(new TreePlanner.Returned<>("tool",1,false)),false,null);
        assertTrue(plan(recipe,8,Map.of("tool",1L,"ore",8L)).missing().isEmpty());
    }
    @Test void deepTreesStopAtBound() {
        var n=leaf("base",1);
        for(int i=0;i<70;i++)n=node("node"+i,1,1,"recipe"+i,List.of(n));
        assertTrue(plan(n,1,Map.of()).obstacle().contains("large"));
    }
}
