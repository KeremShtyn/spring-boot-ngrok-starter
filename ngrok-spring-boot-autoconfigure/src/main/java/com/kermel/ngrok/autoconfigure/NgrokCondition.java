package com.kermel.ngrok.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.List;

/**
 * Custom condition that checks whether ngrok should be activated based on
 * active Spring profiles and the {@code ngrok.profile-restricted} property.
 */
public class NgrokCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String profileRestricted = context.getEnvironment().getProperty("ngrok.profile-restricted", "true");

        if (!"true".equalsIgnoreCase(profileRestricted)) {
            return ConditionOutcome.match("ngrok.profile-restricted is false — activating regardless of profile");
        }

        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        if (activeProfiles.length == 0) {
            return ConditionOutcome.noMatch("No active profiles and ngrok.profile-restricted is true");
        }

        String configuredProfiles = context.getEnvironment().getProperty("ngrok.active-profiles", "dev,local");
        List<String> allowedProfiles = Arrays.stream(configuredProfiles.split(","))
                .map(String::trim)
                .toList();

        for (String activeProfile : activeProfiles) {
            if (allowedProfiles.contains(activeProfile.trim())) {
                return ConditionOutcome.match("Active profile '" + activeProfile + "' is in ngrok.active-profiles");
            }
        }

        return ConditionOutcome.noMatch(
                "None of the active profiles " + Arrays.toString(activeProfiles) +
                        " match ngrok.active-profiles " + allowedProfiles);
    }
}
