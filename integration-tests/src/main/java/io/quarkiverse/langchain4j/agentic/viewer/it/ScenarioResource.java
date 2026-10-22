package io.quarkiverse.langchain4j.agentic.viewer.it;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.agentic.UntypedAgent;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BlurbedStory;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderDraftReviewer;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderStoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderStoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.DraftReviewer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.PhraseTranslator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ProductLaunch;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ProductLaunch.Audience;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ProductLaunch.Brief;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResearchGatherer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResilientStoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.TripPlanner;

/**
 * Runs each sample system over HTTP, outside the Dev UI: {@code GET /scenarios} lists them. Every query parameter
 * defaults to an input the scripted fake model answers (another input may match no script and fail). These are
 * application runs: the Dev UI lists them (observed only), and {@code @HumanInTheLoop} steps get their answer from
 * {@link io.quarkiverse.langchain4j.agentic.viewer.it.samples.HumanInput} (approves by default).
 * The pattern samples are under {@code /scenarios/patterns} (profile "patterns").
 */
@Path("/scenarios")
@Produces(MediaType.TEXT_PLAIN)
public class ScenarioResource {

    /** An endpoint of the index: path, example query and what it shows. */
    public record Scenario(String path, String query, String description) {

        public String line() {
            return "GET " + path + (query.isEmpty() ? "" : "?" + query) + "\n    " + description;
        }
    }

    public static final List<Scenario> SCENARIOS = List.of(
            new Scenario("/scenarios/story-writer", "bio=Ada, a curious engineer", "StoryWriter: sequence with @Output"),
            new Scenario("/scenarios/research-gatherer", "topic=quantum computing",
                    "ResearchGatherer: parallel with @Output"),
            new Scenario("/scenarios/phrase-translator", "phrase=hello&phrase=thank you&phrase=good night",
                    "PhraseTranslator: parallel mapper, one translation per line"),
            new Scenario("/scenarios/story-creator", "topic=a lighthouse", "StoryCreator: loop nested in a sequence"),
            new Scenario("/scenarios/expert-router", "request=I hurt my leg while running",
                    "ExpertRouter: conditional (leg/fever: medical, laptop/wifi: technical, contract/lease: legal)"),
            new Scenario("/scenarios/bank-supervisor", "request=" + BankSupervisor.Script.request(),
                    "BankSupervisor: supervisor calling AI agents with tools (scripted for this request only)"),
            new Scenario("/scenarios/draft-reviewer", "topic=solar panels",
                    "DraftReviewer: custom @PlannerSupplier planner"),
            new Scenario("/scenarios/trip-planner", "wish=a city by the sea",
                    "TripPlanner: @HumanInTheLoop step, answered by HumanInput (approves)"),
            new Scenario("/scenarios/resilient-story-writer", "topic=pirates",
                    "ResilientStoryWriter: @ErrorHandler, recovers with a default topic when topic is left out"),
            new Scenario("/scenarios/product-launch",
                    "product=Photon&audience=DEVELOPERS&budget=20000&market=Belgium&market=Japan&tone=playful&maxWords=50",
                    "ProductLaunch: every workflow kind, typed inputs (enum, int, list, record); budget < 10000 takes the digital branch"),
            new Scenario("/scenarios/builder/story-creator", "topic=a lighthouse",
                    "Builder UntypedAgent bean: loop nested in a sequence"),
            new Scenario("/scenarios/builder/draft-reviewer", "topic=solar panels",
                    "Builder UntypedAgent bean: custom planner"),
            new Scenario("/scenarios/builder/story-writer", "bio=Ada, a curious engineer",
                    "Builder typed interface bean: sequence with an output function"),
            new Scenario("/builder/research", "topic=quantum computing",
                    "Builder system kept in a service field (not a bean): parallel"),
            new Scenario("/builder/expert", "request=I hurt my leg while running",
                    "Builder system rebuilt on every request: sequence and conditional"),
            new Scenario("/scenarios/blurbed-story", "topic=a lighthouse",
                    "BlurbedStory: mixed, a declarative sequence whose first step calls a builder system"));

    @Inject
    StoryWriter storyWriter;

    @Inject
    ResearchGatherer researchGatherer;

    @Inject
    PhraseTranslator phraseTranslator;

    @Inject
    StoryCreator storyCreator;

