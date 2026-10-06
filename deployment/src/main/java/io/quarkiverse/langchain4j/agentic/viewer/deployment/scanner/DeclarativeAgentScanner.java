package io.quarkiverse.langchain4j.agentic.viewer.deployment.scanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentParameter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.CompositionType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.DataFlow;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ToolNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ToolParameter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.WorkflowComposition;

import org.jboss.logging.Logger;

/**
 * Scanner that uses Jandex to detect declarative agent patterns via annotations.
 */
public class DeclarativeAgentScanner {

    private static final Logger LOG = Logger.getLogger(DeclarativeAgentScanner.class);

    // LangChain4j Agentic annotations (dev.langchain4j.agentic package)
    private static final String LANGCHAIN4J_PACKAGE_PREFIX = "dev.langchain4j.";

    private static final DotName AGENT = DotName.createSimple("dev.langchain4j.agentic.Agent");
    private static final DotName SEQUENCE_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.SequenceAgent");
    private static final DotName PARALLEL_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.ParallelAgent");
    private static final DotName CONDITIONAL_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.ConditionalAgent");
    private static final DotName LOOP_AGENT = DotName.createSimple("dev.langchain4j.agentic.declarative.LoopAgent");
    private static final DotName OUTPUT = DotName.createSimple("dev.langchain4j.agentic.declarative.Output");
    private static final DotName A2A_CLIENT_AGENT = DotName.createSimple("dev.langchain4j.agentic.a2a.A2AClientAgent");

    // Test/alternative annotations (io.quarkiverse.langchain4j.agentic package)
    private static final DotName AGENT_ALT = DotName.createSimple("io.quarkiverse.langchain4j.agentic.Agent");
    private static final DotName SEQUENCE_AGENT_ALT = DotName.createSimple("io.quarkiverse.langchain4j.agentic.SequenceAgent");
    private static final DotName PARALLEL_AGENT_ALT = DotName.createSimple("io.quarkiverse.langchain4j.agentic.ParallelAgent");
    private static final DotName CONDITIONAL_AGENT_ALT = DotName.createSimple("io.quarkiverse.langchain4j.agentic.ConditionalAgent");
    private static final DotName LOOP_AGENT_ALT = DotName.createSimple("io.quarkiverse.langchain4j.agentic.LoopAgent");
    private static final DotName OUTPUT_ALT = DotName.createSimple("io.quarkiverse.langchain4j.agentic.Output");

    // LangChain4j core annotations
    private static final DotName REGISTER_AI_SERVICE = DotName.createSimple("io.quarkiverse.langchain4j.RegisterAiService");
    private static final DotName TOOL = DotName.createSimple("dev.langchain4j.agent.tool.Tool");
    private static final DotName TOOL_BOX = DotName.createSimple("io.quarkiverse.langchain4j.ToolBox");
    private static final DotName SYSTEM_MESSAGE = DotName.createSimple("dev.langchain4j.service.SystemMessage");
    private static final DotName USER_MESSAGE = DotName.createSimple("dev.langchain4j.service.UserMessage");
    private static final DotName V = DotName.createSimple("dev.langchain4j.service.V");
    private static final DotName MEMORY_ID = DotName.createSimple("dev.langchain4j.service.MemoryId");
    private static final DotName P = DotName.createSimple("dev.langchain4j.agent.tool.P");

    private static final int MAX_MESSAGE_LENGTH = 100;

    private final IndexView index;
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final Map<String, String> methodToAgentId = new HashMap<>();
    private final Map<String, String> outputKeyToAgentId = new HashMap<>();
    // Map tool class name to tool ID for linking
    private final Map<String, String> toolClassToToolId = new HashMap<>();

    public DeclarativeAgentScanner(IndexView index) {
        this.index = index;
    }

