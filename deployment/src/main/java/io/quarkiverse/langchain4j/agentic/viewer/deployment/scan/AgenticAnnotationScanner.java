package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
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

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AiAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.RemoteAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.ToolInfo;

/**
 * Finds the agent methods of the application in the Jandex index and describes them: {@code @Agent}, the workflow
 * annotations, {@code @SupervisorAgent}, {@code @PlannerAgent}, {@code @HumanInTheLoop}, and the remote
 * {@code @A2AClientAgent} and {@code @McpClientAgent}, with the guards
 * ({@code @ExitCondition}, {@code @ActivationCondition}) and {@code @Output} method of workflows, and the prompts,
 * model and tools of AI agents.
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
    static final DotName A2A_CLIENT_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.A2AClientAgent");
    static final DotName A2A_SERVER_URL_SUPPLIER = DotName
            .createSimple("dev.langchain4j.agentic.declarative.A2AServerUrlSupplier");
    static final DotName MCP_CLIENT_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.McpClientAgent");
    static final DotName MCP_CLIENT_SUPPLIER = DotName.createSimple("dev.langchain4j.agentic.declarative.McpClientSupplier");
    static final DotName REGISTRY_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.RegistryAgent");
    static final DotName EXIT_CONDITION = DotName.createSimple("dev.langchain4j.agentic.declarative.ExitCondition");
    static final DotName ACTIVATION_CONDITION = DotName
            .createSimple("dev.langchain4j.agentic.declarative.ActivationCondition");
    static final DotName OUTPUT = DotName.createSimple("dev.langchain4j.agentic.declarative.Output");

    static final DotName SYSTEM_MESSAGE = DotName.createSimple("dev.langchain4j.service.SystemMessage");
    static final DotName USER_MESSAGE = DotName.createSimple("dev.langchain4j.service.UserMessage");
    static final DotName TOOL = DotName.createSimple("dev.langchain4j.agent.tool.Tool");
    static final DotName TOOLS_SUPPLIER = DotName.createSimple("dev.langchain4j.agentic.declarative.ToolsSupplier");
    static final DotName TOOL_PROVIDER_SUPPLIER = DotName
            .createSimple("dev.langchain4j.agentic.declarative.ToolProviderSupplier");
    static final DotName MODEL_NAME = DotName.createSimple("io.quarkiverse.langchain4j.ModelName");
    static final DotName TOOL_BOX = DotName.createSimple("io.quarkiverse.langchain4j.ToolBox");
    static final DotName MCP_TOOL_BOX = DotName.createSimple("io.quarkiverse.langchain4j.mcp.runtime.McpToolBox");

    /** {@code @LoopAgent.maxIterations()} default. */
    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private static final DotName V = DotName.createSimple("dev.langchain4j.service.V");
    private static final DotName K = DotName.createSimple("dev.langchain4j.agentic.declarative.K");
    private static final DotName P = DotName.createSimple("dev.langchain4j.agent.tool.P");
    private static final DotName MEMORY_ID = DotName.createSimple("dev.langchain4j.service.MemoryId");
    private static final DotName LOOP_COUNTER = DotName.createSimple("dev.langchain4j.agentic.declarative.LoopCounter");
    private static final DotName INVOCATION_PARAMETERS = DotName
            .createSimple("dev.langchain4j.invocation.InvocationParameters");
    private static final DotName USER_MESSAGE_PARAMETER = USER_MESSAGE;
    private static final DotName AGENTIC_SCOPE = DotName.createSimple("dev.langchain4j.agentic.scope.AgenticScope");
    private static final DotName NO_TYPED_KEY = DotName.createSimple("dev.langchain4j.agentic.Agent$NoTypedKey");
    private static final String LANGCHAIN4J_PACKAGE_PREFIX = "dev.langchain4j.";

    /** Annotations other than {@code @Agent} and the node kind they define: workflows and remote agents. */
    private static final Map<DotName, NodeKind> WORKFLOW_ANNOTATIONS = Map.ofEntries(
            Map.entry(SEQUENCE_AGENT, NodeKind.SEQUENCE),
            Map.entry(PARALLEL_AGENT, NodeKind.PARALLEL),
            Map.entry(PARALLEL_MAPPER_AGENT, NodeKind.PARALLEL_MAPPER),
            Map.entry(LOOP_AGENT, NodeKind.LOOP),
            Map.entry(CONDITIONAL_AGENT, NodeKind.CONDITIONAL),
            Map.entry(SUPERVISOR_AGENT, NodeKind.SUPERVISOR),
            Map.entry(PLANNER_AGENT, NodeKind.PLANNER),
            Map.entry(HUMAN_IN_THE_LOOP, NodeKind.HUMAN_IN_THE_LOOP),
            Map.entry(A2A_CLIENT_AGENT, NodeKind.A2A_CLIENT_AGENT),
            Map.entry(MCP_CLIENT_AGENT, NodeKind.MCP_CLIENT_AGENT),
            Map.entry(REGISTRY_AGENT, NodeKind.REGISTRY_AGENT));

    /**
     * @return one descriptor per agent method, sorted by id; also one per agent method a class inherits from an
     *         indexed supertype without declaring agent methods of its own (langchain4j reads {@code getMethods()})
     */
    public List<AgentDescriptor> scan(IndexView index) {
        List<AgentDescriptor> descriptors = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(AGENT)) {
            agentMethod(annotation)
                    .ifPresent(method -> descriptors.add(describe(index, method, annotation, agentKind(method))));
        }
        WORKFLOW_ANNOTATIONS.forEach((annotationName, kind) -> {
            for (AnnotationInstance annotation : index.getAnnotations(annotationName)) {
                agentMethod(annotation).ifPresent(method -> descriptors.add(describe(index, method, annotation, kind)));
            }
        });
        descriptors.addAll(inherited(index, descriptors));
        descriptors.sort(Comparator.comparing(AgentDescriptor::id));
        return descriptors;
    }

    /** The agents of the indexed subtypes of agent classes that declare no agent method of their own. */
    private static List<AgentDescriptor> inherited(IndexView index, List<AgentDescriptor> declared) {
        Map<String, List<AgentDescriptor>> byClass = new LinkedHashMap<>();
        declared.forEach(d -> byClass.computeIfAbsent(d.declaringClass(), c -> new ArrayList<>()).add(d));
        Map<String, AgentDescriptor> inherited = new LinkedHashMap<>();
        byClass.forEach((className, agents) -> {
            ClassInfo type = index.getClassByName(DotName.createSimple(className));
            if (type == null) {
                return;
            }
            List<ClassInfo> subtypes = new ArrayList<>(type.isInterface() ? index.getAllKnownSubinterfaces(type.name())
                    : index.getAllKnownSubclasses(type.name()));
            for (ClassInfo subtype : subtypes) {
                String subclass = subtype.name().toString();
                if (!byClass.containsKey(subclass) && !subclass.startsWith(LANGCHAIN4J_PACKAGE_PREFIX)) {
                    agents.forEach(agent -> inherited.putIfAbsent(subclass + "#" + agent.methodName(),
                            agent.inheritedBy(subclass)));
                }
            }
        });
        return new ArrayList<>(inherited.values());
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

    private static AgentDescriptor describe(IndexView index, MethodInfo method, AnnotationInstance annotation,
            NodeKind kind) {
        String declaringClass = method.declaringClass().name().toString();
        String name = nonBlank(stringValue(annotation, "name"));
        String description = nonBlank(stringValue(annotation, "description"));
        if (kind == NodeKind.REGISTRY_AGENT) {
            // @RegistryAgent("name"): the agent registered under that name, known at runtime only
            String registered = nonBlank(stringValue(annotation, "value"));
            description = registered == null ? null : "Agent \"" + registered + "\" of the agents registry";
        } else if (description == null) {
            description = nonBlank(stringValue(annotation, "value")); // @Agent("...") / @HumanInTheLoop("...")
        }
        // an overloaded agent method is told apart by its parameter types
        String methodName = isOverloaded(method) ? signature(method) : method.name();
        return new AgentDescriptor(
                declaringClass + "#" + methodName,
                declaringClass,
                methodName,
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
                isWorkflow(kind) ? methodRef(annotatedMethod(method.declaringClass(), OUTPUT)) : null,
                booleanValue(annotation, "optional"),
                booleanValue(annotation, "async"),
                kind == NodeKind.AI_AGENT ? aiDetails(index, method) : null,
                inputs(index, method),
                remoteDetails(method, annotation, kind));
    }

    /**
     * Like langchain4j: an A2A agent has its server URL in the annotation or from an {@code @A2AServerUrlSupplier}
     * method, an MCP agent gets its client from an {@code @McpClientSupplier} method; both declared next to it.
     */
    private static RemoteAgentDetails remoteDetails(MethodInfo method, AnnotationInstance annotation, NodeKind kind) {
        return switch (kind) {
            case A2A_CLIENT_AGENT -> new RemoteAgentDetails(
                    nonBlank(stringValue(annotation, "a2aServerUrl")),
                    methodRef(annotatedMethod(method.declaringClass(), A2A_SERVER_URL_SUPPLIER)),
                    nonBlank(stringValue(annotation, "tenant")), null, null);
            case MCP_CLIENT_AGENT -> new RemoteAgentDetails(null, null, null,
                    nonBlank(stringValue(annotation, "toolName")),
                    methodRef(annotatedMethod(method.declaringClass(), MCP_CLIENT_SUPPLIER)));
            default -> null;
        };
    }

    /**
     * Same sources as quarkus-langchain4j-agentic: {@code @ModelName} and {@code @ToolBox} on the agent method, tool
     * suppliers declared next to it.
     */
    private static AiAgentDetails aiDetails(IndexView index, MethodInfo method) {
        AnnotationInstance systemMessage = method.declaredAnnotation(SYSTEM_MESSAGE);
        if (systemMessage == null) {
            systemMessage = method.declaringClass().declaredAnnotation(SYSTEM_MESSAGE);
        }
        AnnotationInstance modelName = method.declaredAnnotation(MODEL_NAME);
        return new AiAgentDetails(
                template(systemMessage),
                template(method.declaredAnnotation(USER_MESSAGE)),
                modelName == null ? null : nonBlank(modelName.value().asString()),
                tools(index, method),
                runtimeToolSources(method));
    }

    private static List<ToolInfo> tools(IndexView index, MethodInfo method) {
        List<ToolInfo> tools = new ArrayList<>();
        AnnotationInstance toolBox = method.declaredAnnotation(TOOL_BOX);
        if (toolBox == null || toolBox.value() == null) {
            return tools;
        }
        for (Type toolClass : toolBox.value().asClassArray()) {
            // a tool class is a bean: its @Tool methods may be inherited
            for (ClassInfo type = index.getClassByName(toolClass.name()); type != null; type = superClass(index, type)) {
                for (MethodInfo toolMethod : type.methods()) {
                    AnnotationInstance tool = toolMethod.declaredAnnotation(TOOL);
                    if (tool != null) {
                        String name = nonBlank(stringValue(tool, "name"));
                        AnnotationValue description = tool.value();
                        tools.add(new ToolInfo(
                                name != null ? name : toolMethod.name(),
                                type.name().toString(),
                                toolMethod.name(),
                                description == null ? null : nonBlank(String.join("\n", description.asStringArray()))));
                    }
                }
            }
        }
        return tools;
    }

    private static ClassInfo superClass(IndexView index, ClassInfo type) {
        return type.superName() == null ? null : index.getClassByName(type.superName());
    }

    private static List<String> runtimeToolSources(MethodInfo method) {
        List<String> sources = new ArrayList<>();
        for (DotName supplier : List.of(TOOLS_SUPPLIER, TOOL_PROVIDER_SUPPLIER)) {
            String ref = methodRef(annotatedMethod(method.declaringClass(), supplier));
            if (ref != null) {
                sources.add(ref);
            }
        }
        AnnotationInstance mcpToolBox = method.declaredAnnotation(MCP_TOOL_BOX);
        if (mcpToolBox != null) {
            AnnotationValue clients = mcpToolBox.value();
            if (clients == null || clients.asStringArray().length == 0) {
                sources.add("mcp:*"); // every configured MCP client
            } else {
                for (String client : clients.asStringArray()) {
                    sources.add("mcp:" + client);
                }
            }
        }
        return sources;
    }

    /**
     * The prompt template of a {@code @SystemMessage} or {@code @UserMessage}: its value lines joined with its
     * delimiter, or the content of its {@code fromResource} classpath resource.
     */
    private static String template(AnnotationInstance message) {
        if (message == null) {
            return null;
        }
        String resource = nonBlank(stringValue(message, "fromResource"));
        if (resource != null) {
            return readResource(resource);
        }
        AnnotationValue value = message.value();
        if (value == null) {
            return null;
        }
        String delimiter = stringValue(message, "delimiter");
        return nonBlank(String.join(delimiter != null ? delimiter : "\n", value.asStringArray()));
    }

    private static String readResource(String resource) {
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt resource " + resource, e);
        }
    }

    private static boolean isWorkflow(NodeKind kind) {
        return kind != NodeKind.AI_AGENT && kind != NodeKind.NON_AI_AGENT && kind != NodeKind.HUMAN_IN_THE_LOOP
                && kind != NodeKind.A2A_CLIENT_AGENT && kind != NodeKind.MCP_CLIENT_AGENT
                && kind != NodeKind.REGISTRY_AGENT;
    }

    /** Whether another method of the class with that name is an agent method too. */
    private static boolean isOverloaded(MethodInfo method) {
        return method.declaringClass().methods().stream()
                .filter(other -> other.name().equals(method.name()))
                .filter(other -> other.annotations().stream().anyMatch(a -> a.target().kind() == AnnotationTarget.Kind.METHOD
                        && (a.name().equals(AGENT) || WORKFLOW_ANNOTATIONS.containsKey(a.name()))))
                .count() > 1;
    }

    /** {@code name(java.lang.String,int)}: the erased parameter types, as {@code Class.getName()} for non-arrays. */
    private static String signature(MethodInfo method) {
        return method.name() + "(" + String.join(",", method.parameterTypes().stream()
                .map(type -> type.name().toString()).toList()) + ")";
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
            String key = scopeKey(parameter);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * The scope parameters with their JSON schemas, for the run form of a root system.
     */
    private static List<SystemInput> inputs(IndexView index, MethodInfo method) {
        InputSchemaGenerator schemas = new InputSchemaGenerator(index);
        List<SystemInput> inputs = new ArrayList<>();
        for (MethodParameterInfo parameter : method.parameters()) {
            String key = scopeKey(parameter);
            if (key != null) {
                inputs.add(new SystemInput(key, parameter.type().toString(), schemas.schema(parameter.type()),
                        InputSchemaGenerator.required(parameter.type(), parameter)));
            }
        }
        return inputs;
    }

    /**
     * Like langchain4j (AgentUtil.parameterName, AgenticParameterNameResolver): {@code @P(name)}, {@code @V}, the
     * simple name of the {@code @K} typed key (an overridden {@code name()} is only known at runtime), or the
     * parameter name.
     *
     * @return the AgenticScope key of a parameter, null for what langchain4j provides itself (the scope, the memory id,
     *         the loop counter, the invocation parameters, the user message) or a parameter without name
     */
    private static String scopeKey(MethodParameterInfo parameter) {
        if (parameter.type().name().equals(AGENTIC_SCOPE) || parameter.type().name().equals(INVOCATION_PARAMETERS)
                || parameter.hasAnnotation(MEMORY_ID) || parameter.hasAnnotation(LOOP_COUNTER)
                || parameter.hasAnnotation(USER_MESSAGE_PARAMETER)) {
            return null;
        }
        AnnotationInstance p = parameter.annotation(P);
        String pName = p == null ? null : nonBlank(stringValue(p, "name"));
        if (pName != null) {
            return pName;
        }
        AnnotationInstance v = parameter.annotation(V);
        if (v != null) {
            return nonBlank(v.value().asString());
        }
        AnnotationInstance k = parameter.annotation(K);
        if (k != null) {
            return k.value().asClass().name().withoutPackagePrefix().replaceFirst(".*\\$", "");
        }
        return parameter.name();
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

    private static boolean booleanValue(AnnotationInstance annotation, String name) {
        AnnotationValue value = annotation.value(name);
        return value != null && value.asBoolean();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
