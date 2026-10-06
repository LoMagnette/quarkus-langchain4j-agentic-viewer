package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Entry points of the langchain4j-agentic-patterns samples (see SampleWorkflows for why roots are injected).
 */
@ApplicationScoped
public class PatternSampleWorkflows {

    @Inject
    TripGoalPlanner tripGoalPlanner;

    @Inject
    ResearchTeam researchTeam;

    @Inject
    IncidentResponder incidentResponder;
}
