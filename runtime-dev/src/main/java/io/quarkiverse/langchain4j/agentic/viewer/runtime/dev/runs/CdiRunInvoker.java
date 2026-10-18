package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.ManagedContext;

/**
 * Runs a system: a declarative root interface is a CDI bean, whose root method is called with arguments converted
 * from the run form JSON; a builder system's root agent is invoked with the converted values.
 */
public class CdiRunInvoker implements RunInvoker {

    private static final Logger LOG = Logger.getLogger(CdiRunInvoker.class);

    private static final String AGENTIC_ANNOTATIONS = "dev.langchain4j.agentic.";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final Function<String, Optional<Object>> builderRoots;

    /** For declarative systems only. */
    public CdiRunInvoker() {
        this(systemId -> Optional.empty());
    }

    /**
     * @param builderRoots the root agent of a builder system by system id ({@code BuilderSystems})
     */
    public CdiRunInvoker(Function<String, Optional<Object>> builderRoots) {
        this.builderRoots = builderRoots;
    }

    @Override
    public Object invoke(AgenticSystem system, Map<String, Object> inputs) throws Exception {
        // a declarative system may be MIXED too (an agent of it injects a builder system): the root tells
        Optional<Object> builderRoot = system.origin() == SystemOrigin.DECLARATIVE ? Optional.empty()
                : builderRoots.apply(system.id());
        if (builderRoot.isEmpty() && system.origin() == SystemOrigin.BUILDER) {
            throw new IllegalStateException("No agent for the builder system " + system.id());
        }
        Call call = builderRoot.isPresent() ? builderCall(system, builderRoot.get(), inputs) : declarativeCall(system, inputs);
        // a run is handled like a request: request-scoped state, such as the chat memory of the agents, starts empty and
        // is cleared when the run ends, instead of leaking from one run into the next on the reused run threads
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activated = !requestContext.isActive();
        if (activated) {
            requestContext.activate();
        }
        try {
            return call.invoke();
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } finally {
            if (activated) {
                requestContext.terminate();
            }
        }
    }

    @FunctionalInterface
    private interface Call {
        Object invoke() throws Exception;
    }

    /** The root method of the root interface's bean, with arguments converted from the form. */
    private static Call declarativeCall(AgenticSystem system, Map<String, Object> inputs) throws ClassNotFoundException {
        Class<?> type = Class.forName(system.sourceRef().className(), true, Thread.currentThread().getContextClassLoader());
        Method method = rootMethod(type, system.sourceRef().methodName());
        Object[] arguments = arguments(method, inputs);
        Object memoryId = inventedMemoryId(method, inputs, arguments);
        return () -> {
            Object bean = Arc.container().instance(type).get();
            if (bean == null) {
                throw new IllegalStateException("No CDI bean for " + type.getName());
            }
            return invokeAndForget(bean, method, arguments, memoryId);
        };
    }

    /**
     * Calls the root, then evicts the scope of a memory id made up for the run: nobody can reuse it, and langchain4j
     * keeps a scope with a memory id registered (only ephemeral scopes end with the root call).
     */
    private static Object invokeAndForget(Object root, Method method, Object[] arguments, Object memoryId)
            throws Exception {
        try {
            return method.invoke(root, arguments);
        } finally {
            if (memoryId != null && ClientProxy.unwrap(root) instanceof AgenticScopeAccess access) {
                try {
                    access.evictAgenticScope(memoryId);
                } catch (RuntimeException e) {
                    LOG.debugf(e, "Agentic viewer: cannot evict the scope of the run memory id %s", memoryId);
                }
            }
        }
    }

