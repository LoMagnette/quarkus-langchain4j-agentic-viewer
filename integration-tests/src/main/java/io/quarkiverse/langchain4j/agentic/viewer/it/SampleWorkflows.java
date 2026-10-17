package io.quarkiverse.langchain4j.agentic.viewer.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BlurbedStory;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.DraftReviewer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.PhraseTranslator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ProductLaunch;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResearchGatherer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResilientStoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.TripPlanner;

/**
 * Entry points of the sample agentic systems.
 * Injecting the root agents is what tells quarkus-langchain4j-agentic that their parameters
 * are provided by the caller, as in a real application. Tests and the Dev UI inject the agents directly.
 */
@ApplicationScoped
public class SampleWorkflows {

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
    BlurbedStory blurbedStory;

    @Inject
    ProductLaunch productLaunch;
}
