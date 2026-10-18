package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AiAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeIds;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.RemoteAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.ToolInfo;

/**
 * The topology of a system built at runtime (T5.2): the same model as the build-time TopologyBuilder, read from the
 * live agent tree through the public {@link AgentInstance} API (finding doc-5), and from the annotations of the agent
 * interfaces for the AI agent details.
 * <p>
 * Node ids follow the rule run events are correlated with (DevUiAgentListener): the root is the system id, each level
 * adds the simple name of the sub-agent's type, with {@code ~n} for the n-th sibling of the same type. What the tree
 * cannot tell: lambda guards (labelled "lambda"), tools and models given to a builder directly, supervisor settings,
 * the items key of a parallel mapper (its input keys are those of its agent).
 */
public final class BuilderTopology {

    /** A guard given as a lambda, without description. */
    public static final String LAMBDA = "lambda";

    private static final String UNKNOWN_CONDITION = "<unknown>";
    private static final String LIBRARY = "dev.langchain4j.";
    private static final String V = "dev.langchain4j.service.V";
    private static final String DECLARATIVE = "dev.langchain4j.agentic.declarative.";
    private static final String AGENT = "dev.langchain4j.agentic.Agent";
    private static final String HUMAN_IN_THE_LOOP = DECLARATIVE + "HumanInTheLoop";
    private static final String A2A_CLIENT_AGENT = DECLARATIVE + "A2AClientAgent";
    private static final String A2A_SERVER_URL_SUPPLIER = DECLARATIVE + "A2AServerUrlSupplier";
    private static final String MCP_CLIENT_AGENT = DECLARATIVE + "McpClientAgent";
    private static final String MCP_CLIENT_SUPPLIER = DECLARATIVE + "McpClientSupplier";
    private static final String TOOLS_SUPPLIER = DECLARATIVE + "ToolsSupplier";
    private static final String TOOL_PROVIDER_SUPPLIER = DECLARATIVE + "ToolProviderSupplier";
    private static final Set<String> DECLARATIVE_WORKFLOWS = Set.of(DECLARATIVE + "SequenceAgent",
            DECLARATIVE + "ParallelAgent", DECLARATIVE + "ParallelMapperAgent", DECLARATIVE + "LoopAgent",
            DECLARATIVE + "ConditionalAgent", DECLARATIVE + "SupervisorAgent", DECLARATIVE + "PlannerAgent");
    private static final String SYSTEM_MESSAGE = "dev.langchain4j.service.SystemMessage";
    private static final String USER_MESSAGE = "dev.langchain4j.service.UserMessage";
    private static final String TOOL = "dev.langchain4j.agent.tool.Tool";
    private static final String MODEL_NAME = "io.quarkiverse.langchain4j.ModelName";
    private static final String TOOL_BOX = "io.quarkiverse.langchain4j.ToolBox";
    private static final String MCP_TOOL_BOX = "io.quarkiverse.langchain4j.mcp.runtime.McpToolBox";

    /** Planners of the workflow kinds, by class name (internal classes); any other planner is a PLANNER node. */
    private static final Map<String, NodeKind> WORKFLOW_PLANNERS = Map.of(
            LangChain4jInternals.SEQUENTIAL_PLANNER, NodeKind.SEQUENCE,
            LangChain4jInternals.PARALLEL_PLANNER, NodeKind.PARALLEL,
            LangChain4jInternals.PARALLEL_MAPPER_PLANNER, NodeKind.PARALLEL_MAPPER,
            LangChain4jInternals.LOOP_PLANNER, NodeKind.LOOP,
            LangChain4jInternals.CONDITIONAL_PLANNER, NodeKind.CONDITIONAL,
            LangChain4jInternals.SUPERVISOR_PLANNER, NodeKind.SUPERVISOR);
    /** Same as the build-time PlannerTypeDetector: the planners of langchain4j-agentic-patterns (or subclasses). */
    private static final Map<String, PatternType> PATTERN_PLANNERS = Map.of(
            "dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner", PatternType.GOAP,
            "dev.langchain4j.agentic.patterns.p2p.P2PPlanner", PatternType.P2P,
            "dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner", PatternType.BLACKBOARD);

    private BuilderTopology() {
    }

