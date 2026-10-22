package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.langchain4j.agentic.viewer.it.ScenarioResource;
import io.quarkiverse.langchain4j.agentic.viewer.it.ScenarioResource.Scenario;

/**
 * Runs each langchain4j-agentic-patterns sample over HTTP (profile "patterns"), as {@link ScenarioResource} does for
 * the other samples: {@code GET /scenarios/patterns} lists them.
 */
@Path("/scenarios/patterns")
@Produces(MediaType.TEXT_PLAIN)
public class PatternScenarioResource {

    public static final List<Scenario> SCENARIOS = List.of(
            new Scenario("/scenarios/patterns/trip-goal-planner", "city=Lisbon&budget=medium",
                    "TripGoalPlanner: GOAP planner, calls only the agents needed for the goal"),
            new Scenario("/scenarios/patterns/research-team", "topic=quantum batteries",
                    "ResearchTeam: peer-to-peer planner"),
            new Scenario("/scenarios/patterns/incident-responder", "incident=Checkout fails since 10:00",
                    "IncidentResponder: blackboard planner with a conflict resolution strategy"),
            new Scenario("/scenarios/patterns/release-captain", "bugReport=Invoice export fails",
                    "ReleaseCaptain: BDI planner, desires by priority"),
            new Scenario("/scenarios/patterns/design-debate", "proposal=one queue",
                    "DesignDebate: debate planner, then a judge"),
            new Scenario("/scenarios/patterns/review-panel", "change=Cache the invoices",
                    "ReviewPanel: voting planner, the majority decides"),
            new Scenario("/scenarios/patterns/feature-delivery", "feature=CSV export&repository=acme/billing",
                    "FeatureDelivery: GOAP, voting and BDI nested in workflows, a tool, a @HumanInTheLoop sign-off "
                            + "(HumanInput approves)"));

    @Inject
    TripGoalPlanner tripGoalPlanner;

    @Inject
    ResearchTeam researchTeam;

    @Inject
    IncidentResponder incidentResponder;

    @Inject
    ReleaseCaptain releaseCaptain;

    @Inject
    DesignDebate designDebate;

    @Inject
    ReviewPanel reviewPanel;

    @Inject
    FeatureDelivery featureDelivery;

    @GET
    public String index() {
        return SCENARIOS.stream().map(Scenario::line).collect(Collectors.joining("\n", "", "\n"));
    }

    @GET
    @Path("/trip-goal-planner")
    public String tripGoalPlanner(@QueryParam("city") @DefaultValue("Lisbon") String city,
            @QueryParam("budget") @DefaultValue("medium") String budget) {
        return tripGoalPlanner.plan(city, budget);
    }

    @GET
    @Path("/research-team")
    public String researchTeam(@QueryParam("topic") @DefaultValue("quantum batteries") String topic) {
        return researchTeam.research(topic);
    }

    @GET
    @Path("/incident-responder")
    public String incidentResponder(@QueryParam("incident") @DefaultValue("Checkout fails since 10:00") String incident) {
        return incidentResponder.respond(incident);
    }

    @GET
    @Path("/release-captain")
    public String releaseCaptain(@QueryParam("bugReport") @DefaultValue("Invoice export fails") String bugReport) {
        return releaseCaptain.release(bugReport);
    }

    @GET
    @Path("/design-debate")
    public String designDebate(@QueryParam("proposal") @DefaultValue("one queue") String proposal) {
        return designDebate.debate(proposal);
    }

    @GET
    @Path("/review-panel")
    public String reviewPanel(@QueryParam("change") @DefaultValue("Cache the invoices") String change) {
        return reviewPanel.review(change);
    }

    @GET
    @Path("/feature-delivery")
    public String featureDelivery(@QueryParam("feature") @DefaultValue("CSV export") String feature,
            @QueryParam("repository") @DefaultValue("acme/billing") String repository) {
        return featureDelivery.deliver(feature, repository);
    }
}
