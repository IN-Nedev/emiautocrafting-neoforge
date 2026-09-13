// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import com.example.emiautocrafting.emi.StackKey;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public record MenuSnapshot(long sequence, int menuId, List<ItemStack> slots, ItemStack cursor) {
    public MenuSnapshot {
        slots = slots.stream().map(ItemStack::copy).toList(); cursor = cursor.copy();
    }
    public Map<StackKey, Long> stock(List<Integer> indices) {
        Map<StackKey, Long> result = new LinkedHashMap<>();
        for (int index : indices) {
            ItemStack stack = slots.get(index);
            if (!stack.isEmpty()) result.merge(new StackKey(stack), (long) stack.getCount(), Math::addExact);
        }
        return result;
    }
}
