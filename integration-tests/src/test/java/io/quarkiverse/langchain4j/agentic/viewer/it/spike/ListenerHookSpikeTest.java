package io.quarkiverse.langchain4j.agentic.viewer.it.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResilientStoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.TripPlanner;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T0.4 spike (TASK-9): how a listener can observe agents that quarkus-langchain4j-agentic builds from annotations.
 * Executable record of the findings; it also catches behaviour changes when upgrading langchain4j-agentic.
 * <ul>
 * <li>A CDI {@link AgentListener} bean is attached by the extension to every AI agent only: no workflow nodes,
 * no scope lifecycle, no human-in-the-loop step.</li>
 * <li>A listener registered on the root agent instance with {@link InternalAgent#registerInheritedParentListener}
 * (and {@code inheritedBySubagents() = true}) sees the whole system: scope lifecycle, workflow nodes, AI agents,
 * tools and human-in-the-loop steps.</li>
 * </ul>
 */
@QuarkusTest
class ListenerHookSpikeTest {

    abstract static class Recording implements AgentListener {

        // accessed through methods: injected references to the CDI variant are client proxies
        private final List<String> events = new CopyOnWriteArrayList<>();

        List<String> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            events.add("before " + request.agentName());
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            events.add("after " + response.agentName());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError error) {
            events.add("error " + error.agent().name());
        }

        @Override
        public void afterAgenticScopeCreated(AgenticScope scope) {
            events.add("scope created");
        }

        @Override
        public void beforeAgenticScopeDestroyed(AgenticScope scope) {
            events.add("scope destroyed");
        }

        @Override
        public void beforeAgentToolExecution(BeforeAgentToolExecution before) {
            events.add("tool before " + before.toolExecution().request().name());
        }

        @Override
        public void afterAgentToolExecution(AfterAgentToolExecution after) {
            events.add("tool after " + after.toolExecution().request().name());
        }
    }

    /**
     * Discovered as a CDI bean by quarkus-langchain4j-agentic.
     */
    @ApplicationScoped
    public static class CdiListener extends Recording {
        @Override
        public boolean inheritedBySubagents() {
            return true; // makes no difference: the extension attaches it to each AI agent directly
        }
    }

    /**
     * Not a CDI bean: registered programmatically on a root agent instance.
     */
    static class RootListener extends Recording {
        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    @Inject
    CdiListener cdiListener;

    @Inject
    StoryCreator storyCreator;

    @Inject
    BankSupervisor bankSupervisor;

    @Inject
    BankSupervisor.BankTool bankTool;

    @Inject
    TripPlanner tripPlanner;

    @Inject
    ResilientStoryWriter resilientStoryWriter;

    @BeforeEach
    void reset() {
        cdiListener.clear();
        bankTool.reset();
    }

    @Test
    void cdiListenerSeesOnlyAiAgents() {
        storyCreator.create("a lighthouse");

        assertEquals(List.of(
                "before draft", "after draft",
                "before score", "after score", "before edit", "after edit",
                "before score", "after score", "before edit", "after edit",
                "before score", "after score", "before edit", "after edit"), cdiListener.events());
    }

    @Test
    void rootListenerSeesTheWholeNestedSequence() {
        RootListener listener = registerOnRoot(storyCreator);

        storyCreator.create("a lighthouse");

        assertEquals(List.of(
                "scope created",
                "before create",
                "before draft", "after draft",
                "before polish",
                "before score", "after score", "before edit", "after edit",
                "before score", "after score", "before edit", "after edit",
                "before score", "after score", "before edit", "after edit",
                "after polish",
                "after create",
                "scope destroyed"), listener.events());
    }

    @Test
    void rootListenerSeesToolsButNotSupervisorDecisions() {
        RootListener listener = registerOnRoot(bankSupervisor);

        bankSupervisor.transact(BankSupervisor.Script.request());

        assertEquals(List.of(
                "scope created", "before transact",
                "before exchange", "tool before exchange", "tool after exchange", "after exchange",
                "before withdraw", "tool before withdraw", "tool after withdraw", "after withdraw",
                "before credit", "tool before credit", "tool after credit", "after credit",
                "after transact", "scope destroyed"), listener.events());
    }

    @Test
    void rootListenerSeesHumanInTheLoopStep() {
        RootListener listener = registerOnRoot(tripPlanner);

        tripPlanner.plan("a city by the sea");

        assertTrue(listener.events().containsAll(List.of("before askTraveler", "after askTraveler")), listener.events()::toString);
        assertTrue(cdiListener.events().stream().noneMatch(event -> event.endsWith("askTraveler")), "CDI listener misses HITL");
    }

    @Test
    void errorHandlerRetryIsNotReportedToListeners() {
        RootListener listener = registerOnRoot(resilientStoryWriter);

        resilientStoryWriter.write(null);

        // the missing argument fails before the agent is invoked: no error callback, only the successful retry
        assertEquals(List.of(
                "scope created", "before write", "before writeShortStory", "after writeShortStory", "after write",
                "scope destroyed"), listener.events());
    }

    private static RootListener registerOnRoot(Object rootAgentBean) {
        RootListener listener = new RootListener();
        ((InternalAgent) ClientProxy.unwrap(rootAgentBean)).registerInheritedParentListener(listener);
        return listener;
    }
}
