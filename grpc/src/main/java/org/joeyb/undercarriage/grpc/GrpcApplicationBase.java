package org.joeyb.undercarriage.grpc;

import static org.joeyb.undercarriage.core.utils.Exceptions.wrapChecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;

import org.joeyb.undercarriage.core.ApplicationBase;
import org.joeyb.undercarriage.core.config.ConfigContext;
import org.joeyb.undercarriage.core.utils.GenericClass;
import org.joeyb.undercarriage.core.utils.Iterables;
import org.joeyb.undercarriage.core.utils.Suppliers;
import org.joeyb.undercarriage.grpc.config.GrpcConfigSection;
import org.joeyb.undercarriage.grpc.plugins.GrpcPlugin;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@code GrpcApplicationBase} provides a base default implementation for the {@link GrpcApplication} interface.
 *
 * @param <ConfigT> the app's config type
 */
public abstract class GrpcApplicationBase<ConfigT extends GrpcConfigSection>
        extends ApplicationBase<ConfigT>
        implements GrpcApplication<ConfigT> {

    private final Supplier<Iterable<ServerServiceDefinition>> serverServiceDefinitions =
            Suppliers.memoize(this::buildServerServiceDefinitions);

    private final Supplier<Iterable<ServerServiceDefinition>> serverServiceDefinitionsWithInterceptors =
            Suppliers.memoize(this::buildServerServiceDefinitionsWithInterceptors);

    private final Supplier<Iterable<ServerInterceptor>> serverInterceptors =
            Suppliers.memoize(this::buildServerInterceptors);

    private Server server;

    protected GrpcApplicationBase(ConfigContext<ConfigT> configContext) {
        super(configContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int port() {
        if (!isStarted()) {
            throw new IllegalStateException("The application must be started before we know its port.");
        }

        return server.getPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Server server() {
        if (!isStarted()) {
            throw new IllegalStateException("The application must be started before its server is available.");
        }

        return server;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Iterable<ServerServiceDefinition> serverServiceDefinitions() {
        return serverServiceDefinitions.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Iterable<ServerServiceDefinition> serverServiceDefinitionsWithInterceptors() {
        return serverServiceDefinitionsWithInterceptors.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Iterable<ServerInterceptor> serverInterceptors() {
        return serverInterceptors.get();
    }

    /**
     * Returns the {@link ServerServiceDefinition}s provided by the application itself. Similar to
     * {@link ApplicationBase#enabledPlugins()}, implementors should also call
     * {@code super.enabledServerServiceDefinitions()} and merge its result with theirs in order to support default
     * service definitions.
     */
    protected Iterable<ServerServiceDefinition> enabledServerServiceDefinitions() {
        return ImmutableList.of();
    }

    /**
     * Returns the {@link ServerInterceptor}s provided by the application itself. Similar to
     * {@link ApplicationBase#enabledPlugins()}, implementors should also call {@code super.enabledServerInterceptors()}
     * and merge its result with theirs in order to support default service interceptors.
     */
    protected Iterable<ServerInterceptor> enabledServerInterceptors() {
        return ImmutableList.of();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        super.onStart();

        ServerBuilder<?> serverBuilder = createServerBuilder(configContext().config().grpc().port());

        for (ServerServiceDefinition serverServiceDefinition : serverServiceDefinitionsWithInterceptors()) {
            serverBuilder.addService(serverServiceDefinition);
        }

        server = serverBuilder.build();

        wrapChecked(() -> server.start());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        super.onStop();

        server.shutdownNow();
    }

    private Iterable<ServerServiceDefinition> buildServerServiceDefinitions() {
        ImmutableList.Builder<ServerServiceDefinition> builder = ImmutableList.builder();

        builder.addAll(enabledServerServiceDefinitions());

        for (GrpcPlugin<? super ConfigT> plugin : grpcPlugins()) {
            builder.addAll(plugin.serverServiceDefinitions());
        }

        return builder.build();
    }

    private Iterable<ServerServiceDefinition> buildServerServiceDefinitionsWithInterceptors() {
        List<ServerInterceptor> serverInterceptors = ImmutableList.copyOf(serverInterceptors());

        return ImmutableList.copyOf(Iterables.stream(serverServiceDefinitions())
                .map(s -> applyServiceInterceptor(s, serverInterceptors))
                .iterator());
    }

    private Iterable<ServerInterceptor> buildServerInterceptors() {
        ImmutableList.Builder<ServerInterceptor> builder = ImmutableList.builder();

        builder.addAll(enabledServerInterceptors());

        for (GrpcPlugin<? super ConfigT> plugin : grpcPlugins()) {
            builder.addAll(plugin.serverInterceptors());
        }

        return builder.build();
    }

    private Iterable<GrpcPlugin<? super ConfigT>> grpcPlugins() {
        return plugins(new GenericClass<GrpcPlugin<? super ConfigT>>() { }.getGenericClass());
    }

    @VisibleForTesting
    ServerServiceDefinition applyServiceInterceptor(
            ServerServiceDefinition serverServiceDefinition,
            List<ServerInterceptor> serverInterceptors) {
        return ServerInterceptors.intercept(serverServiceDefinition, serverInterceptors);
    }

    @VisibleForTesting
    ServerBuilder<?> createServerBuilder(int port) {
        return ServerBuilder.forPort(port);
    }
}
