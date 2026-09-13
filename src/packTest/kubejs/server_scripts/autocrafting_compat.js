// SPDX-License-Identifier: GPL-3.0-only
// Independent integration fixtures. This is a selected crafting-stack sample,
// not the Impostor Syndrome modpack or a redistribution of its scripts.
ServerEvents.recipes(event => {
    event.remove({ id: 'minecraft:furnace' });
    event.shaped('minecraft:furnace', ['CSC', 'SBS', 'CSC'], {
        C: '#c:cobblestones', S: 'allthecompressed:cobblestone_1x', B: 'minecraft:coal_block'
    }).id('minecraft:furnace');
    event.remove({ id: 'minecraft:piston' });
    event.shaped('minecraft:piston', ['PPP', 'CSC', 'CRC'], {
        P: '#minecraft:planks', C: '#c:cobblestones/normal', S: 'create:shaft', R: 'minecraft:redstone'
    }).id('minecraft:piston');
    event.remove({ output: 'create:shaft' });
    event.shaped('2x create:shaft', ['A', 'A'], { A: 'create:andesite_alloy' }).id('create:shaft');
    event.remove({ output: 'create:andesite_alloy' });
    event.custom({ type: 'create:mixing', ingredients: [{item: 'minecraft:andesite'}, {tag: 'c:ingots/zinc'}],
        results: [{id: 'create:andesite_alloy', count: 4}], heatRequirement: 'heated'
    }).id('create:mixing/andesite_alloy');
    // Exercise an ingredient-action recipe, which needs a separate execution adapter.
    event.shapeless('minecraft:gold_nugget', ['minecraft:iron_ingot', 'minecraft:stick'])
        .keepIngredient('minecraft:stick').id('autocrafting_test:scripted_remainder');
    event.shapeless('minecraft:diamond', ['minecraft:iron_ingot', 'minecraft:coal'])
        .modifyResult('autocrafting_test:dynamic_output').id('autocrafting_test:scripted_output');
    event.shapeless('minecraft:emerald', ['minecraft:copper_ingot', 'minecraft:coal'])
        .modifyResult('').id('autocrafting_test:plain_kubejs_shapeless');
});
