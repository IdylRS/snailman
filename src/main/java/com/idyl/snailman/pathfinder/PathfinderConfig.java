package com.idyl.snailman.pathfinder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import com.idyl.snailman.Transport;

public class PathfinderConfig {
    public static final Duration CALCULATION_CUTOFF = Duration.ofSeconds(5);

    @Getter
    private final CollisionMap map;
    @Getter
    private final Map<WorldPoint, List<Transport>> transports;

    public PathfinderConfig(CollisionMap map, Map<WorldPoint, List<Transport>> transports, Client client) {
        this.map = map;
        this.transports = transports;
    }
}