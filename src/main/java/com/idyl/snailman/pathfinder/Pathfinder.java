package com.idyl.snailman.pathfinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.Thread;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

@Slf4j
public class Pathfinder {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3523, 448, 448, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 320, 442, 0);

    public final CollisionMap map;
    public final Map<WorldPoint, List<WorldPoint>> transports;

    public Pathfinder(CollisionMap map, Map<WorldPoint, List<WorldPoint>> transports) {
        this.map = map;
        this.transports = transports;
    }

    public void writeTransportToFile(String transport) {
        try {
            Files.write(Paths.get("src/main/resources/transports.txt"), transport.concat("\n").getBytes(), StandardOpenOption.APPEND);
        }catch (IOException e) {
            log.info(e.toString());
        }
    }

    public static boolean isInWilderness(WorldPoint p) {
        return WILDERNESS_ABOVE_GROUND.distanceTo(p) == 0 || WILDERNESS_UNDERGROUND.distanceTo(p) == 0;
    }

    public class Path implements Runnable {
        private final Node start;
        private final WorldPoint target;
        private final boolean avoidWilderness;

        private final List<Node> boundary = new LinkedList<>();
        private final Set<WorldPoint> visited = new HashSet<>();

        public Node nearest;
        private List<WorldPoint> path = new ArrayList<>();

        public boolean loading;

        public boolean valid = false;

        public long distance;

        private final Thread thread;

        public Path(WorldPoint start, WorldPoint target, boolean avoidWilderness, List<WorldPoint> existingPath) {
            this.target = target;
            this.start = new Node(start, null);
            this.avoidWilderness = avoidWilderness;
            this.nearest = null;
            this.loading = true;
            this.distance = Integer.MAX_VALUE;
            this.valid = false;

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

            thread = new Thread(this);
            thread.start();
        }

        public void stopThread() {
            thread.interrupt();
        }

        private void addNeighbors(Node node) {
            if (map.w(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() - 1, node.position.getY(), node.position.getPlane()));
            }

            if (map.e(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() + 1, node.position.getY(), node.position.getPlane()));
            }

            if (map.s(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX(), node.position.getY() - 1, node.position.getPlane()));
            }

            if (map.n(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX(), node.position.getY() + 1, node.position.getPlane()));
            }

            if (map.sw(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() - 1, node.position.getY() - 1, node.position.getPlane()));
            }

            if (map.se(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() + 1, node.position.getY() - 1, node.position.getPlane()));
            }

            if (map.nw(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() - 1, node.position.getY() + 1, node.position.getPlane()));
            }

            if (map.ne(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() + 1, node.position.getY() + 1, node.position.getPlane()));
            }

            for (WorldPoint transport : transports.getOrDefault(node.position, new ArrayList<>())) {
                addNeighbor(node, transport);
            }
        }

        public List<WorldPoint> currentBest() {
            return nearest == null ? null : nearest.path();
        }

        public List<WorldPoint> getPath() {
            return this.path;
        }

        public WorldPoint getStart() {
            return start.position;
        }

        public WorldPoint getTarget() {
            return target;
        }

        private void addNeighbor(Node node, WorldPoint neighbor) {
            if (avoidWilderness && isInWilderness(neighbor) && !isInWilderness(node.position) && !isInWilderness(target)) {
                return;
            }

            if (!visited.add(neighbor)) {
                return;
            }

            boundary.add(new Node(neighbor, node));
        }

        @Override
        public void run() {
            if(boundary.isEmpty()) boundary.add(start);

            int bestDistance = Integer.MAX_VALUE;

            long startTime = Instant.now().toEpochMilli();

            while (!boundary.isEmpty() && !Thread.interrupted()) {
                long elapsed = Instant.now().toEpochMilli() - startTime;

                Node node = boundary.remove(0);

                if (node.position.equals(target)) {
                    this.path = node.path();
                    this.loading = false;
                    this.valid = true;
                    return;
                }

                int distance = node.position.distanceTo(target);
                if (nearest == null || distance < bestDistance) {
                    nearest = node;
                    bestDistance = distance;
                    this.distance = distance;
                }

                addNeighbors(node);
            }

            if (nearest != null) {
                this.path = nearest.path();
            }

            this.loading = false;
            long elapsed = Instant.now().toEpochMilli() - startTime;
            thread.interrupt();
        }
    }

    private static class Node {
        public final WorldPoint position;
        public final Node previous;

        public Node(WorldPoint position, Node previous) {
            this.position = position;
            this.previous = previous;
        }

        public List<WorldPoint> path() {
            List<WorldPoint> path = new LinkedList<>();
            Node node = this;

            while (node != null) {
                path.add(0, node.position);
                node = node.previous;
            }

            return new ArrayList<>(path);
        }
    }
}
