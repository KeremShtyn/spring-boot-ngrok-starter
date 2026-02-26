package com.kermel.ngrok.policy.generator;

import com.kermel.ngrok.policy.dsl.PolicyAction;
import com.kermel.ngrok.policy.dsl.PolicyRule;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serializes a list of {@link PolicyRule}s into ngrok Traffic Policy YAML format.
 */
public class TrafficPolicyYamlSerializer {

    /**
     * Serialize policy rules into a YAML string suitable for ngrok.
     *
     * @param rules the policy rules to serialize
     * @return a YAML string in ngrok Traffic Policy format
     */
    public String serialize(List<PolicyRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // Group rules by phase, sorted by order within each phase
        Map<PolicyRule.Phase, List<PolicyRule>> byPhase = rules.stream()
                .sorted(Comparator.comparingInt(PolicyRule::order))
                .collect(Collectors.groupingBy(PolicyRule::phase, LinkedHashMap::new, Collectors.toList()));

        Map<String, Object> policyMap = new LinkedHashMap<>();

        for (Map.Entry<PolicyRule.Phase, List<PolicyRule>> entry : byPhase.entrySet()) {
            List<Map<String, Object>> ruleList = new ArrayList<>();

            for (PolicyRule rule : entry.getValue()) {
                Map<String, Object> ruleMap = new LinkedHashMap<>();

                if (rule.name() != null && !rule.name().isEmpty()) {
                    ruleMap.put("name", rule.name());
                }

                if (rule.expressions() != null && !rule.expressions().isEmpty()) {
                    ruleMap.put("expressions", new ArrayList<>(rule.expressions()));
                }

                List<Map<String, Object>> actions = new ArrayList<>();
                Map<String, Object> actionMap = new LinkedHashMap<>();
                actionMap.put("type", rule.action().getType());

                Map<String, Object> config = rule.action().getConfig();
                if (config != null && !config.isEmpty()) {
                    actionMap.put("config", config);
                }

                actions.add(actionMap);
                ruleMap.put("actions", actions);

                ruleList.add(ruleMap);
            }

            policyMap.put(entry.getKey().yamlKey(), ruleList);
        }

        return toYaml(policyMap);
    }

    private String toYaml(Map<String, Object> policyMap) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);

        Yaml yaml = new Yaml(options);
        return yaml.dump(policyMap);
    }
}
