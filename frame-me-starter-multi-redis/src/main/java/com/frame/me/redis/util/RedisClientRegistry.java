package com.frame.me.redis.util;

import com.frame.me.base.exception.BusinessException;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RedisClientRegistry {

    private final String defaultClientName;
    private final Map<String, RedisClient> clients;

    public RedisClientRegistry(String defaultClientName, Map<String, RedisClient> clients) {
        this.defaultClientName = Objects.requireNonNull(defaultClientName, "defaultClientName");
        this.clients = Map.copyOf(Objects.requireNonNull(clients, "clients"));
        if (!this.clients.containsKey(defaultClientName)) {
            throw new IllegalArgumentException("Default Redis client '" + defaultClientName + "' is not registered");
        }
    }

    public RedisClient getDefaultClient() {
        return getClient(defaultClientName);
    }

    public RedisClient getClient(String name) {
        RedisClient client = clients.get(name);
        if (client == null) {
            throw new BusinessException("Redis client '" + name
                    + "' Not registered. Please check the me.redis.clients configuration");
        }
        return client;
    }

    public Set<String> clientNames() {
        return clients.keySet();
    }
}
