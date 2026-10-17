package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastUserMessage;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * A larger system mixing the workflow patterns, with inputs of every kind the run form handles (text, enum, integer,
 * list, record): research in parallel, one pitch per market (parallel mapper), a campaign drafted then refined in a
 * loop, channels chosen by budget (conditional, one branch a nested sequence), a summary. 17 nodes.
 */
public interface ProductLaunch {

    enum Audience {
        CONSUMERS,
        DEVELOPERS,
        ENTERPRISES
    }

    /** Guidelines for the copy. */
    record Brief(String tone, int maxWords) {
    }

    @SequenceAgent(description = "Plans the launch of a product: research, pitches, campaign, channels, summary", outputKey = "launchPlan", subAgents = {
            MarketResearch.class, LocalizedPitches.class, CampaignDrafter.class, CampaignRefinement.class,
            ChannelSelection.class, LaunchSummarizer.class })
    String plan(@V("product") String product, @V("audience") Audience audience, @V("budget") int budget,
            @V("markets") List<String> markets, @V("brief") Brief brief);

    @Output
    static String launchPlan(@V("summary") String summary, @V("pitches") List<String> pitches,
            @V("channels") String channels) {
        return summary + "\nPitches: " + String.join(" | ", pitches) + "\nChannels: " + channels;
    }

    // ---- research, in parallel ----

    interface MarketResearch {
        @ParallelAgent(description = "Studies competitors, trends and the audience in parallel", outputKey = "research", subAgents = {
                CompetitorAnalyst.class, TrendAnalyst.class, AudienceAnalyst.class })
        String research(@V("product") String product, @V("audience") Audience audience);

        @Output
        static String combine(@V("competitors") String competitors, @V("trends") String trends,
                @V("audienceInsights") String audienceInsights) {
            return competitors + "; " + trends + "; " + audienceInsights;
        }
    }

    interface CompetitorAnalyst {
        @UserMessage("List the competitors of: {{product}}")
        @Agent(description = "Lists the competitors", outputKey = "competitors")
        String competitors(@V("product") String product);
    }

    interface TrendAnalyst {
        @UserMessage("Describe the market trends for: {{product}}")
        @Agent(description = "Describes the market trends", outputKey = "trends")
        String trends(@V("product") String product);
    }

    interface AudienceAnalyst {
        @UserMessage("Describe this audience: {{audience}}")
        @Agent(description = "Describes the target audience", outputKey = "audienceInsights")
        String audience(@V("audience") Audience audience);
    }

    // ---- one pitch per market ----

    interface LocalizedPitches {
        @ParallelMapperAgent(description = "Writes one pitch per market, in parallel", outputKey = "pitches", subAgent = PitchWriter.class, itemsProvider = "markets")
        List<String> pitches(@V("markets") List<String> markets);
    }

    interface PitchWriter {
        @UserMessage("Write a launch pitch for the market: {{market}}")
        @Agent(description = "Writes the pitch of one market", outputKey = "pitch")
        String pitch(@V("market") String market);
    }

    // ---- campaign: a draft, then a refinement loop ----

    interface CampaignDrafter {
        @UserMessage("Draft a launch campaign for {{product}} with the brief {{brief}} from: {{research}}")
        @Agent(description = "Drafts the campaign from the research and the brief", outputKey = "campaign")
        String draft(@V("product") String product, @V("brief") Brief brief, @V("research") String research);
    }

    interface CampaignRefinement {
        @LoopAgent(description = "Refines the campaign until its score is at least 0.8", outputKey = "campaign", maxIterations = 4, subAgents = {
                CampaignCritic.class, CampaignEditor.class })
        String refine(@V("campaign") String campaign);

        @ExitCondition(testExitAtLoopEnd = true, description = "campaign score is at least 0.8")
        static boolean convincing(@V("campaignScore") double score) {
            return score >= 0.8;
        }
    }

    interface CampaignCritic {
        @UserMessage("Score this campaign from 0.0 to 1.0: {{campaign}}")
        @Agent(description = "Scores the campaign", outputKey = "campaignScore")
        double score(@V("campaign") String campaign);
    }

