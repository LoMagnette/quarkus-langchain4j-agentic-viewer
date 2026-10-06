package io.quarkiverse.langchain4j.agentic.viewer.it.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.DraftReviewer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryWriter;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T0.7 spike (TASK-12): decorating the planner of agents built by quarkus-langchain4j-agentic to observe its decisions.
 * The supplier is a private final field of PlannerBasedInvocationHandler, set at build time; this prototype replaces it
 * after build through reflection, on the root agent of each system (proof of mechanism, not the intended design).
 */
@QuarkusTest
class PlannerWrapSpikeTest {

    /**
     * Delegates everything to the real planner and records each Action it returns.
     */
    static class DecisionRecordingPlanner implements Planner {

        private final Planner delegate;
        private final List<String> decisions;

        DecisionRecordingPlanner(Planner delegate, List<String> decisions) {
            this.delegate = delegate;
            this.decisions = decisions;
        }

        @Override
        public void init(InitPlanningContext context) {
            delegate.init(context);
        }

        @Override
        public Action firstAction(PlanningContext context) {
            return record("first", delegate.firstAction(context));
        }

        @Override
        public Action nextAction(PlanningContext context) {
            return record("next", delegate.nextAction(context));
        }

        @Override
        public Map<String, Object> executionState() {
            return delegate.executionState();
        }

        @Override
        public void restoreExecutionState(Map<String, Object> state) {
            delegate.restoreExecutionState(state);
        }

        @Override
        public AgenticSystemTopology topology() {
            return delegate.topology();
        }

        @Override
        public boolean terminated() {
            return delegate.terminated();
        }

        private Action record(String step, Action action) {
            String decision = switch (action) {
                case Action.AgentCallAction call -> "call " + call.agentsToCall().stream().map(AgentExecutor::name).toList();
                case Action.DoneWithResultAction ignored -> "done with result";
                case Action.DoneAction ignored -> "done";
                default -> action.toString();
            };
            decisions.add(step + ": " + decision);
            return action;
        }
    }

    @Inject
    DraftReviewer draftReviewer;

    @Inject
    BankSupervisor bankSupervisor;

    @Inject
    BankSupervisor.BankTool bankTool;

    @Inject
    StoryWriter storyWriter;

    @BeforeEach
    void reset() {
        bankTool.reset();
    }

    @Test
    void customPlannerDecisions() throws Exception {
        List<String> decisions = decorateRootPlanner(draftReviewer);

        draftReviewer.write("solar panels");

        assertEquals(List.of(
                "first: call [writeDraft]",
                "next: call [review]",
                "next: call [writeDraft]",
                "next: call [review]",
                "next: done"), decisions);
    }

    @Test
    void supervisorDecisions() throws Exception {
        List<String> decisions = decorateRootPlanner(bankSupervisor);

        bankSupervisor.transact(BankSupervisor.Script.request());

        assertEquals(List.of(
                "first: call [exchange]",
                "next: call [withdraw]",
                "next: call [credit]",
                "next: done with result"), decisions);
    }

    @Test
    void sequenceDecisions() throws Exception {
        List<String> decisions = decorateRootPlanner(storyWriter);

        storyWriter.writeStory("Ada");

        assertEquals(List.of("first: call [writeIntro]", "next: call [writeAdventure]", "next: done"), decisions);
    }

    /**
     * Replaces the planner supplier of a root agent: the Quarkus agent bean ({@code $$QuarkusAgentImpl}) holds the
     * PlannerBasedInvocationHandler in its {@code agent} field; the handler holds the private final {@code plannerSupplier}.
     */
    @SuppressWarnings("unchecked")
    private static List<String> decorateRootPlanner(Object rootAgentBean) throws Exception {
        Object quarkusAgent = ClientProxy.unwrap(rootAgentBean);
        Field agentField = findField(quarkusAgent.getClass(), "agent");
        Object internalAgent = agentField.get(quarkusAgent);
        // under Quarkus the InternalAgent handed to the agent instance factory is the handler itself;
        // with plain langchain4j it is a JDK proxy around it
        Object handler = Proxy.isProxyClass(internalAgent.getClass()) ? Proxy.getInvocationHandler(internalAgent) : internalAgent;
        Field supplierField = findField(handler.getClass(), "plannerSupplier");
        Supplier<Planner> original = (Supplier<Planner>) supplierField.get(handler);

        List<String> decisions = new CopyOnWriteArrayList<>();
        supplierField.set(handler, (Supplier<Planner>) () -> new DecisionRecordingPlanner(original.get(), decisions));
        return decisions;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchFieldException(name + " in " + type);
    }
}
