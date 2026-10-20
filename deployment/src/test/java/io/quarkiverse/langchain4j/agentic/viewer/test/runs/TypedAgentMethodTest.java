package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.TypedAgentMethod;

/**
 * The agent method of a typed builder interface: its one abstract method, possibly inherited.
 */
class TypedAgentMethodTest {

    interface Own {
        String run(String topic);
    }

    interface Base {
        CharSequence run(String topic);
    }

    interface Child extends Base {
        String toString(); // an Object method, redeclared

        default String twice(String topic) {
            return run(topic) + " " + run(topic);
        }

        static Child none() {
            return null;
        }
    }

    interface Covariant extends Base {
        @Override
        String run(String topic);
    }

    interface WithScopeAccess extends Base, AgenticScopeAccess {
    }

    interface Two extends Base {
        String other(String topic);
    }

    @Test
    void theOneAbstractMethodDeclaredOrInherited() throws Exception {
        assertEquals(Own.class.getMethod("run", String.class), method(Own.class));
        assertEquals(Base.class.getMethod("run", String.class), method(Child.class),
                "inherited; default, static and Object methods are no candidates");
        assertEquals(Covariant.class, method(Covariant.class).getDeclaringClass(), "redeclared: counted once");
        assertEquals(Base.class.getMethod("run", String.class), method(WithScopeAccess.class),
                "the methods of langchain4j's interfaces are no candidates");
    }

    @Test
    void noneOrSeveralIsNoAgentMethod() {
        assertTrue(TypedAgentMethod.of(Two.class).isEmpty());
        assertEquals(2, TypedAgentMethod.candidates(Two.class).size());
        assertTrue(TypedAgentMethod.of(Runnable.class).isPresent(), "a plain functional interface has one");
        assertTrue(TypedAgentMethod.of(AgenticScopeAccess.class).isEmpty());
    }

    private static Method method(Class<?> type) {
        return TypedAgentMethod.of(type).orElseThrow(() -> new AssertionError(TypedAgentMethod.candidates(type)));
    }
}
