package io.quarkiverse.langchain4j.mcp.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stand-in for the quarkus-langchain4j-mcp annotation (same name), which is not a dependency of this extension.
 * Only used by scanner unit tests on a Jandex index, never in a test application.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpToolBox {
    String[] value() default {};
}
