package io.quarkiverse.langchain4j.agentic.viewer.it.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderStoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderStoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.DraftReviewer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.PhraseTranslator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResearchGatherer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.TripPlanner;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T0.5 spike (TASK-10): what the live agent tree tells through the public {@link AgentInstance} API only, for builder
 * and declarative systems. Prints each tree (to the output and target/agent-trees.txt) and checks what the finding
 * relies on.
 */
@QuarkusTest
class AgentTreeSpikeTest {

    private static final Map<String, String> TREES = new LinkedHashMap<>();

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    @Inject
    StoryCreator storyCreator;

    @Inject
    BuilderStoryWriter builderStoryWriter;

    @Inject
    ExpertRouter expertRouter;




    @Inject
    ResearchGatherer researchGatherer;

    @Inject
    PhraseTranslator phraseTranslator;

    @Inject
    BankSupervisor bankSupervisor;

    @Inject
    DraftReviewer draftReviewer;

    @Inject
    TripPlanner tripPlanner;





    @Inject
    ChatModel chatModel;

    @AfterAll
    static void write() throws IOException {
        String all = TREES.entrySet().stream().map(e -> "== " + e.getKey() + "\n" + e.getValue())
                .collect(Collectors.joining("\n"));
        System.out.println(all);
        Files.writeString(Path.of("target", "agent-trees.txt"), all);
    }

    /** A new agent: the app's agent beans belong to its declarative systems (an agent has one parent). */
    private Object fresh(Class<?> agentInterface) {
        return AgenticServices.agentBuilder(agentInterface).chatModel(chatModel).build();
    }

    /** Both beans are AgentInstances once unwrapped from their client proxies. */
    private static AgentInstance instance(Object bean) {
        Object agent = bean instanceof ClientProxy ? ClientProxy.unwrap(bean) : bean;
        assertTrue(agent instanceof AgentInstance, () -> agent.getClass() + " is no AgentInstance");
        return (AgentInstance) agent;
    }

    private static String print(String name, Object bean) {
        StringBuilder out = new StringBuilder();
        describe(instance(bean), "", out);
        TREES.put(name, out.toString());
        return out.toString();
    }

    private static void describe(AgentInstance agent, String indent, StringBuilder out) {
        out.append(indent).append(agent.topology()).append(' ').append(agent.name())
                .append(" id=").append(agent.agentId())
                .append(" type=").append(agent.type() == null ? null : agent.type().getName())
                .append(" planner=").append(agent.plannerType() == null ? null : agent.plannerType().getSimpleName())
                .append(" out=").append(agent.outputKey()).append(':').append(typeName(agent.outputType()))
                .append(" in=").append(agent.arguments().stream()
                        .map(a -> a.name() + ':' + typeName(a.type()) + (a.isOptional() ? "?" : ""))
                        .toList())
                .append(agent.async() ? " async" : "").append(agent.optional() ? " optional" : "")
                .append(" desc=\"").append(agent.description()).append('"');
        LoopAgentInstance loop = as(agent, LoopAgentInstance.class);
        if (loop != null) {
            out.append(" max=").append(loop.maxIterations()).append(" atEnd=").append(loop.testExitAtLoopEnd())
                    .append(" exit=\"").append(loop.exitCondition()).append('"');
        }
        if (agent.topology() == AgenticSystemTopology.AI_AGENT && agent.type() != null) {
            out.append(prompts(agent.type()));
        }
        out.append('\n');
        ConditionalAgentInstance conditional = as(agent, ConditionalAgentInstance.class);
        if (conditional != null) {
            conditional.conditionalSubagents().forEach(c -> {
                out.append(indent).append("  when \"").append(c.condition()).append("\":\n");
                c.agentInstances().forEach(sub -> describe(sub, indent + "    ", out));
            });
            return;
        }
        agent.subagents().forEach(sub -> describe(sub, indent + "  ", out));
    }

