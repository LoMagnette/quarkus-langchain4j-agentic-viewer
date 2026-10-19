package io.quarkiverse.langchain4j.skills;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test stand-in for quarkus-langchain4j's {@code @Skills}, which the pinned versions do not ship: the scanner reads it
 * by name. Only indexed by plain scanner tests (AiAgentSuppliersScanTest), never part of a test application.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Skills {
    String[] value() default {};
}