    interface CampaignEditor {
        @UserMessage("Improve this campaign: {{campaign}}")
        @Agent(description = "Rewrites the campaign", outputKey = "campaign")
        String edit(@V("campaign") String campaign);
    }

    // ---- channels, chosen by budget ----

    interface ChannelSelection {
        @ConditionalAgent(description = "Chooses the channels the budget allows", outputKey = "channels", subAgents = {
                PremiumChannels.class, DigitalChannels.class })
        String channels(@V("product") String product, @V("budget") int budget);

        @ActivationCondition(value = PremiumChannels.class, description = "budget of at least 10000")
        static boolean premium(@V("budget") int budget) {
            return budget >= 10_000;
        }

        @ActivationCondition(value = DigitalChannels.class, description = "budget under 10000")
        static boolean digital(@V("budget") int budget) {
            return budget < 10_000;
        }
    }

    interface PremiumChannels {
        @SequenceAgent(description = "Paid media, then a launch event", outputKey = "channels", subAgents = {
                MediaBuyer.class, EventPlanner.class })
        String premium(@V("product") String product, @V("budget") int budget);

        @Output
        static String channels(@V("media") String media, @V("event") String event) {
            return media + " + " + event;
        }
    }

    interface MediaBuyer {
        @UserMessage("Plan the paid media with a budget of: {{budget}}")
        @Agent(description = "Plans the paid media", outputKey = "media")
        String media(@V("budget") int budget);
    }

    interface EventPlanner {
        @UserMessage("Plan a launch event for: {{product}}")
        @Agent(description = "Plans the launch event", outputKey = "event")
        String event(@V("product") String product);
    }

    interface DigitalChannels {
        @UserMessage("Plan the social media launch of: {{product}}")
        @Agent(description = "Plans the social media launch", outputKey = "channels")
        String social(@V("product") String product);
    }

    // ---- summary ----

    interface LaunchSummarizer {
        @UserMessage("Summarize the launch plan of {{product}} with the campaign: {{campaign}}")
        @Agent(description = "Summarizes the plan", outputKey = "summary")
        String summarize(@V("product") String product, @V("campaign") String campaign);
    }

    /**
     * Campaigns are versioned: each edit increments the version and the score grows with it, so the loop ends after 3
     * iterations; the other agents answer from their prompt.
     */
    @ApplicationScoped
    class Script implements ChatModelScript {

        private static final Pattern VERSION = Pattern.compile("\\(v(\\d+)\\)");

        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("List the competitors of:"))
                    .reply(request -> "2 competitors of " + userMessageAfter(request, "of:"))
                    .when(userMessageContains("Describe the market trends for:"))
                    .reply(request -> "growing demand for " + userMessageAfter(request, "for:"))
                    .when(userMessageContains("Describe this audience:"))
                    .reply(request -> userMessageAfter(request, "audience:").toLowerCase() + " want speed")
                    .when(userMessageContains("Write a launch pitch for the market:"))
                    .reply(request -> "pitch for " + userMessageAfter(request, "market:"))
                    .when(userMessageContains("Draft a launch campaign for"))
                    .reply(request -> "Campaign (v1)")
                    .when(userMessageContains("Score this campaign from 0.0 to 1.0:"))
                    .reply(request -> switch (version(request)) {
                        case 1 -> "0.4";
                        case 2 -> "0.7";
                        default -> "0.9";
                    })
                    .when(userMessageContains("Improve this campaign:"))
                    .reply(request -> "Campaign (v" + (version(request) + 1) + ")")
                    .when(userMessageContains("Plan the paid media with a budget of:"))
                    .reply(request -> "paid media for " + userMessageAfter(request, "of:"))
                    .when(userMessageContains("Plan a launch event for:"))
                    .reply(request -> "an event for " + userMessageAfter(request, "for:"))
                    .when(userMessageContains("Plan the social media launch of:"))
                    .reply(request -> "social media for " + userMessageAfter(request, "of:"))
                    .when(userMessageContains("Summarize the launch plan of"))
                    .reply(request -> "Launch plan of " + userMessageAfter(request, "plan of").replace(" with the campaign:", ","));
        }

        private static int version(ChatRequest request) {
            Matcher matcher = VERSION.matcher(lastUserMessage(request));
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
        }
    }
}
