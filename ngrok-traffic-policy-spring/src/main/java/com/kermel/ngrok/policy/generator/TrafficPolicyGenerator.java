package com.kermel.ngrok.policy.generator;

import com.kermel.ngrok.policy.annotation.NgrokTrafficPolicy;
import com.kermel.ngrok.policy.annotation.OnHttpRequest;
import com.kermel.ngrok.policy.annotation.OnHttpResponse;
import com.kermel.ngrok.policy.annotation.OnTcpConnect;
import com.kermel.ngrok.policy.dsl.PolicyAction;
import com.kermel.ngrok.policy.dsl.PolicyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Scans for {@link NgrokTrafficPolicy}-annotated beans, invokes their
 * annotated methods to collect {@link PolicyAction}s, and generates
 * ngrok Traffic Policy YAML strings per tunnel.
 *
 * <p>The generated YAML is made available to the tunnel manager before
 * tunnels are created.
 */
public class TrafficPolicyGenerator {

    private static final Logger log = LoggerFactory.getLogger(TrafficPolicyGenerator.class);

    private final ApplicationContext applicationContext;
    private final TrafficPolicyYamlSerializer serializer;

    /** Tunnel name -> generated YAML string */
    private final Map<String, String> generatedPolicies = new LinkedHashMap<>();

    public TrafficPolicyGenerator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.serializer = new TrafficPolicyYamlSerializer();
    }

    /**
     * Scan all {@code @NgrokTrafficPolicy} beans and generate YAML for each tunnel.
     * Should be called during application startup, before tunnels are created.
     */
    public void generate() {
        Map<String, Object> policyBeans = applicationContext.getBeansWithAnnotation(NgrokTrafficPolicy.class);

        if (policyBeans.isEmpty()) {
            log.debug("No @NgrokTrafficPolicy beans found");
            return;
        }

        log.debug("Found {} @NgrokTrafficPolicy bean(s)", policyBeans.size());

        // Collect rules grouped by tunnel name
        Map<String, List<PolicyRule>> rulesByTunnel = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : policyBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> beanClass = bean.getClass();

            NgrokTrafficPolicy annotation = beanClass.getAnnotation(NgrokTrafficPolicy.class);
            if (annotation == null && beanClass.getSuperclass() != null) {
                // May be a proxy — check superclass
                annotation = beanClass.getSuperclass().getAnnotation(NgrokTrafficPolicy.class);
            }

            String tunnelName = (annotation != null && !annotation.tunnel().isEmpty())
                    ? annotation.tunnel()
                    : "";

            List<PolicyRule> rules = rulesByTunnel.computeIfAbsent(tunnelName, k -> new ArrayList<>());
            collectRules(bean, rules);
        }

        // Serialize each tunnel's rules to YAML
        for (Map.Entry<String, List<PolicyRule>> entry : rulesByTunnel.entrySet()) {
            String tunnelName = entry.getKey();
            List<PolicyRule> rules = entry.getValue();

            String yaml = serializer.serialize(rules);
            if (yaml != null) {
                generatedPolicies.put(tunnelName, yaml);
                log.info("Generated traffic policy for tunnel '{}' with {} rule(s)",
                        tunnelName.isEmpty() ? "default" : tunnelName, rules.size());
                log.debug("Traffic policy YAML for tunnel '{}':\n{}", tunnelName.isEmpty() ? "default" : tunnelName, yaml);
            }
        }
    }

    /**
     * Get the generated YAML for a specific tunnel.
     *
     * @param tunnelName tunnel name, or empty string for the default tunnel
     * @return the YAML string, or null if no policy was generated for this tunnel
     */
    public String getGeneratedPolicy(String tunnelName) {
        return generatedPolicies.get(tunnelName);
    }

    /**
     * Get the generated YAML for the default tunnel.
     */
    public String getDefaultPolicy() {
        return generatedPolicies.get("");
    }

    /**
     * Check if any policies were generated.
     */
    public boolean hasPolicies() {
        return !generatedPolicies.isEmpty();
    }

    /**
     * Get all generated policies keyed by tunnel name.
     */
    public Map<String, String> getAllPolicies() {
        return Collections.unmodifiableMap(generatedPolicies);
    }

    private void collectRules(Object bean, List<PolicyRule> rules) {
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(OnHttpRequest.class)) {
                OnHttpRequest ann = method.getAnnotation(OnHttpRequest.class);
                PolicyAction action = invokeMethod(bean, method);
                if (action != null) {
                    rules.add(new PolicyRule(
                            PolicyRule.Phase.ON_HTTP_REQUEST,
                            ann.name(),
                            Arrays.asList(ann.expressions()),
                            action,
                            ann.order()
                    ));
                }
            }

            if (method.isAnnotationPresent(OnHttpResponse.class)) {
                OnHttpResponse ann = method.getAnnotation(OnHttpResponse.class);
                PolicyAction action = invokeMethod(bean, method);
                if (action != null) {
                    rules.add(new PolicyRule(
                            PolicyRule.Phase.ON_HTTP_RESPONSE,
                            ann.name(),
                            Arrays.asList(ann.expressions()),
                            action,
                            ann.order()
                    ));
                }
            }

            if (method.isAnnotationPresent(OnTcpConnect.class)) {
                OnTcpConnect ann = method.getAnnotation(OnTcpConnect.class);
                PolicyAction action = invokeMethod(bean, method);
                if (action != null) {
                    rules.add(new PolicyRule(
                            PolicyRule.Phase.ON_TCP_CONNECT,
                            ann.name(),
                            Arrays.asList(ann.expressions()),
                            action,
                            ann.order()
                    ));
                }
            }
        }
    }

    private PolicyAction invokeMethod(Object bean, Method method) {
        try {
            Object result = method.invoke(bean);
            if (result instanceof PolicyAction action) {
                return action;
            }
            log.warn("Method {}#{} did not return a PolicyAction (returned {})",
                    bean.getClass().getSimpleName(), method.getName(),
                    result != null ? result.getClass().getSimpleName() : "null");
            return null;
        } catch (Exception e) {
            log.error("Failed to invoke policy method {}#{}: {}",
                    bean.getClass().getSimpleName(), method.getName(), e.getMessage());
            return null;
        }
    }
}