    /**
     * @param id system id, also the root node id
     * @param name system name shown in the list
     * @param sourceRef where the system is defined (e.g. its producer method)
     * @param root the root agent, unwrapped from any CDI client proxy
     */
    public static AgenticSystem system(String id, String name, SourceRef sourceRef, AgentInstance root) {
        return system(id, name, sourceRef, root, agentClass -> null);
    }

    /**
     * @param declarativeNodes the node of an agent class in a declarative system of the application, null for none:
     *        such an agent takes its build-time details (T5.5b) and makes the system MIXED
     */
    public static AgenticSystem system(String id, String name, SourceRef sourceRef, AgentInstance root,
            Function<String, AgentNode> declarativeNodes) {
        boolean[] shared = { false };
        AgentNode rootNode = node(id, root, null, agentClass -> {
            AgentNode node = declarativeNodes.apply(agentClass);
            shared[0] |= node != null;
            return node;
        });
        SystemOrigin origin = shared[0] || usesDeclarativeWorkflow(root) ? SystemOrigin.MIXED : SystemOrigin.BUILDER;
        rootNode = withCallSite(rootNode, sourceRef);
        return new AgenticSystem(id, name, id, origin, sourceRef, inputs(root, rootNode), rootNode);
    }

    /** Whether a workflow under the root is a declarative one. */
    private static boolean usesDeclarativeWorkflow(AgentInstance root) {
        return root.subagents().stream().anyMatch(sub -> declarativeWorkflow(sub.type()) || usesDeclarativeWorkflow(sub));
    }

    private static boolean declarativeWorkflow(Class<?> type) {
        return type != null && agentMethods(type).stream()
                .anyMatch(method -> annotations(method).stream().anyMatch(a -> DECLARATIVE_WORKFLOWS.contains(a.annotationType().getName())));
    }

    static AgentNode node(String id, AgentInstance agent, Guard guard, Function<String, AgentNode> declarativeNodes) {
        NodeKind kind = kind(agent);
        List<AgentNode> children = new ArrayList<>();
        Map<String, Guard> branchGuards = branchGuards(agent);
        Map<String, Integer> occurrences = new HashMap<>();
        for (AgentInstance sub : agent.subagents()) {
            String segment = simpleName(sub.type());
            int occurrence = occurrences.merge(segment, 1, Integer::sum);
            children.add(node(NodeIds.child(id, segment, occurrence), sub,
                    branchGuards.get(sub.agentId()), declarativeNodes));
        }
        // an agent of a declarative system: its build-time details (prompt resources, tools of superclasses...)
        AgentNode declared = agent.parent() == null || agent.type() == null ? null : declarativeNodes.apply(agent.type().getName());
        return new AgentNode(
                id,
                agent.name(),
                kind,
                agent.description(),
                inputKeys(agent, kind, children),
                agent.outputKey(),
                agent.optional(),
                agent.async(),
                children,
                kind == NodeKind.LOOP ? withActivation(loopGuard(agent), guard) : guard,
                kind == NodeKind.PLANNER ? plannerInfo(agent.plannerType()) : null,
                kind != NodeKind.AI_AGENT ? null : declared != null && declared.aiDetails() != null ? declared.aiDetails()
                        : aiDetails(agent.type()),
                declared != null && declared.remoteDetails() != null ? declared.remoteDetails() : remoteDetails(kind, agent.type()),
                declared != null && !declared.code().isEmpty() ? declared.code() : agentCode(agent.type()));
    }

    /**
     * The application class of an agent (its agent method when it declares one), none for the library's own types (an
     * UntypedAgent, an agentAction).
     */
    private static List<CodeRef> agentCode(Class<?> type) {
        if (type == null || type.getName().startsWith(LIBRARY)) {
            return List.of();
        }
        String member = agentMethods(type).stream()
                .filter(method -> annotations(method).stream().anyMatch(a -> a.annotationType().getName().equals(AGENT)
                        || a.annotationType().getName().startsWith(DECLARATIVE)))
                .map(Method::getName).findFirst()
                .orElseGet(() -> type.isInterface() && agentMethods(type).size() == 1 ? agentMethods(type).get(0).getName() : null);
        return List.of(CodeRef.method(CodeRef.Role.AGENT, type.getName(), member));
    }