    @Inject
    ExpertRouter expertRouter;

    @Inject
    BankSupervisor bankSupervisor;

    @Inject
    DraftReviewer draftReviewer;

    @Inject
    TripPlanner tripPlanner;

    @Inject
    ResilientStoryWriter resilientStoryWriter;

    @Inject
    ProductLaunch productLaunch;

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    @Inject
    @Named(BuilderDraftReviewer.NAME)
    UntypedAgent builderDraftReviewer;

    @Inject
    BuilderStoryWriter builderStoryWriter;

    @Inject
    BlurbedStory blurbedStory;

    @GET
    public String index() {
        return SCENARIOS.stream().map(Scenario::line).collect(Collectors.joining("\n", "", "\n"))
                + "\nPattern samples (profile \"patterns\"): GET /scenarios/patterns\n";
    }

    @GET
    @Path("/story-writer")
    public String storyWriter(@QueryParam("bio") @DefaultValue("Ada, a curious engineer") String bio) {
        return storyWriter.writeStory(bio);
    }

    @GET
    @Path("/research-gatherer")
    public String researchGatherer(@QueryParam("topic") @DefaultValue("quantum computing") String topic) {
        return researchGatherer.gather(topic);
    }

    @GET
    @Path("/phrase-translator")
    public String phraseTranslator(@QueryParam("phrase") List<String> phrases) {
        List<String> items = phrases.isEmpty() ? List.of("hello", "thank you", "good night") : phrases;
        return String.join("\n", phraseTranslator.translateAll(items));
    }

    @GET
    @Path("/story-creator")
    public String storyCreator(@QueryParam("topic") @DefaultValue("a lighthouse") String topic) {
        return storyCreator.create(topic);
    }

    @GET
    @Path("/expert-router")
    public String expertRouter(@QueryParam("request") @DefaultValue("I hurt my leg while running") String request) {
        return expertRouter.ask(request);
    }

    @GET
    @Path("/bank-supervisor")
    public String bankSupervisor(@QueryParam("request") String request) {
        return bankSupervisor.transact(request == null ? BankSupervisor.Script.request() : request);
    }

    @GET
    @Path("/draft-reviewer")
    public String draftReviewer(@QueryParam("topic") @DefaultValue("solar panels") String topic) {
        return draftReviewer.write(topic);
    }

    @GET
    @Path("/trip-planner")
    public String tripPlanner(@QueryParam("wish") @DefaultValue("a city by the sea") String wish) {
        return tripPlanner.plan(wish);
    }

    /** No default: without a topic, the error handler recovers with its own. */
    @GET
    @Path("/resilient-story-writer")
    public String resilientStoryWriter(@QueryParam("topic") String topic) {
        return resilientStoryWriter.write(topic);
    }

    @GET
    @Path("/product-launch")
    public String productLaunch(@QueryParam("product") @DefaultValue("Photon") String product,
            @QueryParam("audience") @DefaultValue("DEVELOPERS") Audience audience,
            @QueryParam("budget") @DefaultValue("20000") int budget,
            @QueryParam("market") List<String> markets,
            @QueryParam("tone") @DefaultValue("playful") String tone,
            @QueryParam("maxWords") @DefaultValue("50") int maxWords) {
        return productLaunch.plan(product, audience, budget, markets.isEmpty() ? List.of("Belgium", "Japan") : markets,
                new Brief(tone, maxWords));
    }

    @GET
    @Path("/builder/story-creator")
    public String builderStoryCreator(@QueryParam("topic") @DefaultValue("a lighthouse") String topic) {
        return String.valueOf(builderStoryCreator.invoke(Map.of("topic", topic)));
    }

    @GET
    @Path("/builder/draft-reviewer")
    public String builderDraftReviewer(@QueryParam("topic") @DefaultValue("solar panels") String topic) {
        return String.valueOf(builderDraftReviewer.invoke(Map.of("topic", topic)));
    }

    @GET
    @Path("/builder/story-writer")
    public String builderStoryWriter(@QueryParam("bio") @DefaultValue("Ada, a curious engineer") String bio) {
        return builderStoryWriter.write(bio);
    }

    @GET
    @Path("/blurbed-story")
    public String blurbedStory(@QueryParam("topic") @DefaultValue("a lighthouse") String topic) {
        return blurbedStory.create(topic);
    }
}
