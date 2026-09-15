// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

/** Tick-driven lifecycle, independent of Minecraft for delayed/rejected-operation tests. */
public final class JobController<S> {
    public enum State { IDLE, PLANNING, READY, WAITING, BLOCKED, COMPLETED, CANCELLED, FAILED }
    public enum Confirmation { PENDING, CONFIRMED, REJECTED }
    public record Decision<S>(S step, String blocked, boolean completed) {
        public static <S> Decision<S> ready(S step) { return new Decision<>(step, null, false); }
        public static <S> Decision<S> blocked(String text) { return new Decision<>(null, text, false); }
        public static <S> Decision<S> completedResult() { return new Decision<>(null, null, true); }
    }
    public interface Port<S> {
        boolean valid();
        Decision<S> plan();
        void dispatch(S step);
        Confirmation confirm(S step);
        void timeout();
        default void rejected() { }
        default String waitingMessage() { return "Waiting for server"; }
        String describe(S step);
        void error(Exception error);
        default boolean countsAsCraft(S step) { return true; }
    }
    private State state = State.IDLE;
    private String message = "Idle";
    private S step;
    private boolean single;
    private long sent, next, operations;
    public State state() { return state; }
    public String message() { return message; }
    public boolean active() { return state == State.PLANNING || state == State.READY || state == State.WAITING; }
    public boolean start(boolean singleStep, long tick) {
        if (active()) return false;
        single = singleStep; step = null; operations = 0; next = tick;
        set(State.PLANNING, "Planning"); return true;
    }
    public void cancel(String reason) { if (active()) { step = null; set(State.CANCELLED, reason); } }
    public void tick(long tick, int timeoutTicks, int paceTicks, Port<S> port) {
        if (!active()) return;
        try {
            if (!port.valid()) { cancel("Cancelled; menu or recipe tree changed. Completed items were retained."); return; }
            // Advance bookkeeping in the same tick, but dispatch at most one operation.
            // Every following dispatch still requires confirmation of the previous one.
            for (int transition = 0; transition < 3 && active(); transition++) switch (state) {
                case PLANNING -> {
                    if (tick < next) return;
                    if (operations >= 100000) { set(State.BLOCKED, "Job step limit reached; prepare a smaller target"); return; }
                    Decision<S> decision = port.plan();
                    if (decision.completed()) set(State.COMPLETED, "Completed");
                    else if (decision.blocked() != null) set(State.BLOCKED, decision.blocked());
                    else { step = decision.step(); set(State.READY, "Crafting: " + port.describe(step)); }
                }
                case READY -> {
                    sent = tick;
                    // Install WAITING before a callback can fail or synchronously report a result.
                    set(State.WAITING, "Waiting for server");
                    port.dispatch(step);
                    return;
                }
                case WAITING -> {
                    Confirmation result = port.confirm(step);
                    if (result == Confirmation.REJECTED) {
                        port.rejected();
                        set(State.BLOCKED, "Server inventory did not match the craft; reopen the menu before retrying");
                    } else if (result == Confirmation.CONFIRMED) {
                        if (port.countsAsCraft(step)) operations++;
                        if (single && port.countsAsCraft(step)) set(State.COMPLETED, "Single step completed; crafted items were retained");
                        else { next = tick + (port.countsAsCraft(step) ? paceTicks : 0); set(State.PLANNING, "Planning"); }
                    } else if (tick - sent >= timeoutTicks) {
                        port.timeout();
                        set(State.BLOCKED, "Server response timed out: " + port.waitingMessage() + "; reopen the menu before retrying");
                    } else { message = port.waitingMessage(); return; }
                }
                default -> { }
            }
        } catch (Exception error) {
            step = null;
            if (error instanceof ArithmeticException) set(State.BLOCKED, "Recipe quantities exceed the supported limit; choose a smaller target");
            else set(State.FAILED, "Crafting stopped: " + error.getMessage());
            port.error(error);
        }
    }
    private void set(State value, String text) { state = value; message = text; }
}
