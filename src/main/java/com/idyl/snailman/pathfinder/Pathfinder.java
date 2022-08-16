package com.idyl.snailman.pathfinder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import com.idyl.snailman.Transport;

public class Pathfinder implements Runnable {
    @Getter
    private final WorldPoint start;
    @Getter
    private final WorldPoint target;
    private final PathfinderConfig config;

    private final List<Node> boundary = new LinkedList<>();
    private final Set<WorldPoint> visited = new HashSet<>();

    private int error;

    @Getter
    private List<WorldPoint> path = new ArrayList<>();
    @Getter
    private boolean done = false;

    public Pathfinder(PathfinderConfig config, WorldPoint start, WorldPoint target, List<WorldPoint> existingPath, int error) {
        this.config = config;
        this.start = start;
        this.target = target;

        this.error = error;

        if(existingPath  != null) {
            Node prev = null;
            boolean foundStart = false;
            for(int i = 0; i < existingPath.size(); i++) {
                WorldPoint point = existingPath.get(i);

                if(!point.equals(start) && !foundStart) continue;
                foundStart = true;

                Node n = new Node(existingPath.get(i), prev);
                boundary.add(n);
                prev = n;
            }
        }

        new Thread(this).start();
    }

    private void addNeighbor(Node node, WorldPoint neighbor) {
        if (!visited.add(neighbor)) {
            return;
        }
        boundary.add(new Node(neighbor, node));
    }

    private void addNeighbors(Node node) {
        for (WorldPoint neighbor : config.getMap().getNeighbors(node.position)) {
            addNeighbor(node, neighbor);
        }

        for (Transport transport : config.getTransports().getOrDefault(node.position, new ArrayList<>())) {
            addNeighbor(node, transport.getDestination());
        }
    }

    @Override
    public void run() {
        boundary.add(new Node(start, null));

        Node nearest = boundary.get(0);
        int bestDistance = Integer.MAX_VALUE;
        Instant cutoffTime = Instant.now().plus(PathfinderConfig.CALCULATION_CUTOFF);
        long startTime = Instant.now().toEpochMilli();

        while (!boundary.isEmpty()) {
            Node node = boundary.remove(0);

            if (node.position.equals(target) || node.position.distanceTo(target) <= error) {
                path = node.getPath();
                long elapsed = Instant.now().toEpochMilli() - startTime;
                System.out.println("Found best path in "+elapsed+" seconds");
                break;
            }

            int distance = node.position.distanceTo(target);
            if (distance < bestDistance) {
                path = node.getPath();
                nearest = node;
                bestDistance = distance;
                cutoffTime = Instant.now().plus(PathfinderConfig.CALCULATION_CUTOFF);
            }

            if (Instant.now().isAfter(cutoffTime)) {
                path = nearest.getPath();
                long elapsed = Instant.now().toEpochMilli() - startTime;
                System.out.println("Cutoff pathfinding at "+elapsed+" seconds");
                break;
            }

            addNeighbors(node);
        }

        done = true;
        boundary.clear();
        visited.clear();
    }
}