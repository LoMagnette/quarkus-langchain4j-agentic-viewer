package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Finds the agent methods of the application in the Jandex index and describes them: {@code @Agent}, the workflow
 * annotations, {@code @SupervisorAgent}, {@code @PlannerAgent} and {@code @HumanInTheLoop}.
 * Agents declared by the langchain4j libraries themselves are skipped.
 */
public class AgenticAnnotationScanner {

    static final DotName AGENT = DotName.createSimple("dev.langchain4j.agentic.Agent");
    static final DotName SEQUENCE_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.SequenceAgent");
    static final DotName PARALLEL_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.ParallelAgent");
    static final DotName PARALLEL_MAPPER_AGENT = DotName
            .createSimple("dev.langchain4j.agentic.declarative.ParallelMapperAgent");
    static final DotName LOOP_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.LoopAgent");
    static final DotName CONDITIONAL_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.ConditionalAgent");
    static final DotName SUPERVISOR_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.SupervisorAgent");
    static final DotName PLANNER_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.PlannerAgent");
    static final DotName HUMAN_IN_THE_LOOP = DotName.createSimple("dev.langchain4j.agentic.declarative.HumanInTheLoop");
    static final DotName PLANNER_SUPPLIER = DotName.createSimple("dev.langchain4j.agentic.declarative.PlannerSupplier");

    private static final DotName V = DotName.createSimple("dev.langchain4j.service.V");
    private static final DotName MEMORY_ID = DotName.createSimple("dev.langchain4j.service.MemoryId");
    private static final DotName AGENTIC_SCOPE = DotName.createSimple("dev.langchain4j.agentic.scope.AgenticScope");
    private static final DotName NO_TYPED_KEY = DotName.createSimple("dev.langchain4j.agentic.Agent$NoTypedKey");
    private static final String LANGCHAIN4J_PACKAGE_PREFIX = "dev.langchain4j.";

    /** Workflow annotations and the node kind they define. */
    private static final Map<DotName, NodeKind> WORKFLOW_ANNOTATIONS = Map.of(
            SEQUENCE_AGENT, NodeKind.SEQUENCE,
            PARALLEL_AGENT, NodeKind.PARALLEL,
            PARALLEL_MAPPER_AGENT, NodeKind.PARALLEL_MAPPER,
            LOOP_AGENT, NodeKind.LOOP,
            CONDITIONAL_AGENT, NodeKind.CONDITIONAL,
            SUPERVISOR_AGENT, NodeKind.SUPERVISOR,
            PLANNER_AGENT, NodeKind.PLANNER,
            HUMAN_IN_THE_LOOP, NodeKind.HUMAN_IN_THE_LOOP);

    /**
     * @return one descriptor per agent method, sorted by id
     */
    public List<AgentDescriptor> scan(IndexView index) {
        List<AgentDescriptor> descriptors = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(AGENT)) {
            agentMethod(annotation).ifPresent(method -> descriptors.add(describe(method, annotation, agentKind(method))));
        }
        WORKFLOW_ANNOTATIONS.forEach((annotationName, kind) -> {
            for (AnnotationInstance annotation : index.getAnnotations(annotationName)) {
                agentMethod(annotation).ifPresent(method -> descriptors.add(describe(method, annotation, kind)));
            }
        });
        descriptors.sort(Comparator.comparing(AgentDescriptor::id));
        return descriptors;
    }

    private static Optional<MethodInfo> agentMethod(AnnotationInstance annotation) {
        if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
            return Optional.empty();
        }
        MethodInfo method = annotation.target().asMethod();
        if (method.declaringClass().name().toString().startsWith(LANGCHAIN4J_PACKAGE_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(method);
    }

    /**
     * An abstract interface method is implemented by the LLM; a concrete method is a plain Java agent.
     */
    private static NodeKind agentKind(MethodInfo method) {
        return method.declaringClass().isInterface() && Modifier.isAbstract(method.flags())
                ? NodeKind.AI_AGENT
                : NodeKind.NON_AI_AGENT;
    }

    private static AgentDescriptor describe(MethodInfo method, AnnotationInstance annotation, NodeKind kind) {
        String declaringClass = method.declaringClass().name().toString();
        String name = nonBlank(stringValue(annotation, "name"));
        String description = nonBlank(stringValue(annotation, "description"));
        if (description == null) {
            description = nonBlank(stringValue(annotation, "value")); // @Agent("...") / @HumanInTheLoop("...")
        }
        return new AgentDescriptor(
                declaringClass + "#" + method.name(),
                declaringClass,
                method.name(),
                kind,
                name != null ? name : method.name(),
                description,
                outputKey(annotation),
                inputKeys(method),
                subAgents(annotation),
                nonBlank(stringValue(annotation, "itemsProvider")),
                kind == NodeKind.PLANNER ? plannerSupplier(method.declaringClass()) : null);
    }

    /**
     * The concrete planner class is only known when the supplier runs; the supplier method is what the build sees.
     */
    private static String plannerSupplier(ClassInfo declaringClass) {
        return declaringClass.methods().stream()
                .filter(method -> method.hasDeclaredAnnotation(PLANNER_SUPPLIER))
                .map(method -> declaringClass.name() + "#" + method.name())
                .findFirst()
                .orElse(null);
    }

    private static String outputKey(AnnotationInstance annotation) {
        String outputKey = nonBlank(stringValue(annotation, "outputKey"));
        if (outputKey != null) {
            return outputKey;
        }
        AnnotationValue typedKey = annotation.value("typedOutputKey");
        if (typedKey != null && !typedKey.asClass().name().equals(NO_TYPED_KEY)) {
            // TypedKey.name() defaults to the simple class name; an overridden name() is only known at runtime
            return typedKey.asClass().name().withoutPackagePrefix().replaceFirst(".*\\$", "");
        }
        return null;
    }

    private static List<String> inputKeys(MethodInfo method) {
        List<String> keys = new ArrayList<>();
        for (MethodParameterInfo parameter : method.parameters()) {
            if (parameter.type().name().equals(AGENTIC_SCOPE) || parameter.hasAnnotation(MEMORY_ID)) {
                continue;
            }
            AnnotationInstance v = parameter.annotation(V);
            String key = v != null ? nonBlank(v.value().asString()) : parameter.name();
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static List<String> subAgents(AnnotationInstance annotation) {
        List<String> subAgents = new ArrayList<>();
        AnnotationValue many = annotation.value("subAgents");
        if (many != null) {
            for (Type type : many.asClassArray()) {
                subAgents.add(type.name().toString());
            }
        }
        AnnotationValue single = annotation.value("subAgent");
        if (single != null) {
            subAgents.add(single.asClass().name().toString());
        }
        return subAgents;
    }

    private static String stringValue(AnnotationInstance annotation, String name) {
        AnnotationValue value = annotation.value(name);
        return value == null ? null : value.asString();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
