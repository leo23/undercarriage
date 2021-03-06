package org.joeyb.undercarriage.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import org.joeyb.undercarriage.core.config.ConfigContext;
import org.joeyb.undercarriage.core.config.ConfigSection;
import org.joeyb.undercarriage.core.plugins.MockPlugin;
import org.joeyb.undercarriage.core.plugins.Plugin;
import org.joeyb.undercarriage.core.plugins.PluginSorter;
import org.joeyb.undercarriage.core.utils.GenericClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.security.InvalidParameterException;
import java.util.concurrent.CountDownLatch;

public class ApplicationBaseTests {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    public ConfigContext<ConfigSection> configContext;

    @Test
    public void givenConfigContextIsReturnedByGetter() {
        MockApplication application = new MockApplication(configContext);

        assertThat(application.configContext())
                .isEqualTo(configContext);
    }

    @Test
    public void applicationConstructionThrowsForNullConfigContext() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new MockApplication(null));
    }

    @Test
    public void configureThrowsIfExecutedTwice() {
        MockApplication application = new MockApplication(configContext);

        application.configure();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(application::configure);
    }

    @Test
    public void configureCallsOnConfigure() throws InterruptedException {
        CountDownLatch onConfigureLatch = new CountDownLatch(1);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected void onConfigure() {
                onConfigureLatch.countDown();
            }
        };

        application.configure();

        onConfigureLatch.await();
    }

    @Test
    public void configureConfiguresPlugins() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        application.configure();

        verify(plugin).configure();
    }

    @Test
    public void configureConfiguresPluginsInSortedOrder() {
        MockPlugin plugin1 = mock(MockPlugin.class, CALLS_REAL_METHODS);
        MockPlugin plugin2 = mock(MockPlugin.class, CALLS_REAL_METHODS);

        PluginSorter pluginSorter = mock(PluginSorter.class);

        when(pluginSorter.sort(any())).thenReturn(ImmutableList.of(plugin1, plugin2));

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin1, plugin2);
            }

            @Override
            protected PluginSorter pluginSorter() {
                return pluginSorter;
            }
        };

        InOrder inOrder = inOrder(plugin1, plugin2);

        application.configure();

        inOrder.verify(plugin1).configure();
        inOrder.verify(plugin2).configure();
    }

    @Test
    public void isConfiguredIsSetByConfigure() {
        MockApplication application = new MockApplication(configContext);

        assertThat(application.isConfigured()).isEqualTo(false);

        application.configure();

        assertThat(application.isConfigured()).isEqualTo(true);
    }

    @Test
    public void isStartedIsSetByStart() {
        MockApplication application = new MockApplication(configContext);

        assertThat(application.isStarted()).isEqualTo(false);

        application.start();

        assertThat(application.isStarted()).isEqualTo(true);
    }

    @Test
    public void isStoppedIsSetByStop() {
        MockApplication application = new MockApplication(configContext);

        assertThat(application.isStopped()).isEqualTo(false);

        application.start();
        application.stop();

        assertThat(application.isStopped()).isEqualTo(true);
    }

    @Test
    public void pluginReturnsTheExpectedInstanceIfItIsEnabled() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        MockPlugin foundPlugin = application.plugin(MockPlugin.class);

        assertThat(foundPlugin)
                .isEqualTo(plugin);
    }

    @Test
    public void pluginThrowsIfTheGivenPluginIsDisabled() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Class<?>> disabledPlugins() {
                return ImmutableList.of(MockPlugin.class);
            }

            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        assertThatExceptionOfType(InvalidParameterException.class)
                .isThrownBy(() -> application.plugin(MockPlugin.class));
    }

    @Test
    public void pluginThrowsIfTheGivenPluginIsNotEnabled() {
        MockApplication application = new MockApplication(configContext);

        assertThatExceptionOfType(InvalidParameterException.class)
                .isThrownBy(() -> application.plugin(MockPlugin.class));
    }

    @Test
    public void pluginsSortsUsingThePluginSorter() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);
        PluginSorter pluginSorter = mock(PluginSorter.class);

        Iterable<Plugin<? super ConfigSection>> plugins = ImmutableList.of(plugin);

        when(pluginSorter.sort(any())).thenReturn(plugins);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }

            @Override
            protected PluginSorter pluginSorter() {
                return pluginSorter;
            }
        };

        application.plugins();

        verify(pluginSorter).sort(any());
    }

    @Test
    public void pluginsIncludesTheExpectedPluginIfItIsEnabled() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        Iterable<Plugin<? super ConfigSection>> plugins = application.plugins();

        assertThat(plugins)
                .contains(plugin);
    }

    @Test
    public void pluginsExcludesTheExpectedPluginIfItIsDisabled() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Class<?>> disabledPlugins() {
                return ImmutableList.of(MockPlugin.class);
            }

            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        Iterable<Plugin<? super ConfigSection>> plugins = application.plugins();

        assertThat(plugins)
                .isNotNull()
                .doesNotContain(plugin);
    }

    @Test
    public void pluginsReturnsTheSameInstancesOnSubsequentCalls() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        Iterable<Plugin<? super ConfigSection>> plugins1 = application.plugins();
        Iterable<Plugin<? super ConfigSection>> plugins2 = application.plugins();

        assertThat(plugins1)
                .contains(plugin);
        assertThat(plugins2)
                .contains(plugin);
    }

    @Test
    public void pluginsOfTypeIncludesTheExpectedPluginsIfTheyAreEnabled() {
        MockPlugin plugin1 = mock(MockPlugin.class, CALLS_REAL_METHODS);
        MockPlugin2 plugin2 = mock(MockPlugin2.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin1, plugin2);
            }
        };

        Iterable<MockPlugin> plugins = application.plugins(MockPlugin.class);

        assertThat(plugins)
                .containsExactlyInAnyOrder(plugin1);
    }

    @Test
    public void pluginsOfTypeIncludesTheExpectedPluginsOfGivenSupertypeIfTheyAreEnabled() {
        MockPlugin plugin1 = mock(MockPlugin.class, CALLS_REAL_METHODS);
        MockPlugin2 plugin2 = mock(MockPlugin2.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin1, plugin2);
            }
        };

        Iterable<Plugin<ConfigSection>> plugins = application.plugins(
                new GenericClass<Plugin<ConfigSection>>() { }.getGenericClass());

        assertThat(plugins)
                .containsExactlyInAnyOrder(plugin1, plugin2);
    }

    @Test
    public void pluginsOfTypeExcludesTheExpectedPluginsIfTheyAreDisabled() {
        MockPlugin plugin1 = mock(MockPlugin.class, CALLS_REAL_METHODS);
        MockPlugin2 plugin2 = mock(MockPlugin2.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {

            @Override
            protected Iterable<Class<?>> disabledPlugins() {
                return ImmutableList.of(MockPlugin.class);
            }

            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin1, plugin2);
            }
        };

        Iterable<MockPlugin> plugins = application.plugins(MockPlugin.class);

        assertThat(plugins)
                .isNotNull()
                .isEmpty();
    }

    @Test
    public void startDoesNotCallConfigureIfConfigured() throws InterruptedException {
        CountDownLatch onConfigureLatch = new CountDownLatch(1);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected void onConfigure() {
                if (onConfigureLatch.getCount() == 0) {
                    fail("Already configured");
                }

                onConfigureLatch.countDown();
            }
        };

        application.configure();
        application.start();

        onConfigureLatch.await();
    }

    @Test
    public void startCallsConfigureIfNotConfigured() throws InterruptedException {
        CountDownLatch onConfigureLatch = new CountDownLatch(1);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected void onConfigure() {
                onConfigureLatch.countDown();
            }
        };

        application.start();

        onConfigureLatch.await();
    }

    @Test
    public void startCallsOnStart() throws InterruptedException {
        CountDownLatch onStartLatch = new CountDownLatch(1);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected void onStart() {
                onStartLatch.countDown();
            }
        };

        application.start();

        onStartLatch.await();
    }

    @Test
    public void startStartsPlugins() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        application.start();

        verify(plugin).start();
    }

    @Test
    public void startStartsPluginsInSortedOrder() {
        MockPlugin plugin1 = mock(MockPlugin.class, CALLS_REAL_METHODS);
        MockPlugin plugin2 = mock(MockPlugin.class, CALLS_REAL_METHODS);

        PluginSorter pluginSorter = mock(PluginSorter.class);

        when(pluginSorter.sort(any())).thenReturn(ImmutableList.of(plugin1, plugin2));

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin1, plugin2);
            }

            @Override
            protected PluginSorter pluginSorter() {
                return pluginSorter;
            }
        };

        InOrder inOrder = inOrder(plugin1, plugin2);

        application.start();

        inOrder.verify(plugin1).start();
        inOrder.verify(plugin2).start();
    }

    @Test
    public void startThrowsIfExecutedTwice() {
        MockApplication application = new MockApplication(configContext);

        application.start();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(application::start);
    }

    @Test
    public void stopCallsOnStop() throws InterruptedException {
        CountDownLatch onStopLatch = new CountDownLatch(1);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected void onStop() {
                onStopLatch.countDown();
            }
        };

        application.start();
        application.stop();

        onStopLatch.await();
    }

    @Test
    public void stopStopsPlugins() {
        MockPlugin plugin = mock(MockPlugin.class, CALLS_REAL_METHODS);

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin);
            }
        };

        application.start();
        application.stop();

        verify(plugin).stop();
    }

    @Test
    public void stopStopsPluginsInReverseSortedOrder() {
        MockPlugin plugin1 = mock(MockPlugin.class, CALLS_REAL_METHODS);
        MockPlugin plugin2 = mock(MockPlugin.class, CALLS_REAL_METHODS);

        PluginSorter pluginSorter = mock(PluginSorter.class);

        when(pluginSorter.sort(any())).thenReturn(ImmutableList.of(plugin1, plugin2));

        MockApplication application = new MockApplication(configContext) {
            @Override
            protected Iterable<Plugin<? super ConfigSection>> enabledPlugins() {
                return ImmutableList.of(plugin1, plugin2);
            }

            @Override
            protected PluginSorter pluginSorter() {
                return pluginSorter;
            }
        };

        InOrder inOrder = inOrder(plugin1, plugin2);

        application.start();
        application.stop();

        inOrder.verify(plugin2).stop();
        inOrder.verify(plugin1).stop();
    }

    @Test
    public void stopThrowsIfExecutedTwice() {
        MockApplication application = new MockApplication(configContext);

        application.start();

        application.stop();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(application::stop);
    }

    @Test
    public void stopThrowsIfApplicationNeverStarted() {
        MockApplication application = new MockApplication(configContext);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(application::stop);
    }

    private static class MockApplication extends ApplicationBase<ConfigSection> {

        MockApplication(ConfigContext<ConfigSection> configContext) {
            super(configContext);
        }
    }

    private interface MockPlugin2 extends Plugin<ConfigSection> {

    }
}
