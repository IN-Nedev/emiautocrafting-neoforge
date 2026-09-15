// SPDX-License-Identifier: GPL-3.0-only
// Recipe-wrapper fixtures for the isolated KubeJS integration profile.
ServerEvents.recipes(event => {
    event.shapeless('minecraft:gold_nugget', ['minecraft:iron_ingot', 'minecraft:stick'])
        .keepIngredient('minecraft:stick').id('autocrafting_test:scripted_remainder');
    event.shapeless('minecraft:diamond', ['minecraft:iron_ingot', 'minecraft:coal'])
        .modifyResult('autocrafting_test:dynamic_output').id('autocrafting_test:scripted_output');
    event.shapeless('minecraft:emerald', ['minecraft:copper_ingot', 'minecraft:coal'])
        .modifyResult('').id('autocrafting_test:plain_kubejs_shapeless');
});