    /** Loop and conditional details come through as(): the agents are no instances of these interfaces. */
    private static <T extends AgentInstance> T as(AgentInstance agent, Class<T> type) {
        try {
            return agent.as(type);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /** AI agent details are not in AgentInstance; a typed agent's interface still carries its annotations. */
    private static String prompts(Class<?> type) {
        for (Method method : type.getMethods()) {
            UserMessage user = method.getAnnotation(UserMessage.class);
            SystemMessage system = method.getAnnotation(SystemMessage.class);
            if (user != null || system != null) {
                return " user=\"" + (user == null ? "" : String.join("", user.value())) + "\""
                        + (system == null ? "" : " system=\"" + String.join("", system.value()) + "\"");
            }
        }
        return "";
    }

    private static String typeName(java.lang.reflect.Type type) {
        return type == null ? null : type.getTypeName().replaceAll("[a-z0-9_.]+\\.([A-Z])", "$1");
    }

    /** Structure without what only the builder may lack (exit condition text, names of the workflow methods). */
    private static List<String> shape(Object bean) {
        List<String> shape = new ArrayList<>();
        shape(instance(bean), shape);
        return shape;
    }

    private static void shape(AgentInstance agent, List<String> shape) {
        LoopAgentInstance loop = as(agent, LoopAgentInstance.class);
        shape.add(agent.topology() + " " + agent.name() + " " + agent.outputKey() + " " + agent.subagents().size()
                + (loop != null ? " max=" + loop.maxIterations() + " atEnd=" + loop.testExitAtLoopEnd() : ""));
        agent.subagents().forEach(sub -> shape(sub, shape));
    }

    @Test
    void builderTwinHasTheDeclarativeShape() {
        String declarative = print("StoryCreator (declarative)", storyCreator);
        String builder = print("BuilderStoryCreator (builder, UntypedAgent bean)", builderStoryCreator);

        assertEquals(shape(storyCreator), shape(builderStoryCreator));
        // the declarative loop describes its exit condition, the builder lambda does not
        assertTrue(declarative.contains("exit=\"style score is at least 0.8\""), declarative);
        assertTrue(builder.contains("exit=\"<unknown>\""), builder);
        // the builder root declares no input and outputs Object: its inputs are only known from its agents
        assertTrue(builder.startsWith("SEQUENCE create id=create type=dev.langchain4j.agentic.UntypedAgent"), builder);
        assertTrue(builder.contains("out=story:Object in=[]"), builder);
    }

    @Test
    void typedBuilderInterface() {
        String tree = print("BuilderStoryWriter (builder, typed interface bean)", builderStoryWriter);
        assertTrue(tree.startsWith("SEQUENCE write"), tree);
    }

    @Test
    void conditionsOfBuilderAndDeclarativeRouters() {
        print("ExpertRouter (declarative)", expertRouter);
        UntypedAgent experts = AgenticServices.conditionalBuilder()
                .name("askExpert")
                .subAgents("category is medical", scope -> true, fresh(ExpertRouter.MedicalExpert.class))
                .subAgents(scope -> false, fresh(ExpertRouter.TechnicalExpert.class))
                .outputKey("response")
                .build();
        UntypedAgent router = AgenticServices.sequenceBuilder().name("ask")
                .subAgents(fresh(ExpertRouter.CategoryRouter.class), experts).outputKey("response").build();
        String tree = print("ExpertRouter twin (builder, built per request)", router);
        assertTrue(tree.contains("when \"category is medical\""), tree);
        // a lambda without description: nothing to show but that it is one
        assertTrue(tree.contains("when \"<unknown>\""), tree);
    }

    /** Builder supervisor, planner and human-in-the-loop step: what tells them apart. */
    @Test
    void builderSupervisorPlannerAndHumanStep() {
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder().name("transact").chatModel(chatModel)
                .subAgents(fresh(BankSupervisor.ExchangeAgent.class), fresh(BankSupervisor.WithdrawAgent.class)).build();
        UntypedAgent planned = AgenticServices.plannerBuilder().name("write")
                .subAgents(fresh(DraftReviewer.Drafter.class), fresh(DraftReviewer.Reviewer.class))
                .planner(DraftReviewer.DraftReviewPlanner::new).outputKey("draft").build();
        HumanInTheLoop approval = AgenticServices.humanInTheLoopBuilder().description("Asks the traveler to approve")
                .inputKey(String.class, "draft").outputKey("approval").responseProvider(scope -> "yes").build();
        UntypedAgent root = AgenticServices.sequenceBuilder().name("root").subAgents(supervisor, planned, approval).build();

        String tree = print("Builder supervisor, planner and human step", root);
        // supervisors and planners report a SEQUENCE topology: their planner type tells them apart
        assertTrue(tree.contains("SEQUENCE transact id=transact$0 type=dev.langchain4j.agentic.supervisor.SupervisorAgent planner=SupervisorPlanner"), tree);
        assertTrue(tree.contains("SEQUENCE write id=write$1 type=dev.langchain4j.agentic.UntypedAgent planner=DraftReviewPlanner"), tree);
        // a builder human step is HUMAN_IN_THE_LOOP (a declarative one is a NON_AI_AGENT)
        assertTrue(tree.contains("HUMAN_IN_THE_LOOP askUser id=askUser$2 type=dev.langchain4j.agentic.workflow.HumanInTheLoop"), tree);
    }

    @Test
    void otherKinds() {
        print("ResearchGatherer (declarative parallel)", researchGatherer);
        // the topology does not tell a mapper, a supervisor or a custom planner: the planner type does
        assertTrue(print("PhraseTranslator (declarative parallel mapper)", phraseTranslator)
                .startsWith("PARALLEL translateAll id=translateAll type=io.quarkiverse.langchain4j.agentic.viewer.it.samples.PhraseTranslator planner=ParallelMapperPlanner"));
        assertTrue(print("BankSupervisor (declarative supervisor)", bankSupervisor).contains("planner=SupervisorPlanner"));
        assertTrue(print("DraftReviewer (declarative planner)", draftReviewer).contains("planner=DraftReviewPlanner"));
        // a human-in-the-loop step is a NON_AI_AGENT; its type is the interface carrying @HumanInTheLoop
        assertTrue(print("TripPlanner (declarative, human in the loop)", tripPlanner)
                .contains("NON_AI_AGENT askTraveler id=askTraveler$1 type=io.quarkiverse.langchain4j.agentic.viewer.it.samples.TripPlanner$TravelerApproval"));
    }
}
