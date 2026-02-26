package com.kermel.ngrok.policy.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an {@code on_http_request} traffic policy rule.
 * The method must return a {@link com.kermel.ngrok.policy.dsl.PolicyAction}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnHttpRequest {

    /** CEL expression(s) that filter when this rule applies. */
    String[] expressions() default {};

    /** Rule name (for readability in logs/debugging). */
    String name() default "";

    /** Execution order (lower = earlier). */
    int order() default 0;
}
