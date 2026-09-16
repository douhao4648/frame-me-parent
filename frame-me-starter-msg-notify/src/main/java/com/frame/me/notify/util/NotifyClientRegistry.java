package com.frame.me.notify.util;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NotifyClientRegistry {

    private static final String EMAIL = "email";
    private static final String WEBHOOK = "webhook";
    private static final String SMS = "sms";
    private static final String NO_GLOBAL_DEFAULT_CODE = "NO_GLOBAL_DEFAULT";
    private static final String NO_GLOBAL_DEFAULT_MESSAGE =
            "Global default notify client is not configured or not available";

    private final Map<String, INotifyClient> clients;
    private final Map<String, String> channelDefaultNames;
    private final String globalDefaultName;

    public NotifyClientRegistry(Map<String, INotifyClient> clients,
                                Map<String, String> channelDefaultNames,
                                String globalDefaultName) {
        this.clients = Map.copyOf(clients);
        this.channelDefaultNames = Map.copyOf(channelDefaultNames);
        this.globalDefaultName = globalDefaultName;
    }

    public String getDefaultClientName(String channelType) {
        String name = channelDefaultNames.get(channelType);
        if (name == null) {
            throw new IllegalStateException(
                    "No default client configured for channel '" + channelType
                            + "'. Please check me.notify." + channelType + " configuration");
        }
        return name;
    }

    public INotifyClient getClient(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Notify client name is blank. Please check the me.notify configuration");
        }
        INotifyClient client = clients.get(name);
        if (client == null) {
            throw new IllegalStateException(
                    "Notify client '" + name + "' not registered. Please check the me.notify configuration");
        }
        return client;
    }

    public Optional<INotifyClient> getGlobalDefaultClient() {
        return Optional.ofNullable(globalDefaultName).map(clients::get);
    }

    public boolean hasClient(String name) {
        return clients.containsKey(name);
    }

    public boolean hasGlobalDefault() {
        return globalDefaultName != null && clients.containsKey(globalDefaultName);
    }

    public Set<String> clientNames() {
        return clients.keySet();
    }

    public NotifyResult send(NotifyMessage message) {
        return getGlobalDefaultClient()
                .map(client -> client.send(message))
                .orElseGet(() -> NotifyResult.fail(NO_GLOBAL_DEFAULT_CODE, NO_GLOBAL_DEFAULT_MESSAGE));
    }

    public NotifyResult send(String title, String content, List<String> receivers) {
        return send(NotifyMessage.of(title, content, receivers));
    }

    public NotifyResult send(String title, String content, String receiver) {
        return send(title, content, List.of(receiver));
    }

    public INotifyClient email() {
        return getDefaultClient(EMAIL);
    }

    public INotifyClient email(String name) {
        return getNamedClient(EMAIL, name);
    }

    public INotifyClient webhook() {
        return getDefaultClient(WEBHOOK);
    }

    public INotifyClient webhook(String name) {
        return getNamedClient(WEBHOOK, name);
    }

    public INotifyClient sms() {
        return getDefaultClient(SMS);
    }

    public INotifyClient sms(String name) {
        return getNamedClient(SMS, name);
    }

    private INotifyClient getDefaultClient(String channelType) {
        String defaultName = getDefaultClientName(channelType);
        return requireChannelType(getClient(defaultName), defaultName, channelType);
    }

    private INotifyClient getNamedClient(String channelType, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Notify client name is blank. Please check the me.notify configuration");
        }
        String fullName = name.contains(":") ? name : channelType + ":" + name;
        return requireChannelType(getClient(fullName), name, channelType);
    }

    private INotifyClient requireChannelType(INotifyClient client, String name, String channelType) {
        if (!channelType.equals(client.getChannelType())) {
            throw new IllegalStateException(
                    "Client '" + name + "' is not a " + channelType + " client, type=" + client.getChannelType());
        }
        return client;
    }
}