    /**
     * Gets annotations from multiple DotNames (for supporting alternative package names).
     * Annotations declared by the langchain4j libraries themselves are skipped: they are indexed too,
     * but are not part of the application.
     */
    private Collection<AnnotationInstance> getAnnotations(DotName... names) {
        List<AnnotationInstance> result = new ArrayList<>();
        for (DotName name : names) {
            for (AnnotationInstance annotation : index.getAnnotations(name)) {
                if (!isDeclaredByLangChain4j(annotation.target())) {
                    result.add(annotation);
                }
            }
        }
        return result;
    }

    private static boolean isDeclaredByLangChain4j(AnnotationTarget target) {
        ClassInfo declaringClass = switch (target.kind()) {
            case CLASS -> target.asClass();
            case METHOD -> target.asMethod().declaringClass();
            case FIELD -> target.asField().declaringClass();
            case METHOD_PARAMETER -> target.asMethodParameter().method().declaringClass();
            default -> null;
        };
        return declaringClass != null && declaringClass.name().toString().startsWith(LANGCHAIN4J_PACKAGE_PREFIX);
    }

    /**
     * Scans the index for agentic patterns and builds a graph.
     *
     * @return the constructed agentic graph
     */
    public AgenticGraph scan() {
        List<AgentNode> agents = new ArrayList<>();

        // First pass: scan all tools
        List<ToolNode> tools = new ArrayList<>(scanTools());

        // Second pass: scan all agents
        agents.addAll(scanAgents());
        agents.addAll(scanSequenceAgents());
        agents.addAll(scanParallelAgents());
        agents.addAll(scanConditionalAgents());
        agents.addAll(scanLoopAgents());
        agents.addAll(scanAiServices());
        agents.addAll(scanOutputMethods());
        agents.addAll(scanA2AClientAgents());

        // Debug: log all collected mappings before building compositions
        if (LOG.isDebugEnabled()) {
            LOG.debugf("classToAgentId mappings (%d entries): %s", classToAgentId.size(), classToAgentId);
            LOG.debugf("workflowSubAgentClasses mappings (%d entries): %s", workflowSubAgentClasses.size(), workflowSubAgentClasses);
        }

        // Third pass: build compositions and data flows
        List<WorkflowComposition> compositions = new ArrayList<>(buildCompositions(agents));
        List<DataFlow> dataFlows = new ArrayList<>(buildDataFlows(agents));

        if (LOG.isDebugEnabled()) {
            LOG.debugf("Final result: %d compositions, %d dataFlows", compositions.size(), dataFlows.size());
            for (WorkflowComposition comp : compositions) {
                LOG.debugf("  Composition: parent=%s, children=%s, type=%s", comp.parentAgentId(), comp.childAgentIds(), comp.type());
            }
        }

        return new AgenticGraph(agents, tools, dataFlows, compositions, false, List.of(), List.of());
    }

    private List<ToolNode> scanTools() {
        List<ToolNode> tools = new ArrayList<>();
        Collection<AnnotationInstance> toolAnnotations = getAnnotations(TOOL);

        for (AnnotationInstance annotation : toolAnnotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "tool_" + idCounter.incrementAndGet();
            String name = method.name();
            String description = getStringValue(annotation, "value", "");

            List<ToolParameter> parameters = new ArrayList<>();
            for (MethodParameterInfo param : method.parameters()) {
                String paramName = param.name();
                String paramType = param.type().name().local();

                // Check for @P annotation for description
                AnnotationInstance pAnnotation = param.annotation(P);
                if (pAnnotation != null && paramName == null) {
                    paramName = getStringValue(pAnnotation, "value", "param" + param.position());
                }
                if (paramName == null) {
                    paramName = "param" + param.position();
                }

                parameters.add(new ToolParameter(paramName, paramType));
            }

            String returnType = method.returnType().name().local();

            // Map tool class to tool ID for linking
            String toolClassName = classInfo.name().toString();
            toolClassToToolId.put(toolClassName, id);
            // Use putIfAbsent for simple name to avoid collision when multiple tools have same simple name
            toolClassToToolId.putIfAbsent(classInfo.simpleName(), id);
            LOG.debugf("Registered tool class: '%s' -> %s", toolClassName, id);

            tools.add(new ToolNode(
                    id,
                    name,
                    description,
                    toolClassName,
                    method.name(),
                    parameters,
                    returnType));
        }

        return tools;
    }

