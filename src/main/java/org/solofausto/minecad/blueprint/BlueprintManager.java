package org.solofausto.minecad.blueprint;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlueprintManager {
    private static final BlueprintManager INSTANCE = new BlueprintManager();

    private final Map<UUID, BlueprintSession> sessions = new ConcurrentHashMap<>();

    private BlueprintManager() {
    }

    public static BlueprintManager getInstance() {
        return INSTANCE;
    }

    public BlueprintSession getOrCreate(UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new BlueprintSession());
    }

    public Optional<BlueprintSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }
}
