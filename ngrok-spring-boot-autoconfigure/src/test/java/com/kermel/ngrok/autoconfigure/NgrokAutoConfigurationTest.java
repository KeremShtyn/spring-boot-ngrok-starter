package com.kermel.ngrok.autoconfigure;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.kermel.ngrok.core.NgrokPublicUrlProvider;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NgrokAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NgrokAutoConfiguration.class));

    @Test
    void autoConfigurationCreatesAllBeans() {
        contextRunner
                .withPropertyValues("ngrok.profile-restricted=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(NgrokTunnelRegistry.class);
                    assertThat(context).hasSingleBean(NgrokTunnelManager.class);
                    assertThat(context).hasSingleBean(NgrokBannerPrinter.class);
                    assertThat(context).hasSingleBean(NgrokPublicUrlProvider.class);
                    assertThat(context).hasSingleBean(NgrokLifecycle.class);
                    assertThat(context).hasSingleBean(NgrokTunnelReconnector.class);
                    assertThat(context).hasSingleBean(NgrokClient.class);
                });
    }

    @Test
    void disabledByProperty() {
        contextRunner
                .withPropertyValues("ngrok.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NgrokAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(NgrokTunnelRegistry.class);
                });
    }

    @Test
    void disabledByProfileRestriction() {
        contextRunner
                .withPropertyValues("ngrok.profile-restricted=true")
                // No active profiles set
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NgrokAutoConfiguration.class);
                });
    }

    @Test
    void enabledWithMatchingProfile() {
        contextRunner
                .withPropertyValues(
                        "ngrok.profile-restricted=true",
                        "spring.profiles.active=dev"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(NgrokTunnelRegistry.class);
                });
    }

    @Test
    void enabledWhenProfileRestrictionDisabled() {
        contextRunner
                .withPropertyValues("ngrok.profile-restricted=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(NgrokTunnelRegistry.class);
                });
    }

    @Test
    void propertiesAreBound() {
        contextRunner
                .withPropertyValues(
                        "ngrok.profile-restricted=false",
                        "ngrok.auth-token=my-token",
                        "ngrok.region=eu"
                )
                .run(context -> {
                    NgrokProperties props = context.getBean(NgrokProperties.class);
                    assertThat(props.getAuthToken()).isEqualTo("my-token");
                    assertThat(props.getRegion()).isEqualTo("eu");
                });
    }

    @Test
    void customBeansAreRespected() {
        contextRunner
                .withPropertyValues("ngrok.profile-restricted=false")
                .withBean(NgrokTunnelRegistry.class, NgrokTunnelRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(NgrokTunnelRegistry.class);
                });
    }
}