    private List<AgentNode> scanAgents() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> agentAnnotations = getAnnotations(AGENT, AGENT_ALT);

        for (AnnotationInstance annotation : agentAnnotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "agent_" + idCounter.incrementAndGet();
            String name = method.name();
            // @Agent uses "value" for description
            String description = getStringValue(annotation, "value", "");
            String outputKey = getStringValue(annotation, "outputKey", "");

            // Get system and user messages
            String systemMessage = getMessageFromMethod(method, SYSTEM_MESSAGE);
            String userMessage = getMessageFromMethod(method, USER_MESSAGE);

            // Get tools from @ToolBox
            List<String> toolIds = getToolIdsFromMethod(method);

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register by BOTH full class name AND simple class name for subAgents lookup
            String fullClassName = classInfo.name().toString();
            String simpleClassName = classInfo.simpleName();
            classToAgentId.put(fullClassName, id);
            classToAgentId.putIfAbsent(simpleClassName, id);
            LOG.debugf("Registered agent class: '%s' -> %s", fullClassName, id);
            if (!outputKey.isEmpty()) {
                outputKeyToAgentId.put(outputKey, id);
            }

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.AGENT,
                    description,
                    truncate(systemMessage),
                    truncate(userMessage),
                    outputKey,
                    toolIds,
                    classInfo.name().toString(),
                    method.name(),
                    false,
                    null,
                    inputParameters));
        }

        return agents;
    }

    // Store subAgents (class names) for workflow compositions
    private final Map<String, List<String>> workflowSubAgentClasses = new HashMap<>();
    // Map class name to agent ID (for linking sub-agents)
    private final Map<String, String> classToAgentId = new HashMap<>();

    private List<AgentNode> scanSequenceAgents() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(SEQUENCE_AGENT, SEQUENCE_AGENT_ALT);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "sequence_" + idCounter.incrementAndGet();
            String name = method.name();
            String description = getStringValue(annotation, "description", "");
            String outputKey = getStringValue(annotation, "outputKey", "");

            // Extract subAgents as class array (not string array)
            List<String> subAgentClasses = getClassArrayValue(annotation, "subAgents");
            LOG.debugf("SequenceAgent %s: found %d subAgents: %s", name, subAgentClasses.size(), subAgentClasses);

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register by BOTH full and simple class name for nested workflow lookup
            classToAgentId.put(classInfo.name().toString(), id);
            classToAgentId.put(classInfo.simpleName(), id);
            if (!outputKey.isEmpty()) {
                outputKeyToAgentId.put(outputKey, id);
            }

            // Store subAgent class names for later composition building
            if (!subAgentClasses.isEmpty()) {
                workflowSubAgentClasses.put(id, subAgentClasses);
                LOG.debugf("Stored workflow subAgents for %s: %s", id, subAgentClasses);
            }

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.SEQUENCE,
                    description,
                    null,
                    null,
                    outputKey,
                    List.of(),
                    classInfo.name().toString(),
                    method.name(),
                    false,
                    null,
                    inputParameters));
        }

        return agents;
    }

    private List<AgentNode> scanParallelAgents() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(PARALLEL_AGENT, PARALLEL_AGENT_ALT);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "parallel_" + idCounter.incrementAndGet();
            String name = method.name();
            String description = getStringValue(annotation, "description", "");

            // Extract subAgents as class array
            List<String> subAgentClasses = getClassArrayValue(annotation, "subAgents");

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register by BOTH full and simple class name
            classToAgentId.put(classInfo.name().toString(), id);
            classToAgentId.put(classInfo.simpleName(), id);

            // Store subAgent class names for later composition building
            if (!subAgentClasses.isEmpty()) {
                workflowSubAgentClasses.put(id, subAgentClasses);
            }

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.PARALLEL,
                    description,
                    null,
                    null,
                    null,
                    List.of(),
                    classInfo.name().toString(),
                    method.name(),
                    false,
                    null,
                    inputParameters));
        }

        return agents;
    }

    private List<AgentNode> scanConditionalAgents() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(CONDITIONAL_AGENT, CONDITIONAL_AGENT_ALT);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "conditional_" + idCounter.incrementAndGet();
            String name = method.name();
            String description = getStringValue(annotation, "description", "");

            // Extract subAgents as class array
            List<String> subAgentClasses = getClassArrayValue(annotation, "subAgents");

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register by BOTH full and simple class name
            classToAgentId.put(classInfo.name().toString(), id);
            classToAgentId.put(classInfo.simpleName(), id);

            // Store subAgent class names for later composition building
            if (!subAgentClasses.isEmpty()) {
                workflowSubAgentClasses.put(id, subAgentClasses);
            }

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.CONDITIONAL,
                    description,
                    null,
                    null,
                    null,
                    List.of(),
                    classInfo.name().toString(),
                    method.name(),
                    false,
                    null,
                    inputParameters));
        }

        return agents;
    }

    private List<AgentNode> scanLoopAgents() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(LOOP_AGENT, LOOP_AGENT_ALT);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "loop_" + idCounter.incrementAndGet();
            String name = method.name();
            String description = getStringValue(annotation, "description", "");

            // Extract subAgents as class array
            List<String> subAgentClasses = getClassArrayValue(annotation, "subAgents");

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register by BOTH full and simple class name
            classToAgentId.put(classInfo.name().toString(), id);
            classToAgentId.put(classInfo.simpleName(), id);

            // Store subAgent class names for later composition building
            if (!subAgentClasses.isEmpty()) {
                workflowSubAgentClasses.put(id, subAgentClasses);
            }

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.LOOP,
                    description,
                    null,
                    null,
                    null,
                    List.of(),
                    classInfo.name().toString(),
                    method.name(),
                    false,
                    null,
                    inputParameters));
        }

        return agents;
    }

    private List<AgentNode> scanAiServices() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(REGISTER_AI_SERVICE);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            ClassInfo classInfo = annotation.target().asClass();
            String fullClassName = classInfo.name().toString();
            String simpleClassName = classInfo.simpleName();

            // Skip if this class already has an agent registered (e.g., from @Agent methods)
            if (classToAgentId.containsKey(fullClassName) || classToAgentId.containsKey(simpleClassName)) {
                LOG.debugf("Skipping AI service %s - already has agent registered", simpleClassName);
                continue;
            }

            String id = "aiservice_" + idCounter.incrementAndGet();
            String name = simpleClassName;

            // Get tools from annotation and link to tool IDs
            List<String> toolIds = new ArrayList<>();
            AnnotationValue toolsValue = annotation.value("tools");
            if (toolsValue != null) {
                try {
                    for (Type toolType : toolsValue.asClassArray()) {
                        String toolClassName = toolType.name().toString();
                        String toolId = toolClassToToolId.get(toolClassName);
                        if (toolId == null) {
                            // Try simple name
                            String simpleName = toolClassName.contains(".")
                                ? toolClassName.substring(toolClassName.lastIndexOf('.') + 1)
                                : toolClassName;
                            toolId = toolClassToToolId.get(simpleName);
                        }
                        if (toolId != null) {
                            toolIds.add(toolId);
                            LOG.debugf("Linked AI service %s to tool %s", name, toolId);
                        }
                    }
                } catch (Exception e) {
                    LOG.warnf("Could not parse tools for AI service %s: %s", name, e.getMessage());
                }
            }

            // Get system message from class-level annotation
            String systemMessage = null;
            AnnotationInstance sysMsg = classInfo.declaredAnnotation(SYSTEM_MESSAGE);
            if (sysMsg != null) {
                systemMessage = getStringValue(sysMsg, "value", "");
            }

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.AI_SERVICE,
                    "AI Service",
                    truncate(systemMessage),
                    null,
                    null,
                    toolIds,
                    classInfo.name().toString(),
                    null,
                    false,
                    null,
                    List.of())); // AI services don't have method-level parameters
        }

        return agents;
    }

    private List<AgentNode> scanOutputMethods() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(OUTPUT, OUTPUT_ALT);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "output_" + idCounter.incrementAndGet();
            String name = method.name();

            // Collect @V bindings from parameters
            List<String> inputVariables = new ArrayList<>();
            for (MethodParameterInfo param : method.parameters()) {
                AnnotationInstance vAnnotation = param.annotation(V);
                if (vAnnotation != null) {
                    inputVariables.add(getStringValue(vAnnotation, "value", ""));
                }
            }

            String description = "Output: combines " + String.join(", ", inputVariables);

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register for sub-agent lookup
            classToAgentId.put(classInfo.name().toString(), id);
            classToAgentId.putIfAbsent(classInfo.simpleName(), id);

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.OUTPUT,
                    description,
                    null,
                    null,
                    null,
                    List.of(),
                    classInfo.name().toString(),
                    method.name(),
                    false,
                    null,
                    inputParameters));
        }

        return agents;
    }

    private List<AgentNode> scanA2AClientAgents() {
        List<AgentNode> agents = new ArrayList<>();
        Collection<AnnotationInstance> annotations = getAnnotations(A2A_CLIENT_AGENT);

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            ClassInfo classInfo = method.declaringClass();

            String id = "a2a_" + idCounter.incrementAndGet();
            String name = method.name();
            String description = getStringValue(annotation, "description", "Remote A2A Agent");
            String outputKey = getStringValue(annotation, "outputKey", "");
            String serverUrl = getStringValue(annotation, "serverUrl", "");

            methodToAgentId.put(classInfo.name().toString() + "." + method.name(), id);
            // Register for sub-agent lookup
            classToAgentId.put(classInfo.name().toString(), id);
            classToAgentId.putIfAbsent(classInfo.simpleName(), id);
            if (!outputKey.isEmpty()) {
                outputKeyToAgentId.put(outputKey, id);
            }

            // Get input parameters
            List<AgentParameter> inputParameters = extractInputParameters(method);

            agents.add(new AgentNode(
                    id,
                    name,
                    AgentType.A2A,
                    description,
                    null,
                    null,
                    outputKey,
                    List.of(),
                    classInfo.name().toString(),
                    method.name(),
                    true,
                    serverUrl,
                    inputParameters));
        }

        return agents;
    }

    private List<WorkflowComposition> buildCompositions(List<AgentNode> agents) {
        List<WorkflowComposition> compositions = new ArrayList<>();

        // For each workflow agent (SEQUENCE, PARALLEL, CONDITIONAL, LOOP),
        // use the subAgents extracted from the annotation
        for (AgentNode agent : agents) {
            if (agent.type() == AgentType.SEQUENCE ||
                    agent.type() == AgentType.PARALLEL ||
                    agent.type() == AgentType.CONDITIONAL ||
                    agent.type() == AgentType.LOOP) {

                List<String> childIds = findChildAgents(agent);
                if (!childIds.isEmpty()) {
                    CompositionType compType = switch (agent.type()) {
                        case SEQUENCE -> CompositionType.SEQUENCE;
                        case PARALLEL -> CompositionType.PARALLEL;
                        case CONDITIONAL -> CompositionType.CONDITIONAL;
                        case LOOP -> CompositionType.LOOP;
                        default -> CompositionType.SEQUENCE;
                    };
                    compositions.add(new WorkflowComposition(agent.id(), childIds, compType));
                }
            }
        }

        return compositions;
    }

    private List<String> findChildAgents(AgentNode workflowAgent) {
        // Use the subAgent class names stored from annotation parsing
        List<String> subAgentClassNames = workflowSubAgentClasses.get(workflowAgent.id());

        if (subAgentClassNames == null || subAgentClassNames.isEmpty()) {
            return List.of();
        }

        // Convert class names to agent IDs
        List<String> childIds = new ArrayList<>();
        for (String className : subAgentClassNames) {
            String agentId = classToAgentId.get(className);
            if (agentId != null) {
                childIds.add(agentId);
            } else {
                LOG.debugf("Could not find agent ID for sub-agent class: %s", className);
            }
        }

        LOG.debugf("findChildAgents for %s: %d children found", workflowAgent.id(), childIds.size());
        return childIds;
    }

    private List<DataFlow> buildDataFlows(List<AgentNode> agents) {
        List<DataFlow> dataFlows = new ArrayList<>();
        Set<String> seenFlows = new HashSet<>();

        // For each agent, check if it has @V bindings that reference outputKeys
        for (AgentNode agent : agents) {
            String className = agent.className();
            String methodName = agent.methodName();

            if (methodName == null) {
                continue;
            }

            ClassInfo classInfo = index.getClassByName(DotName.createSimple(className));
            if (classInfo == null) {
                continue;
            }

            MethodInfo method = findMethod(classInfo, methodName);
            if (method == null) {
                continue;
            }

            // Check parameters for @V annotations
            for (MethodParameterInfo param : method.parameters()) {
                AnnotationInstance vAnnotation = param.annotation(V);
                if (vAnnotation != null) {
                    String variableName = getStringValue(vAnnotation, "value", "");
                    String sourceAgentId = outputKeyToAgentId.get(variableName);

                    if (sourceAgentId != null) {
                        String flowKey = sourceAgentId + "->" + agent.id() + ":" + variableName;
                        if (!seenFlows.contains(flowKey)) {
                            dataFlows.add(new DataFlow(sourceAgentId, agent.id(), variableName, variableName));
                            seenFlows.add(flowKey);
                        }
                    }
                }
            }
        }

        return dataFlows;
    }

    private MethodInfo findMethod(ClassInfo classInfo, String methodName) {
        for (MethodInfo method : classInfo.methods()) {
            if (method.name().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private String getMessageFromMethod(MethodInfo method, DotName annotationName) {
        AnnotationInstance annotation = method.annotation(annotationName);
        if (annotation != null) {
            return getStringValue(annotation, "value", "");
        }
        return null;
    }

    private List<String> getToolIdsFromMethod(MethodInfo method) {
        List<String> toolIds = new ArrayList<>();

        // Check for @ToolBox annotation on the method
        AnnotationInstance toolBoxAnnotation = method.annotation(TOOL_BOX);
        if (toolBoxAnnotation != null) {
            AnnotationValue value = toolBoxAnnotation.value();
            if (value != null) {
                try {
                    for (Type toolType : value.asClassArray()) {
                        String toolClassName = toolType.name().toString();
                        String toolId = toolClassToToolId.get(toolClassName);
                        if (toolId == null) {
                            String simpleName = toolClassName.contains(".")
                                ? toolClassName.substring(toolClassName.lastIndexOf('.') + 1)
                                : toolClassName;
                            toolId = toolClassToToolId.get(simpleName);
                        }
                        if (toolId != null) {
                            toolIds.add(toolId);
                            LOG.debugf("Linked method %s to tool %s via @ToolBox", method.name(), toolId);
                        }
                    }
                } catch (Exception e) {
                    LOG.warnf("Could not parse @ToolBox for method %s: %s", method.name(), e.getMessage());
                }
            }
        }

        // Also check declaring class for @ToolBox
        ClassInfo classInfo = method.declaringClass();
        AnnotationInstance classToolBox = classInfo.declaredAnnotation(TOOL_BOX);
        if (classToolBox != null) {
            AnnotationValue value = classToolBox.value();
            if (value != null) {
                try {
                    for (Type toolType : value.asClassArray()) {
                        String toolClassName = toolType.name().toString();
                        String toolId = toolClassToToolId.get(toolClassName);
                        if (toolId == null) {
                            String simpleName = toolClassName.contains(".")
                                ? toolClassName.substring(toolClassName.lastIndexOf('.') + 1)
                                : toolClassName;
                            toolId = toolClassToToolId.get(simpleName);
                        }
                        if (toolId != null && !toolIds.contains(toolId)) {
                            toolIds.add(toolId);
                            LOG.debugf("Linked class %s to tool %s via @ToolBox", classInfo.simpleName(), toolId);
                        }
                    }
                } catch (Exception e) {
                    LOG.warnf("Could not parse @ToolBox for class %s: %s", classInfo.simpleName(), e.getMessage());
                }
            }
        }

        return toolIds;
    }

    private String getStringValue(AnnotationInstance annotation, String key, String defaultValue) {
        AnnotationValue value = annotation.value(key);
        if (value != null) {
            return value.asString();
        }
        // Try default value (no key)
        if ("value".equals(key)) {
            value = annotation.value();
            if (value != null) {
                return value.asString();
            }
        }
        return defaultValue;
    }

    private List<String> getClassArrayValue(AnnotationInstance annotation, String key) {
        AnnotationValue value = annotation.value(key);
        if (value == null) {
            LOG.debugf("getClassArrayValue: no value for key '%s' in annotation %s", key, annotation);
            return List.of();
        }

        List<String> classNames = new ArrayList<>();

        // Try asClassArray first (for direct Class[] arrays)
        try {
            Type[] classArray = value.asClassArray();
            if (classArray != null && classArray.length > 0) {
                for (Type type : classArray) {
                    classNames.add(type.name().toString());
                }
                LOG.debugf("getClassArrayValue via asClassArray: %s", classNames);
                return classNames;
            }
        } catch (IllegalArgumentException e) {
            // Expected when value is not a class array - try nested annotations
            LOG.debugf("asClassArray not applicable for key '%s', trying nested annotation parsing", key);
        }

        // Try as nested annotation array (for @SubAgent[] arrays)
        try {
            AnnotationInstance[] nestedAnnotations = value.asNestedArray();
            if (nestedAnnotations != null && nestedAnnotations.length > 0) {
                for (AnnotationInstance nested : nestedAnnotations) {
                    // Extract the 'type' attribute from @SubAgent annotation
                    AnnotationValue typeValue = nested.value("type");
                    if (typeValue == null) {
                        // Try default value
                        typeValue = nested.value();
                    }
                    if (typeValue != null && typeValue.kind() == AnnotationValue.Kind.CLASS) {
                        String className = typeValue.asClass().name().toString();
                        classNames.add(className);
                    }
                }
                if (!classNames.isEmpty()) {
                    LOG.debugf("getClassArrayValue via nested annotations: %s", classNames);
                    return classNames;
                }
            }
        } catch (IllegalArgumentException e) {
            LOG.debugf("asNestedArray not applicable for key '%s'", key);
        }

        if (classNames.isEmpty()) {
            LOG.debugf("getClassArrayValue: could not extract classes for key '%s'", key);
        }

        return classNames;
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    /**
     * Extracts input parameters from a method, including @V and @MemoryId annotations.
     */
    private List<AgentParameter> extractInputParameters(MethodInfo method) {
        List<AgentParameter> parameters = new ArrayList<>();

        for (MethodParameterInfo param : method.parameters()) {
            String paramName = param.name();
            String paramType = getSimpleTypeName(param.type());

            // Check for @V annotation
            AnnotationInstance vAnnotation = param.annotation(V);
            if (vAnnotation != null) {
                String vName = getStringValue(vAnnotation, "value", paramName);
                paramName = vName;
            }

            // Check for @MemoryId annotation
            boolean isMemoryId = param.annotation(MEMORY_ID) != null;

            // Skip common framework parameters that aren't meaningful inputs
            if (!isMemoryId && !"AgenticScope".equals(paramType)) {
                parameters.add(new AgentParameter(paramName, paramType, isMemoryId));
            } else if (isMemoryId) {
                parameters.add(new AgentParameter(paramName, paramType, true));
            }
        }

        return parameters;
    }

    /**
     * Gets a simple type name from a Jandex Type.
     */
    private String getSimpleTypeName(Type type) {
        if (type == null) {
            return "?";
        }
        String name = type.name().toString();
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return name;
    }
}
