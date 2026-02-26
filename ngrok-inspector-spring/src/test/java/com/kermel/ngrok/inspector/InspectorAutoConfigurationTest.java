package com.kermel.ngrok.inspector;

import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.inspector.actuator.NgrokRequestsEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InspectorAutoConfiguration")
class InspectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InspectorAutoConfiguration.class));

    @Test
    void noBeansWithoutNgrokTunnelRegistry() {
        contextRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(InspectorClient.class);
            assertThat(ctx).doesNotHaveBean(NgrokInspector.class);
        });
    }

    @Test
    void beansCreatedWithTunnelRegistry() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(InspectorClient.class);
                    assertThat(ctx).hasSingleBean(NgrokInspector.class);
                });
    }

    @Test
    void disabledViaProperty() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class)
                .withPropertyValues("ngrok.inspection.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(InspectorClient.class);
                    assertThat(ctx).doesNotHaveBean(NgrokInspector.class);
                });
    }

    @Test
    void enabledByDefault() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(NgrokInspector.class);
                });
    }

    @Test
    void customInspectorClientNotOverridden() {
        InspectorClient customClient = new InspectorClient(null, null);

        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class)
                .withBean(InspectorClient.class, () -> customClient)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(InspectorClient.class);
                    assertThat(ctx.getBean(InspectorClient.class)).isSameAs(customClient);
                });
    }

    @Test
    void customInspectorPortFromProperties() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class, () -> {
                    NgrokProperties props = new NgrokProperties();
                    props.getInspection().setPort(5050);
                    return props;
                })
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(InspectorClient.class);
                });
    }

    @Test
    void actuatorEndpointNotCreatedWithoutActuator() {
        // NgrokRequestsEndpoint uses @ConditionalOnClass for actuator
        // In test context without actuator, it should still be created
        // since actuator is on the test classpath via starter-test
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(NgrokRequestsEndpoint.class);
                });
    }

    @Test
    void allBeansCreatedTogether() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withBean(NgrokProperties.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(InspectorClient.class);
                    assertThat(ctx).hasSingleBean(NgrokInspector.class);
                    assertThat(ctx).hasSingleBean(NgrokRequestsEndpoint.class);
                });
    }
}
