// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.nav;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// walkable nav graph of one island (nodes with positions, weighted edges, optional names + tags)
public final class Graph {
    public static final Graph EMPTY = new Graph("", new Node[0]);

    public record Node(int index, String id, Vec3 pos, String name, List<String> tags, int[] neighbours, double[] costs) {}

    public final String island;
    public final Node[] nodes;
    private final List<Node> named = new ArrayList<>();

    private Graph(String island, Node[] nodes) {
        this.island = island;
        this.nodes = nodes;
        for (Node n : nodes) if (n.name() != null && !n.name().isBlank() && !"no_area".equals(n.name())) named.add(n);
    }

    public boolean isEmpty() {
        return nodes.length == 0;
    }

    public List<Node> named() {
        return named;
    }

    public static Graph parse(String island, JsonObject json) {
        Map<String, Integer> idx = new HashMap<>();
        List<String> ids = new ArrayList<>(json.keySet());
        for (int i = 0; i < ids.size(); i++) idx.put(ids.get(i), i);
        Node[] nodes = new Node[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            JsonObject o = json.getAsJsonObject(ids.get(i));
            String[] p = o.get("Position").getAsString().split(":");
            Vec3 pos = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            String name = o.has("Name") ? o.get("Name").getAsString() : null;
            List<String> tags = new ArrayList<>();
            if (o.has("Tags")) for (JsonElement t : o.getAsJsonArray("Tags")) tags.add(t.getAsString());
            JsonObject nb = o.has("Neighbours") ? o.getAsJsonObject("Neighbours") : new JsonObject();
            int[] n = new int[nb.size()];
            double[] c = new double[nb.size()];
            int k = 0;
            for (Map.Entry<String, JsonElement> e : nb.entrySet()) {
                Integer j = idx.get(e.getKey());
                if (j == null) continue;
                n[k] = j;
                c[k++] = e.getValue().getAsDouble();
            }
            nodes[i] = new Node(i, ids.get(i), pos, name, tags, Arrays.copyOf(n, k), Arrays.copyOf(c, k));
        }
        return new Graph(island, nodes);
    }

    public Node nearest(Vec3 p) {
        Node best = null;
        double bd = Double.MAX_VALUE;
        for (Node n : nodes) {
            double d = n.pos().distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    // a* shortest path between 2 nodes as positions, empty = unreachable
    public List<Vec3> path(Node from, Node to) {
        return path(from, to, null);
    }

    // same but around the nodes avoid says no to
    public List<Vec3> path(Node from, Node to, java.util.function.Predicate<Node> avoid) {
        if (from == null || to == null) return List.of();
        int n = nodes.length;
        double[] g = new double[n];
        int[] prev = new int[n];
        Arrays.fill(g, Double.MAX_VALUE);
        Arrays.fill(prev, -1);
        boolean[] closed = new boolean[n];
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        g[from.index()] = 0;
        open.add(new double[]{from.pos().distanceTo(to.pos()), from.index()});
        while (!open.isEmpty()) {
            int cur = (int) open.poll()[1];
            if (closed[cur]) continue;
            if (cur == to.index()) break;
            closed[cur] = true;
            Node c = nodes[cur];
            for (int i = 0; i < c.neighbours().length; i++) {
                int nb = c.neighbours()[i];
                if (avoid != null && nb != to.index() && avoid.test(nodes[nb])) continue;
                double ng = g[cur] + c.costs()[i];
                if (ng < g[nb]) {
                    g[nb] = ng;
                    prev[nb] = cur;
                    open.add(new double[]{ng + nodes[nb].pos().distanceTo(to.pos()), nb});
                }
            }
        }
        if (from != to && prev[to.index()] < 0) return List.of();
        List<Vec3> out = new ArrayList<>();
        for (int at = to.index(); at != -1; at = prev[at]) {
            out.add(nodes[at].pos());
            if (at == from.index()) break;
        }
        java.util.Collections.reverse(out);
        return out;
    }

    public Node byName(String name) {
        for (Node n : named) if (n.name().equalsIgnoreCase(name)) return n;
        for (Node n : named) if (n.name().toLowerCase().contains(name.toLowerCase())) return n;
        return null;
    }

    // closest warp node (tag "warp", named like "/warp mines") to a target
    public Node warpNear(Vec3 target) {
        Node best = null;
        double bd = Double.MAX_VALUE;
        for (Node n : named) {
            if (!n.tags().contains("warp") || !n.name().startsWith("/warp")) continue;
            double d = n.pos().distanceToSqr(target);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }
}