    /** Where the system is built: the call site the transform reported ({@code Class#method:line}), else the producer. */
    private static AgentNode withCallSite(AgentNode root, SourceRef sourceRef) {
        if (sourceRef == null || sourceRef.className() == null) {
            return root;
        }
        Integer line = null;
        String callSite = sourceRef.callSite();
        if (callSite != null && callSite.lastIndexOf(':') > 0) {
            try {
                line = Integer.valueOf(callSite.substring(callSite.lastIndexOf(':') + 1));
            } catch (NumberFormatException e) {
                // no line
            }
        }
        List<CodeRef> code = new ArrayList<>();
        code.add(new CodeRef(CodeRef.Role.CALL_SITE, sourceRef.className(), sourceRef.methodName(), null, line));
        code.addAll(root.code());
        return new AgentNode(root.id(), root.name(), root.kind(), root.description(), root.inputKeys(), root.outputKey(),
                root.optional(), root.async(), root.children(), root.guard(), root.plannerInfo(), root.aiDetails(),
                root.remoteDetails(), code);
    }

    /**
     * The planner decides for workflows: supervisors and custom planners report a SEQUENCE topology. A
     * human-in-the-loop step is HUMAN_IN_THE_LOOP, built with the builder (its topology) or declared (its interface
     * carries the annotation).
     */
    static NodeKind kind(AgentInstance agent) {
        Class<?> planner = agent.plannerType();
        if (planner != null) {
            return WORKFLOW_PLANNERS.getOrDefault(planner.getName(), NodeKind.PLANNER);
        }
        if (agent.topology() == AgenticSystemTopology.AI_AGENT) {
            return NodeKind.AI_AGENT;
        }
        if (agent.topology() == AgenticSystemTopology.HUMAN_IN_THE_LOOP || hasAgentMethod(agent.type(), HUMAN_IN_THE_LOOP)) {
            return NodeKind.HUMAN_IN_THE_LOOP;
        }
        if (hasAgentMethod(agent.type(), A2A_CLIENT_AGENT)) {
            return NodeKind.A2A_CLIENT_AGENT;
        }
        if (hasAgentMethod(agent.type(), MCP_CLIENT_AGENT)) {
            return NodeKind.MCP_CLIENT_AGENT;
        }
        return NodeKind.NON_AI_AGENT;
    }

    /**
     * Declared arguments; a builder workflow declares none: the keys its agents read before any of them writes them,
     * in order for sequences and loops; for the others, whose agents run in any order (planners, supervisors) or apart
     * (parallel, conditional branches), the keys no agent writes. A condition's keys are unknown.
     */
    private static List<String> inputKeys(AgentInstance agent, NodeKind kind, List<AgentNode> children) {
        List<AgentArgument> declared = declaredArguments(agent);
        if (!declared.isEmpty() || children.isEmpty()) {
            return declared.stream().map(AgentArgument::name).toList();
        }
        Set<String> inputs = new LinkedHashSet<>();
        Set<String> written = new LinkedHashSet<>();
        if (kind != NodeKind.SEQUENCE && kind != NodeKind.LOOP) {
            children.stream().map(AgentNode::outputKey).filter(key -> key != null).forEach(written::add);
        }
        for (AgentNode child : children) {
            child.inputKeys().stream().filter(key -> !written.contains(key)).forEach(inputs::add);
            if (child.outputKey() != null) {
                written.add(child.outputKey());
            }
        }
        return List.copyOf(inputs);
    }

    /** The root's inputs, with the type of the first agent reading each and its form schema (T5.6). */
    private static List<SystemInput> inputs(AgentInstance root, AgentNode rootNode) {
        Map<String, AgentArgument> arguments = new LinkedHashMap<>();
        collectArguments(root, arguments);
        return rootNode.inputKeys().stream().map(key -> {
            AgentArgument argument = arguments.get(key);
            Type type = argument == null ? Object.class : argument.type();
            boolean required = argument == null || !argument.isOptional() && RuntimeInputSchema.required(type, null);
            return new SystemInput(key, type.getTypeName(), RuntimeInputSchema.schema(type), required);
        }).toList();
    }

    private static void collectArguments(AgentInstance agent, Map<String, AgentArgument> arguments) {
        declaredArguments(agent).forEach(argument -> arguments.putIfAbsent(argument.name(), argument));
        agent.subagents().forEach(sub -> collectArguments(sub, arguments));
    }

