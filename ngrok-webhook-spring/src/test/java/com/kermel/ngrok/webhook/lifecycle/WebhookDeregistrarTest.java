package com.kermel.ngrok.webhook.lifecycle;

import com.kermel.ngrok.webhook.WebhookAutoRegistrar;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import com.kermel.ngrok.webhook.provider.WebhookProvider;
import com.kermel.ngrok.webhook.provider.WebhookRegistrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDeregistrar")
class WebhookDeregistrarTest {

    @Mock
    private WebhookAutoRegistrar registrar;

    @Mock
    private WebhookProvider stripeProvider;

    @Mock
    private WebhookProvider githubProvider;

    @Test
    void deregistersAutoDeregisterWebhooks() throws Exception {
        WebhookRegistrationResult stripeResult = WebhookRegistrationResult.success("stripe", "we_123", "https://x/stripe", true);
        when(registrar.getAutoDeregisterRegistrations()).thenReturn(List.of(stripeResult));
        when(stripeProvider.name()).thenReturn("stripe");

        WebhookDeregistrar deregistrar = new WebhookDeregistrar(registrar, List.of(stripeProvider));
        deregistrar.destroy();

        verify(stripeProvider).deregister(stripeResult);
    }

    @Test
    void noAutoDeregisterRegistrations_noAction() throws Exception {
        when(registrar.getAutoDeregisterRegistrations()).thenReturn(List.of());

        WebhookDeregistrar deregistrar = new WebhookDeregistrar(registrar, List.of(stripeProvider));
        deregistrar.destroy();

        verify(stripeProvider, never()).deregister(any());
    }

    @Test
    void deregistrationFailure_doesNotPreventOthers() throws Exception {
        WebhookRegistrationResult r1 = WebhookRegistrationResult.success("stripe", "we_1", "https://x/stripe", true);
        WebhookRegistrationResult r2 = WebhookRegistrationResult.success("github", "gh_1", "https://x/github", true);
        when(registrar.getAutoDeregisterRegistrations()).thenReturn(List.of(r1, r2));
        when(stripeProvider.name()).thenReturn("stripe");
        when(githubProvider.name()).thenReturn("github");
        doThrow(new WebhookRegistrationException("Stripe API error")).when(stripeProvider).deregister(r1);

        WebhookDeregistrar deregistrar = new WebhookDeregistrar(registrar, List.of(stripeProvider, githubProvider));

        assertThatNoException().isThrownBy(deregistrar::destroy);
        verify(githubProvider).deregister(r2);
    }

    @Test
    void missingProvider_skipped() throws Exception {
        WebhookRegistrationResult result = WebhookRegistrationResult.success("unknown", "id_1", "https://x/unknown", true);
        when(registrar.getAutoDeregisterRegistrations()).thenReturn(List.of(result));

        WebhookDeregistrar deregistrar = new WebhookDeregistrar(registrar, List.of());

        assertThatNoException().isThrownBy(deregistrar::destroy);
    }
}
