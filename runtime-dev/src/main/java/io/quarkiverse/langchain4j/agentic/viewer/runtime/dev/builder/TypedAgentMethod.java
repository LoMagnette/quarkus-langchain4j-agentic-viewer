package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The agent method of a typed agent interface given to a builder: it carries no agentic annotation, so it is the one
 * abstract method of the interface, declared on it or inherited from a parent interface. Default and static methods,
 * the methods of {@code Object} and those of langchain4j's own interfaces (e.g. {@code AgenticScopeAccess}) are not
 * candidates; a method redeclared by a sub-interface counts once. Used for the run and for the inputs of the topology.
 */
public final class TypedAgentMethod {

    private static final String LIBRARY = "dev.langchain4j.";

    private TypedAgentMethod() {
    }

    /**
     * @return the agent method, empty when the interface has none or several
     */
    public static Optional<Method> of(Class<?> type) {
        List<Method> candidates = candidates(type);
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    /**
     * @return the candidate agent methods, most specific declaration of each signature
     */
    public static List<Method> candidates(Class<?> type) {
        Map<String, Method> bySignature = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || isObjectMethod(method) || method.getDeclaringClass().getName().startsWith(LIBRARY)) {
                continue;
            }
            // a covariant redeclaration is listed with its parent's: the sub-interface's wins
            bySignature.merge(method.getName() + Arrays.toString(method.getParameterTypes()), method,
                    (known, other) -> known.getDeclaringClass().isAssignableFrom(other.getDeclaringClass()) ? other : known);
        }
        return List.copyOf(bySignature.values());
    }

    /** A method of {@code Object}, possibly redeclared by an interface ({@code String toString();}). */
    private static boolean isObjectMethod(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
