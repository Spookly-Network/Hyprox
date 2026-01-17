package net.spookly.hyprox.routing;

import net.spookly.hyprox.config.HyproxConfig;
import net.spookly.hyprox.registry.BackendRegistry;
import net.spookly.hyprox.registry.RegisteredBackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds routing decisions from static config and dynamic registry entries.
 */
public final class RoutingService {
    private final HyproxConfig config;
    private final BackendRegistry registry;
    private final BackendCapacityTracker capacityTracker;
    private final BackendHealthTracker healthTracker;
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public RoutingService(HyproxConfig config,
                          BackendRegistry registry,
                          BackendCapacityTracker capacityTracker,
                          BackendHealthTracker healthTracker) {
        this.config = Objects.requireNonNull(config, "config");
        this.registry = registry;
        this.capacityTracker = capacityTracker;
        this.healthTracker = healthTracker;
    }

    /**
     * Route a request to a backend using pool rules and selection policy.
     */
    public RoutingResult route(RoutingRequest request) {
        String pool = selectPool(request);
        if (isBlank(pool)) {
            return new RoutingResult(null, null, null, "no_pool");
        }
        List<BackendTarget> candidates = listBackends(pool, false);
        if (candidates.isEmpty()) {
            return new RoutingResult(pool, null, null, "no_backends");
        }
        List<BackendTarget> healthyCandidates = filterHealthy(candidates);
        boolean hasHealthy = healthTracker != null && !healthyCandidates.isEmpty();
        List<BackendTarget> selection = hasHealthy ? healthyCandidates : candidates;
        BackendReservation reservation = selectBackend(pool, selection, hasHealthy);
        if (reservation == null && hasHealthy && healthyCandidates.size() < candidates.size()) {
            reservation = selectBackend(pool, candidates, false);
        }
        if (reservation == null) {
            return new RoutingResult(pool, null, null, "pool_full");
        }
        return new RoutingResult(pool, reservation.backend(), reservation, "selected");
    }

    /**
     * List backends for a pool, optionally including draining dynamic entries.
     */
    public List<BackendTarget> listBackends(String pool, boolean includeDraining) {
        if (config.routing == null || config.routing.pools == null || isBlank(pool)) {
            return Collections.emptyList();
        }
        HyproxConfig.PoolConfig poolConfig = config.routing.pools.get(pool);
        if (poolConfig == null || poolConfig.backends == null) {
            return Collections.emptyList();
        }
        List<BackendTarget> results = new ArrayList<>();
        for (HyproxConfig.BackendConfig backend : poolConfig.backends) {
            if (backend == null) {
                continue;
            }
            results.add(fromStatic(pool, backend));
        }

        if (registry != null) {
            List<RegisteredBackend> dynamic = new ArrayList<>(registry.list(pool));
            dynamic.sort(Comparator.comparing(RegisteredBackend::id, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            for (RegisteredBackend backend : dynamic) {
                if (!includeDraining && backend.draining()) {
                    continue;
                }
                results.add(fromDynamic(backend));
            }
        }
        return results;
    }

    private String selectPool(RoutingRequest request) {
        if (config.routing == null) {
            return null;
        }
        if (config.routing.rules != null) {
            for (HyproxConfig.RuleConfig rule : config.routing.rules) {
                if (rule == null || rule.match == null) {
                    continue;
                }
                if (matches(rule.match, request)) {
                    return rule.pool;
                }
            }
        }
        return config.routing.defaultPool;
    }

    private boolean matches(HyproxConfig.MatchConfig match, RoutingRequest request) {
        if (match == null) {
            return false;
        }
        if (!isBlank(match.clientType)) {
            if (request == null || isBlank(request.clientType())
                    || !match.clientType.equalsIgnoreCase(request.clientType())) {
                return false;
            }
        }
        if (!isBlank(match.referralSource)) {
            if (request == null || isBlank(request.referralSource())
                    || !match.referralSource.equalsIgnoreCase(request.referralSource())) {
                return false;
            }
        }
        return true;
    }

    private BackendReservation selectBackend(String pool, List<BackendTarget> candidates, boolean applyHealth) {
        List<BackendTarget> remaining = new ArrayList<>(candidates);
        PoolPolicy policy = PoolPolicy.WEIGHTED;
        if (config.routing != null && config.routing.pools != null) {
            HyproxConfig.PoolConfig poolConfig = config.routing.pools.get(pool);
            if (poolConfig != null) {
                policy = PoolPolicy.fromConfig(poolConfig.policy);
            }
        }
        while (!remaining.isEmpty()) {
            BackendTarget candidate = policy == PoolPolicy.ROUND_ROBIN
                    ? selectRoundRobin(pool, remaining)
                    : selectWeighted(remaining, applyHealth);
            if (candidate == null) {
                return null;
            }
            BackendReservation reservation = tryReserve(candidate);
            if (reservation != null) {
                return reservation;
            }
            remaining.remove(candidate);
        }
        return null;
    }

    private BackendTarget selectRoundRobin(String pool, List<BackendTarget> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(pool, key -> new AtomicInteger());
        int index = Math.floorMod(counter.getAndIncrement(), candidates.size());
        return candidates.get(index);
    }

    private BackendTarget selectWeighted(List<BackendTarget> candidates, boolean applyHealth) {
        if (candidates.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (BackendTarget candidate : candidates) {
            totalWeight += weightFor(candidate, applyHealth);
        }
        if (totalWeight <= 0) {
            return null;
        }
        int target = ThreadLocalRandom.current().nextInt(totalWeight);
        int running = 0;
        for (BackendTarget candidate : candidates) {
            int weight = weightFor(candidate, applyHealth);
            if (weight <= 0) {
                continue;
            }
            running += weight;
            if (target < running) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private BackendReservation tryReserve(BackendTarget candidate) {
        if (capacityTracker == null) {
            return BackendReservation.unlimited(candidate);
        }
        return capacityTracker.tryReserve(candidate);
    }

    private List<BackendTarget> filterHealthy(List<BackendTarget> candidates) {
        if (healthTracker == null || candidates.isEmpty()) {
            return candidates;
        }
        List<BackendTarget> healthy = new ArrayList<>();
        for (BackendTarget candidate : candidates) {
            if (healthTracker.isHealthy(candidate)) {
                healthy.add(candidate);
            }
        }
        return healthy;
    }

    private int weightFor(BackendTarget candidate, boolean applyHealth) {
        int baseWeight = Math.max(1, candidate.weight());
        if (!applyHealth || healthTracker == null) {
            return baseWeight;
        }
        int score = healthTracker.score(candidate);
        int adjusted = (baseWeight * score) / 100;
        return Math.max(1, adjusted);
    }

    private BackendTarget fromStatic(String pool, HyproxConfig.BackendConfig backend) {
        return new BackendTarget(
                backend.id,
                pool,
                backend.host,
                backend.port != null ? backend.port : 0,
                effectiveWeight(backend.weight),
                backend.maxPlayers,
                copyTags(backend.tags),
                BackendSource.STATIC,
                false
        );
    }

    private BackendTarget fromDynamic(RegisteredBackend backend) {
        return new BackendTarget(
                backend.id(),
                backend.pool(),
                backend.host(),
                backend.port(),
                effectiveWeight(backend.weight()),
                backend.maxPlayers(),
                copyTags(backend.tags()),
                BackendSource.DYNAMIC,
                backend.draining()
        );
    }

    private int effectiveWeight(Integer weight) {
        if (weight == null || weight <= 0) {
            return 1;
        }
        return weight;
    }

    private List<String> copyTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return List.copyOf(tags);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
