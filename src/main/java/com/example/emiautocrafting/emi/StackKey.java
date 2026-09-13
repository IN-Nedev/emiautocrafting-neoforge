// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.emi;

import net.minecraft.world.item.ItemStack;

/** Exact item + data components; count is deliberately excluded. */
public final class StackKey {
    private final ItemStack stack;
    public StackKey(ItemStack stack) { this.stack = stack.copyWithCount(1); }
    public ItemStack stack() { return stack.copy(); }
    public String label() { return stack.getHoverName().getString(); }
    @Override public boolean equals(Object other) {
        return other instanceof StackKey k && ItemStack.isSameItemSameComponents(stack, k.stack);
    }
    @Override public int hashCode() { return 31 * stack.getItem().hashCode() + stack.getComponents().hashCode(); }
    @Override public String toString() { return stack.toString() + stack.getComponentsPatch(); }
}