    /**
     * The agent's arguments; a typed agent interface given to a builder carries no agentic annotation, so langchain4j
     * declares none: the parameters of its one method then (what its callers provide, by {@code @V} name).
     */
    private static List<AgentArgument> declaredArguments(AgentInstance agent) {
        // the scope given to an agent (e.g. an agentAction) is no input, as for the build-time scanner
        List<AgentArgument> declared = agent.arguments().stream()
                .filter(argument -> !AgenticScope.class.isAssignableFrom(argument.rawType())).toList();
        Class<?> type = agent.type();
        if (!agent.arguments().isEmpty() || type == null || !type.isInterface() || type.getName().startsWith(LIBRARY)) {
            return declared;
        }
        List<Method> methods = Arrays.stream(type.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()) && method.getDeclaringClass() == type).toList();
        if (methods.size() != 1) {
            return declared;
        }
        List<AgentArgument> arguments = new ArrayList<>();
        for (Parameter parameter : methods.get(0).getParameters()) {
            if (!AgenticScope.class.isAssignableFrom(parameter.getType())) {
                Annotation v = annotation(parameter, V);
                String name = v != null ? (String) value(v, "value") : parameter.getName();
                // optional when @Nullable or Optional, as a declarative input (the parameter's annotations tell)
                arguments.add(new AgentArgument(parameter.getParameterizedType(), name, null,
                        !RuntimeInputSchema.required(parameter.getParameterizedType(), parameter)));
            }
        }
        return arguments;
    }

    private static Guard loopGuard(AgentInstance agent) {
        LoopAgentInstance loop = as(agent, LoopAgentInstance.class);
        return loop == null ? null : Guard.loopExit(label(loop.exitCondition()), loop.maxIterations(), loop.testExitAtLoopEnd());
    }

    /** The exit condition of a loop, with the activation condition of the branch it is, if any. */
    private static Guard withActivation(Guard loopGuard, Guard branch) {
        return loopGuard == null ? branch : loopGuard.withActivation(branch);
    }

    /** Activation guard by sub-agent id. */
    private static Map<String, Guard> branchGuards(AgentInstance agent) {
        ConditionalAgentInstance conditional = as(agent, ConditionalAgentInstance.class);
        Map<String, Guard> guards = new HashMap<>();
        if (conditional != null) {
            conditional.conditionalSubagents().forEach(branch -> branch.agentInstances()
                    .forEach(sub -> guards.putIfAbsent(sub.agentId(), Guard.activation(label(branch.condition())))));
        }
        return guards;
    }

    private static String label(String condition) {
        return condition == null || condition.isBlank() || condition.equals(UNKNOWN_CONDITION) ? LAMBDA : condition;
    }

    /** Loop and conditional details: the agents are no instances of these interfaces, as() reaches them. */
    private static <T extends AgentInstance> T as(AgentInstance agent, Class<T> type) {
        try {
            return agent.as(type);
        } catch (ClassCastException e) {
            return null;
        }
    }

    private static PlannerInfo plannerInfo(Class<?> planner) {
        for (Class<?> type = planner; type != null && type != Object.class; type = type.getSuperclass()) {
            PatternType pattern = PATTERN_PLANNERS.get(type.getName());
            if (pattern != null) {
                return new PlannerInfo(planner.getName(), pattern, Map.of());
            }
        }
        return new PlannerInfo(planner.getName(), PatternType.CUSTOM, Map.of());
    }

    /**
     * Same sources as the build-time scanner: the annotations of the agent method and interface. Tools and models
     * given to a builder directly are not visible.
     */
    private static AiAgentDetails aiDetails(Class<?> type) {
        Method method = agentMethod(type, AGENT);
        if (method == null) {
            return null;
        }
        Annotation systemMessage = annotation(method, SYSTEM_MESSAGE);
        if (systemMessage == null) {
            systemMessage = annotation(type, SYSTEM_MESSAGE);
        }
        Annotation modelName = annotation(method, MODEL_NAME);
        return new AiAgentDetails(template(type, systemMessage), template(type, annotation(method, USER_MESSAGE)),
                modelName == null ? null : nonBlank((String) value(modelName, "value")), tools(method),
                runtimeToolSources(type, method));
    }

    private static List<ToolInfo> tools(Method method) {
        List<ToolInfo> tools = new ArrayList<>();
        Annotation toolBox = annotation(method, TOOL_BOX);
        if (toolBox == null) {
            return tools;
        }
        for (Class<?> toolClass : (Class<?>[]) value(toolBox, "value")) {
            for (Class<?> type = toolClass; type != null && type != Object.class; type = type.getSuperclass()) {
                for (Method toolMethod : type.getDeclaredMethods()) {
                    Annotation tool = annotation(toolMethod, TOOL);
                    if (tool != null) {
                        String name = nonBlank((String) value(tool, "name"));
                        String[] description = (String[]) value(tool, "value");
                        tools.add(new ToolInfo(name != null ? name : toolMethod.getName(), type.getName(),
                                toolMethod.getName(),
                                description == null ? null : nonBlank(String.join("\n", description))));
                    }
                }
            }
        }
        return tools;
    }

    private static List<String> runtimeToolSources(Class<?> type, Method method) {
        List<String> sources = new ArrayList<>();
        for (String supplier : List.of(TOOLS_SUPPLIER, TOOL_PROVIDER_SUPPLIER)) {
            Method supplierMethod = agentMethod(type, supplier);
            if (supplierMethod != null) {
                sources.add(type.getName() + "#" + supplierMethod.getName());
            }
        }
        Annotation mcpToolBox = annotation(method, MCP_TOOL_BOX);
        if (mcpToolBox != null) {
            String[] clients = (String[]) value(mcpToolBox, "value");
            if (clients == null || clients.length == 0) {
                sources.add("mcp:*"); // every configured MCP client
            } else {
                for (String client : clients) {
                    sources.add("mcp:" + client);
                }
            }
        }
        return sources;
    }

    private static RemoteAgentDetails remoteDetails(NodeKind kind, Class<?> type) {
        if (kind == NodeKind.A2A_CLIENT_AGENT) {
            Annotation agent = annotation(agentMethod(type, A2A_CLIENT_AGENT), A2A_CLIENT_AGENT);
            return new RemoteAgentDetails(nonBlank((String) value(agent, "a2aServerUrl")),
                    methodRef(type, agentMethod(type, A2A_SERVER_URL_SUPPLIER)), nonBlank((String) value(agent, "tenant")),
                    null, null);
        }
        if (kind == NodeKind.MCP_CLIENT_AGENT) {
            Annotation agent = annotation(agentMethod(type, MCP_CLIENT_AGENT), MCP_CLIENT_AGENT);
            return new RemoteAgentDetails(null, null, null, nonBlank((String) value(agent, "toolName")),
                    methodRef(type, agentMethod(type, MCP_CLIENT_SUPPLIER)));
        }
        return null;
    }

    /** The prompt template: the value lines joined with the delimiter, or the content of the fromResource resource. */
    private static String template(Class<?> type, Annotation message) {
        if (message == null) {
            return null;
        }
        String resource = nonBlank((String) value(message, "fromResource"));
        if (resource != null) {
            String path = resource.startsWith("/") ? resource.substring(1) : resource;
            try (InputStream in = type.getClassLoader().getResourceAsStream(path)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }
        String[] lines = (String[]) value(message, "value");
        String delimiter = (String) value(message, "delimiter");
        return lines == null ? null : nonBlank(String.join(delimiter != null ? delimiter : "\n", lines));
    }

    private static boolean hasAgentMethod(Class<?> type, String annotation) {
        return agentMethod(type, annotation) != null;
    }

    /** The first method of the type (or its interfaces) carrying the annotation, by name: some are optional. */
    private static Method agentMethod(Class<?> type, String annotation) {
        if (type == null) {
            return null;
        }
        for (Method method : agentMethods(type)) {
            if (annotation(method, annotation) != null) {
                return method;
            }
        }
        return null;
    }

    private static List<Method> agentMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>(List.of(type.getDeclaredMethods()));
        for (Class<?> parent : type.getInterfaces()) {
            methods.addAll(List.of(parent.getDeclaredMethods()));
        }
        return methods;
    }

    private static List<Annotation> annotations(AnnotatedElement element) {
        return List.of(element.getAnnotations());
    }

    private static Annotation annotation(AnnotatedElement element, String name) {
        if (element == null) {
            return null;
        }
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getName().equals(name)) {
                return annotation;
            }
        }
        return null;
    }

    private static Object value(Annotation annotation, String attribute) {
        if (annotation == null) {
            return null;
        }
        try {
            return annotation.annotationType().getMethod(attribute).invoke(annotation);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static String methodRef(Class<?> type, Method method) {
        return method == null ? null : type.getName() + "#" + method.getName();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static String simpleName(Class<?> type) {
        return NodeIds.simpleName(type);
    }

    static String simpleName(String className) {
        return NodeIds.simpleName(className);
    }
}
