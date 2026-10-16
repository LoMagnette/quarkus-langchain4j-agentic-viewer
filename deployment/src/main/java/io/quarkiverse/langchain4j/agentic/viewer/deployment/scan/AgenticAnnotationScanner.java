package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Finds the agent methods of the application in the Jandex index and describes them: {@code @Agent}, the workflow
 * annotations, {@code @SupervisorAgent}, {@code @PlannerAgent} and {@code @HumanInTheLoop}, with the guards
 * ({@code @ExitCondition}, {@code @ActivationCondition}) and {@code @Output} method of workflows.
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
    static final DotName EXIT_CONDITION = DotName.createSimple("dev.langchain4j.agentic.declarative.ExitCondition");
    static final DotName ACTIVATION_CONDITION = DotName
            .createSimple("dev.langchain4j.agentic.declarative.ActivationCondition");
    static final DotName OUTPUT = DotName.createSimple("dev.langchain4j.agentic.declarative.Output");

    /** {@code @LoopAgent.maxIterations()} default. */
    private static final int DEFAULT_MAX_ITERATIONS = 10;

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
                kind == NodeKind.PLANNER ? methodRef(annotatedMethod(method.declaringClass(), PLANNER_SUPPLIER)) : null,
                kind == NodeKind.LOOP ? loopGuard(method.declaringClass(), annotation) : null,
                kind == NodeKind.CONDITIONAL ? branchGuards(method.declaringClass(), annotation) : null,
                isWorkflow(kind) ? methodRef(annotatedMethod(method.declaringClass(), OUTPUT)) : null);
    }

    private static boolean isWorkflow(NodeKind kind) {
        return kind != NodeKind.AI_AGENT && kind != NodeKind.NON_AI_AGENT && kind != NodeKind.HUMAN_IN_THE_LOOP;
    }

    /**
     * Like langchain4j, the exit condition is the {@code @ExitCondition} method of the loop's declaring class.
     */
    private static Guard loopGuard(ClassInfo declaringClass, AnnotationInstance loopAgent) {
        AnnotationValue maxIterations = loopAgent.value("maxIterations");
        MethodInfo exitCondition = annotatedMethod(declaringClass, EXIT_CONDITION);
        AnnotationValue testExitAtLoopEnd = exitCondition == null ? null
                : exitCondition.declaredAnnotation(EXIT_CONDITION).value("testExitAtLoopEnd");
        return Guard.loopExit(
                exitCondition == null ? null : exitCondition.name(),
                maxIterations == null ? DEFAULT_MAX_ITERATIONS : maxIterations.asInt(),
                testExitAtLoopEnd != null && testExitAtLoopEnd.asBoolean());
    }

    /**
     * Like langchain4j, a branch is guarded by the first {@code @ActivationCondition} method of the conditional's
     * declaring class that lists the sub-agent.
     */
    private static Map<String, Guard> branchGuards(ClassInfo declaringClass, AnnotationInstance conditionalAgent) {
        Map<String, Guard> guards = new LinkedHashMap<>();
        for (String subAgent : subAgents(conditionalAgent)) {
            declaringClass.methods().stream()
                    .filter(method -> activates(method, subAgent))
                    .findFirst()
                    .ifPresent(method -> guards.put(subAgent, Guard.activation(method.name())));
        }
        return guards;
    }

    private static boolean activates(MethodInfo method, String subAgent) {
        AnnotationInstance condition = method.declaredAnnotation(ACTIVATION_CONDITION);
        if (condition == null || condition.value() == null) {
            return false;
        }
        for (Type type : condition.value().asClassArray()) {
            if (type.name().toString().equals(subAgent)) {
                return true;
            }
        }
        return false;
    }

    private static MethodInfo annotatedMethod(ClassInfo declaringClass, DotName annotation) {
        return declaringClass.methods().stream()
                .filter(method -> method.hasDeclaredAnnotation(annotation))
                .findFirst()
                .orElse(null);
    }

    /**
     * For suppliers and {@code @Output}, the method is what the build sees; what it returns is only known at runtime.
     */
    private static String methodRef(MethodInfo method) {
        return method == null ? null : method.declaringClass().name() + "#" + method.name();
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
