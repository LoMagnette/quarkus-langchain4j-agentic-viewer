package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;

/**
 * Fixture app of the UI tests: a custom planner calling an AI agent that uses a tool, so a run emits agent, tool,
 * scope and planner events.
 */
public interface ToolPlanner {

    @PlannerAgent(outputKey = "answer", subAgents = { Clerk.class })
    String ask(@V("question") String question);

    @PlannerSupplier
    static Planner planner() {
        return new OneShot();
    }

    /** Calls its agent once, then is done. */
    class OneShot implements Planner {
        private AgentInstance clerk;

        @Override
        public void init(InitPlanningContext context) {
            clerk = context.subagents().get(0);
        }

        @Override
        public Action firstAction(PlanningContext context) {
            return call(clerk);
        }

        @Override
        public Action nextAction(PlanningContext context) {
            return done();
        }
    }

    interface Clerk {
        @dev.langchain4j.service.UserMessage("Answer with the clock: {{question}}")
        @Agent(description = "Answers using the clock", outputKey = "answer")
        @ToolBox(Clock.class)
        String answer(@V("question") String question);
    }

    @ApplicationScoped
    class Clock {
        @Tool("Tells the time")
        public String time() {
            return "noon";
        }
    }

    /** Injecting the root agent marks its parameters as provided by the caller. */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        ToolPlanner planner;
    }
}
