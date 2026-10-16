package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

/**
 * Runs a declarative system: its root interface is a CDI bean, whose root method is called with arguments converted
 * from the run form JSON.
 */
public class CdiRunInvoker implements RunInvoker {

    private static final String AGENTIC_ANNOTATIONS = "dev.langchain4j.agentic.";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public Object invoke(AgenticSystem system, Map<String, Object> inputs) throws Exception {
        Class<?> type = Class.forName(system.sourceRef().className(), true, Thread.currentThread().getContextClassLoader());
        Method method = rootMethod(type, system.sourceRef().methodName());
        Object[] arguments = arguments(method, inputs);
        // a run is handled like a request: request-scoped state, such as the chat memory of the agents, starts empty and
        // is cleared when the run ends, instead of leaking from one run into the next on the reused run threads
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activated = !requestContext.isActive();
        if (activated) {
            requestContext.activate();
        }
        try {
            Object bean = Arc.container().instance(type).get();
            if (bean == null) {
                throw new IllegalStateException("No CDI bean for " + type.getName());
            }
            return method.invoke(bean, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } finally {
            if (activated) {
                requestContext.terminate();
            }
        }
    }

    /**
     * The agent method of the root interface with that name (the one carrying an agentic annotation).
     */
    static Method rootMethod(Class<?> type, String name) {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name) && !Modifier.isStatic(method.getModifiers()))
                .sorted(Comparator.comparing((Method method) -> Arrays.stream(method.getAnnotations())
                        .noneMatch(a -> a.annotationType().getName().startsWith(AGENTIC_ANNOTATIONS))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No method " + name + " on " + type.getName()));
    }

    /**
     * Converts the form values to the method parameter types: a parameter takes the value of its scope key ({@code @V}
     * value or parameter name); a {@code @MemoryId} parameter takes its value or a fresh id.
     *
     * @throws IllegalArgumentException naming the parameter, for a missing primitive or a value that cannot be converted
     */
    public static Object[] arguments(Method method, Map<String, Object> inputs) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (AgenticScope.class.isAssignableFrom(parameter.getType())) {
                continue; // provided by langchain4j
            }
            String key = key(parameter);
            Object value = inputs.get(key);
            if (value == null && parameter.isAnnotationPresent(MemoryId.class)) {
                value = UUID.randomUUID().toString();
            }
            if (value == null) {
                if (parameter.getType().isPrimitive()) {
                    throw new IllegalArgumentException("Missing value for input '" + key + "'");
                }
                continue;
            }
            JavaType target = MAPPER.getTypeFactory().constructType(parameter.getParameterizedType());
            try {
                arguments[i] = MAPPER.convertValue(value, target);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot convert input '" + key + "' to "
                        + parameter.getParameterizedType().getTypeName() + ": " + e.getMessage(), e);
            }
        }
        return arguments;
    }

    private static String key(Parameter parameter) {
        V v = parameter.getAnnotation(V.class);
        return v != null && !v.value().isBlank() ? v.value() : parameter.getName();
    }
}