    /**
     * @return the memory id {@link #arguments} made up because the form gave none, null if there is none
     */
    static Object inventedMemoryId(Method method, Map<String, Object> inputs, Object[] arguments) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(MemoryId.class) && inputs.get(key(parameters[i])) == null) {
                return arguments[i];
            }
        }
        return null;
    }

    /**
     * A builder system (T5.6): an UntypedAgent is invoked with the form values converted to the types of its inputs;
     * a typed agent interface through its method, as a declarative root.
     */
    private static Call builderCall(AgenticSystem system, Object root, Map<String, Object> inputs) {
        if (root instanceof UntypedAgent untyped) {
            Map<String, Object> values = values(system, inputs);
            return () -> untyped.invoke(values);
        }
        Method method = agentMethod(((AgentInstance) root).type());
        Object[] arguments = arguments(method, inputs);
        Object memoryId = inventedMemoryId(method, inputs, arguments);
        return () -> invokeAndForget(root, method, arguments, memoryId);
    }

    /** The one method of a typed agent interface built with a builder (it carries no agentic annotation). */
    static Method agentMethod(Class<?> type) {
        List<Method> methods = Arrays.stream(type.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()) && method.getDeclaringClass() == type)
                .toList();
        if (methods.size() != 1) {
            throw new IllegalStateException("Expected one agent method on " + type.getName() + ", found " + methods);
        }
        return methods.get(0);
    }

    /**
     * The form values by input name, converted to the input types (an UntypedAgent takes a map).
     *
     * @throws IllegalArgumentException naming the input, for a value that cannot be converted
     */
    static Map<String, Object> values(AgenticSystem system, Map<String, Object> inputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (SystemInput input : system.inputs()) {
            Object value = inputs.get(input.name());
            if (value == null) {
                continue;
            }
            try {
                JavaType target = MAPPER.getTypeFactory().constructFromCanonical(input.javaType());
                values.put(input.name(), MAPPER.convertValue(value, target));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot convert input '" + input.name() + "' to " + input.javaType()
                        + ": " + e.getMessage(), e);
            }
        }
        return values;
    }

    /**
     * The agent method of the root interface with that name (the one carrying an agentic annotation); an overloaded
     * one is named with its parameter types, {@code name(java.lang.String,int)} (the scanner's method name).
     */
    public static Method rootMethod(Class<?> type, String name) {
        int paren = name.indexOf('(');
        String methodName = paren < 0 ? name : name.substring(0, paren);
        List<String> parameterTypes = paren < 0 ? null
                : name.length() == paren + 2 ? List.of() : List.of(name.substring(paren + 1, name.length() - 1).split(","));
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers()))
                .filter(method -> parameterTypes == null || Arrays.stream(method.getParameterTypes())
                        .map(Class::getName).toList().equals(parameterTypes))
                .sorted(Comparator.comparing((Method method) -> Arrays.stream(method.getAnnotations())
                        .noneMatch(a -> a.annotationType().getName().startsWith(AGENTIC_ANNOTATIONS))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No method " + name + " on " + type.getName()));
    }

    /**
     * Converts the form values to the method parameter types: a parameter takes the value of its scope key ({@code @V}
     * value or parameter name); a {@code @MemoryId} parameter takes its value or a fresh id (a String or a UUID; none
     * for other types).
     *
     * @throws IllegalArgumentException naming the parameter, for a missing primitive or a value that cannot be converted
     */
    public static Object[] arguments(Method method, Map<String, Object> inputs) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (providedByLangChain4j(parameter)) {
                continue;
            }
            String key = key(parameter);
            Object value = inputs.get(key);
            if (value == null && parameter.isAnnotationPresent(MemoryId.class)) {
                value = freshMemoryId(parameter.getType());
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

    private static Object freshMemoryId(Class<?> type) {
        if (type == UUID.class) {
            return UUID.randomUUID();
        }
        return type.isAssignableFrom(String.class) ? UUID.randomUUID().toString() : null;
    }

    /** The scope, the loop counter, the invocation parameters, the user message: not run inputs (no form field). */
    private static boolean providedByLangChain4j(Parameter parameter) {
        return AgenticScope.class.isAssignableFrom(parameter.getType())
                || InvocationParameters.class.isAssignableFrom(parameter.getType())
                || parameter.isAnnotationPresent(LoopCounter.class)
                || parameter.isAnnotationPresent(dev.langchain4j.service.UserMessage.class);
    }

    /** The scope key of a parameter, as the build-time scanner names the run inputs (langchain4j rules). */
    private static String key(Parameter parameter) {
        P p = parameter.getAnnotation(P.class);
        if (p != null && !p.name().isBlank()) {
            return p.name();
        }
        V v = parameter.getAnnotation(V.class);
        if (v != null && !v.value().isBlank()) {
            return v.value();
        }
        K k = parameter.getAnnotation(K.class);
        return k != null ? k.value().getSimpleName() : parameter.getName();
    }
}
