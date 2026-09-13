// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.*;

/** Immutable EMI-tree projection. A single ledger reserves shared stock and batch surplus. */
public final class TreePlanner<K, R> {
    public record Returned<K>(K key, long amount, boolean perBatch) {
        public Returned(K key, long amount) { this(key, amount, true); }
    }
    public record Node<K, R>(String label, List<K> alternatives, long amount, K output,
            long outputCount, R recipe, List<Node<K, R>> inputs, List<Returned<K>> returns,
            boolean catalyst, String obstacle) {
        public Node {
            alternatives = List.copyOf(alternatives); inputs = List.copyOf(inputs); returns = List.copyOf(returns);
            if (amount <= 0 || outputCount <= 0) throw new IllegalArgumentException("Invalid tree quantity");
        }
    }
    public record Step<K, R>(Node<K, R> node, long batches, Map<K, Long> available) {}
    public record Plan<K, R>(List<Step<K, R>> steps, Map<String, Long> missing, String obstacle) {
        public boolean complete() { return steps.isEmpty() && missing.isEmpty() && obstacle == null; }
    }
    private final LinkedHashMap<K, Long> stock = new LinkedHashMap<>();
    private final List<Step<K, R>> steps = new ArrayList<>();
    private final Map<String, Long> missing = new LinkedHashMap<>();
    private final Set<R> path = new HashSet<>();
    private String obstacle;
    private int visits;

    public Plan<K, R> plan(Node<K, R> root, long total, Map<K, Long> inventory) {
        stock.clear(); steps.clear(); missing.clear(); path.clear(); obstacle = null; visits = 0;
        if (total <= 0) throw new IllegalArgumentException("Target must be positive");
        inventory.forEach((k, v) -> { if (v < 0) throw new IllegalArgumentException("Negative stock"); stock.put(k, v); });
        demand(root, total, 0);
        return new Plan<>(List.copyOf(steps), Map.copyOf(missing), obstacle);
    }
    private void demand(Node<K, R> node, long requested, int depth) {
        if (++visits > 4096 || depth > 64) { obstacle = "Recipe tree is too large or cyclic"; return; }
        long left = requested;
        for (K key : node.alternatives()) {
            long take = Math.min(left, stock.getOrDefault(key, 0L));
            stock.put(key, stock.getOrDefault(key, 0L) - take);
            left -= take;
            if (left == 0) break;
        }
        if (left == 0) return;
        if (node.obstacle() != null) { obstacle = node.obstacle(); return; }
        if (node.recipe() == null) {
            missing.merge(node.label(), left, Math::addExact); return;
        }
        if (!path.add(node.recipe())) { obstacle = "Cyclic recipe: " + node.label(); return; }
        try {
            long batches = Quantities.batches(left, node.outputCount());
            long produced = Math.multiplyExact(batches, node.outputCount());
            Map<K, Long> available = Map.copyOf(stock);
            long[] needed = new long[node.inputs().size()];
            for (int i = 0; i < needed.length; i++) {
                Node<K, R> child = node.inputs().get(i);
                needed[i] = child.catalyst() ? child.amount() : Math.multiplyExact(batches, child.amount());
            }
            reserveTogether(node.inputs(), needed);
            for (int i = 0; i < needed.length; i++) {
                if (needed[i] > 0) demand(node.inputs().get(i), needed[i], depth + 1);
            }
            steps.add(new Step<>(node, batches, available));
            stock.merge(node.output(), produced - left, Math::addExact);
            for (Returned<K> returned : node.returns()) {
                stock.merge(returned.key(), Math.multiplyExact(returned.perBatch() ? batches : 1, returned.amount()), Math::addExact);
            }
        } finally { path.remove(node.recipe()); }
    }

    /** Allocate sibling alternatives together, so a broad tag cannot steal a narrow input's only match. */
    private void reserveTogether(List<Node<K, R>> inputs, long[] needed) {
        List<K> keys = stock.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey).filter(k -> inputs.stream().anyMatch(n -> n.alternatives().contains(k))).toList();
        int sink = keys.size() + inputs.size() + 1;
        List<List<Edge>> graph = new ArrayList<>();
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        List<Edge> sources = new ArrayList<>(), targets = new ArrayList<>();
        for (int k = 0; k < keys.size(); k++) {
            sources.add(edge(graph, 0, k + 1, stock.get(keys.get(k))));
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i).alternatives().contains(keys.get(k))) edge(graph, k + 1, keys.size() + i + 1, needed[i]);
            }
        }
        for (int i = 0; i < inputs.size(); i++) targets.add(edge(graph, keys.size() + i + 1, sink, needed[i]));
        while (true) {
            Edge[] path = new Edge[sink + 1];
            int[] parent = new int[sink + 1]; Arrays.fill(parent, -1); parent[0] = 0;
            Deque<Integer> queue = new ArrayDeque<>(); queue.add(0);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int from = queue.remove();
                for (Edge e : graph.get(from)) if (e.capacity > 0 && parent[e.to] < 0) {
                    parent[e.to] = from; path[e.to] = e; queue.add(e.to);
                }
            }
            if (parent[sink] < 0) break;
            long amount = Long.MAX_VALUE;
            for (int at = sink; at != 0; at = parent[at]) amount = Math.min(amount, path[at].capacity);
            for (int at = sink; at != 0; at = parent[at]) {
                Edge e = path[at]; e.capacity -= amount; e.reverse.capacity += amount;
            }
        }
        for (int k = 0; k < keys.size(); k++) stock.put(keys.get(k), sources.get(k).capacity);
        for (int i = 0; i < inputs.size(); i++) needed[i] = targets.get(i).capacity;
    }
    private static final class Edge {
        final int to; long capacity; Edge reverse;
        Edge(int to, long capacity) { this.to = to; this.capacity = capacity; }
    }
    private static Edge edge(List<List<Edge>> graph, int from, int to, long capacity) {
        Edge forward = new Edge(to, capacity), reverse = new Edge(from, 0);
        forward.reverse = reverse; reverse.reverse = forward;
        graph.get(from).add(forward); graph.get(to).add(reverse); return forward;
    }
}
