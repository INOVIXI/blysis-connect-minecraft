package com.uxplima.blysis.velocity.bootstrap;

import java.nio.file.Path;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The plugin shell on a proxy. Velocity builds it through Guice and hands it the proxy, a logger and the
 * folder it may write in.
 *
 * <p>It owns the lifecycle and nothing else. There is no {@code @Plugin} annotation: that annotation only
 * fed the processor that writes {@code velocity-plugin.json}, and the file is written by hand here, so the
 * proxy reads one place.
 */
public final class ConnectVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final String version;
    private @Nullable ProxyModule module;

    @Inject
    public ConnectVelocityPlugin(
            ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, PluginContainer container) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.version = container.getDescription().getVersion().orElse("unknown");
    }

    /** Wiring waits for this event, because the command manager is only ready once the proxy has started. */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        ProxyModule built = new ProxyModule(this, proxy, logger, dataDirectory, version);
        built.wire();
        this.module = built;
    }

    /** The connection's journal and HTTP client are given back; the proxy takes back nothing on its own. */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        ProxyModule built = module;
        if (built != null) {
            built.stop();
            module = null;
        }
    }
}
