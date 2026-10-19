package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastMessageIsToolResult;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastToolResult;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastUserMessage;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.patterns.bdi.BDIPlanner;
import dev.langchain4j.agentic.patterns.bdi.Desire;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.patterns.voting.VotingStrategy;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.HumanInput;

/**
 * A bit of everything: workflows with agentic-patterns planners nested in them, a tool, a human step and
 * {@code @Output} methods. A feature goes from discovery to release:
 * <ol>
 * <li>discovery in parallel: user research, and a code scout calling a repository tool ({@code @Output} combines them);</li>
 * <li>a goal-oriented (GOAP) planner reaches the spec through the requirements and the risks; the estimate and the UX
 * sketch are not needed for it, so they are never made;</li>
 * <li>a first implementation, then a review loop: three reviewers vote (voting planner) and a refactorer rewrites the
 * code until the majority approves (version 2);</li>
 * <li>the product owner signs off ({@code @HumanInTheLoop});</li>
 * <li>a conditional rollout: when approved, a BDI planner stages the rollout then announces the release (changelog,
 * announcement); otherwise the release is postponed.</li>
 * </ol>
 * 23 nodes.
 */
public interface FeatureDelivery {

    @SequenceAgent(description = "Delivers a feature: discovery, spec, implementation and review, sign-off, rollout", outputKey = "outcome", subAgents = {
            Discovery.class, SpecPlanner.class, Implementer.class, ReviewLoop.class, ProductOwnerSignOff.class,
            Rollout.class })
    String deliver(@V("feature") String feature, @V("repository") String repository);

    @Output
    static String outcome(@V("release") String release, @V("verdict") String verdict) {
        return release + " (review: " + verdict + ")";
    }

    // ---- discovery, in parallel ----

    interface Discovery {
        @ParallelAgent(description = "Researches the users' needs and scouts the code at once", outputKey = "findings", subAgents = {
                UserResearcher.class, CodeScout.class })
        String discover(@V("feature") String feature, @V("repository") String repository);

        @Output
        static String findings(@V("userNeeds") String userNeeds, @V("codeMap") String codeMap) {
            return userNeeds + "; " + codeMap;
        }
    }

    interface UserResearcher {
        @UserMessage("Interview users about the feature: {{feature}}")
        @Agent(description = "Finds what users need from the feature", outputKey = "userNeeds")
        String research(@V("feature") String feature);
    }

    interface CodeScout {
        @UserMessage("Scout the code base of {{repository}} for the feature: {{feature}}")
        @Agent(description = "Finds the code the feature touches, with the repository tool", outputKey = "codeMap")
        @ToolBox(RepositoryTool.class)
        String scout(@V("repository") String repository, @V("feature") String feature);
    }

    @ApplicationScoped
    class RepositoryTool {
        @Tool("Searches the repository for code matching the query and tells which modules it is in")
        public String searchCode(String query) {
            return "billing and reporting modules match '" + query + "'";
        }
    }

    // ---- spec: a goal-oriented planner ----

    interface SpecPlanner {
        @PlannerAgent(description = "Reaches the spec with only the agents it needs", outputKey = "spec", subAgents = {
                RequirementsWriter.class, RiskAssessor.class, Estimator.class, UxSketcher.class, SpecWriter.class })
        String specify(@V("feature") String feature, @V("findings") String findings);

        @PlannerSupplier
        static Planner planner() {
            return new GoalOrientedPlanner();
        }
    }

    interface RequirementsWriter {
        @UserMessage("Write the requirements of {{feature}} from these findings: {{findings}}")
        @Agent(description = "Writes the requirements", outputKey = "requirements")
        String requirements(@V("feature") String feature, @V("findings") String findings);
    }

    interface RiskAssessor {
        @UserMessage("Assess the delivery risks of these requirements: {{requirements}}")
        @Agent(description = "Assesses the risks", outputKey = "risks")
        String risks(@V("requirements") String requirements);
    }

    interface Estimator {
        @UserMessage("Estimate the effort of these requirements: {{requirements}}")
        @Agent(description = "Estimates the effort (not needed for the spec)", outputKey = "estimate")
        String estimate(@V("requirements") String requirements);
    }

    interface UxSketcher {
        @UserMessage("Sketch the screens of the feature: {{feature}}")
        @Agent(description = "Sketches the screens (not needed for the spec)", outputKey = "mockups")
        String sketch(@V("feature") String feature);
    }

    interface SpecWriter {
        @UserMessage("Write the spec from the requirements {{requirements}} and the risks {{risks}}")
        @Agent(description = "Writes the spec", outputKey = "spec")
        String spec(@V("requirements") String requirements, @V("risks") String risks);
    }

    // ---- implementation, then a review loop with a voting planner ----

    interface Implementer {
        @UserMessage("Implement this spec: {{spec}}")
        @Agent(description = "Implements the spec", outputKey = "code")
        String implement(@V("spec") String spec);
    }

    interface ReviewLoop {
        @LoopAgent(description = "Reviews and refactors the code until the reviewers approve it", outputKey = "code", maxIterations = 3, subAgents = {
                CodeReview.class, Refactorer.class })
        String review(@V("code") String code);

        @ExitCondition(description = "the reviewers approve")
        static boolean approved(@V("verdict") String verdict) {
            return "APPROVE".equals(verdict);
        }
    }

    interface CodeReview {
        @PlannerAgent(description = "Reviewers vote on the code, the majority decides", outputKey = "verdict", subAgents = {
                ReadabilityReviewer.class, SecurityAuditor.class, PerformanceAuditor.class })
        String vote(@V("code") String code);

        @PlannerSupplier
        static Planner planner() {
            return new VotingPlanner(VotingStrategy.majority());
        }
    }

    interface ReadabilityReviewer {
        @UserMessage("Vote APPROVE or REJECT on the readability of the code: {{code}}")
        @Agent(description = "Votes on the readability", outputKey = "readabilityVote")
        String voteOnReadability(@V("code") String code);
    }

    interface SecurityAuditor {
        @UserMessage("Vote APPROVE or REJECT on the security of the code: {{code}}")
        @Agent(description = "Votes on the security", outputKey = "securityAudit")
        String voteOnSecurity(@V("code") String code);
    }

    interface PerformanceAuditor {
        @UserMessage("Vote APPROVE or REJECT on the performance of the code: {{code}}")
        @Agent(description = "Votes on the performance", outputKey = "performanceAudit")
        String voteOnPerformance(@V("code") String code);
    }

    interface Refactorer {
        @UserMessage("Refactor the code the reviewers rejected: {{code}}")
        @Agent(description = "Rewrites the code", outputKey = "code")
        String refactor(@V("code") String code);
    }

    // ---- sign-off ----

    interface ProductOwnerSignOff {
        @HumanInTheLoop(description = "Asks the product owner to sign the code off", outputKey = "approval")
        static String askProductOwner(@V("code") String code) {
            return HumanInput.ask("Ship " + code + "?");
        }
    }

    // ---- rollout: a BDI planner when approved ----

    interface Rollout {
        @ConditionalAgent(description = "Releases the feature when the product owner approved it, postpones it otherwise", outputKey = "release", subAgents = {
                ReleaseTrain.class, Postponer.class })
        String rollout(@V("approval") String approval, @V("code") String code);

        @ActivationCondition(value = ReleaseTrain.class, description = "the product owner said yes")
        static boolean approved(@V("approval") String approval) {
            return approval.startsWith("Yes");
        }

        @ActivationCondition(value = Postponer.class, description = "the product owner did not say yes")
        static boolean declined(@V("approval") String approval) {
            return !approval.startsWith("Yes");
        }
    }

    interface ReleaseTrain {
        @PlannerAgent(description = "Stages the rollout, then announces the release", outputKey = "release", subAgents = {
                RolloutPlanner.class, ChangelogWriter.class, ReleaseAnnouncer.class })
        String ship(@V("code") String code);

        @PlannerSupplier
        static Planner planner() {
            return new BDIPlanner(List.of(
                    Desire.of("stage the rollout", 10, "code", "rolloutPlan", RolloutPlanner.class),
                    Desire.of("announce the release", 5, "rolloutPlan", "release", ChangelogWriter.class,
                            ReleaseAnnouncer.class)));
        }
    }

    interface RolloutPlanner {
        @UserMessage("Plan a staged rollout of the code: {{code}}")
        @Agent(description = "Plans the staged rollout", outputKey = "rolloutPlan")
        String plan(@V("code") String code);
    }

    interface ChangelogWriter {
        @UserMessage("Write the changelog of this rollout: {{rolloutPlan}}")
        @Agent(description = "Writes the changelog", outputKey = "changelog")
        String changelog(@V("rolloutPlan") String rolloutPlan);
    }

    interface ReleaseAnnouncer {
        @UserMessage("Announce the feature release with this changelog: {{changelog}}")
        @Agent(description = "Announces the release", outputKey = "release")
        String announce(@V("changelog") String changelog);
    }

    interface Postponer {
        @UserMessage("Tell the team the release of {{code}} is postponed, the product owner answered: {{approval}}")
        @Agent(description = "Postpones the release", outputKey = "release")
        String postpone(@V("code") String code, @V("approval") String approval);
    }

    /**
     * The code is versioned: the security and performance auditors reject version 1, so the reviewers approve after
     * one refactoring; the other agents answer from their prompt.
     */
    @ApplicationScoped
    class Script implements ChatModelScript {

        private static final Pattern VERSION = Pattern.compile("\\(v(\\d+)\\)");

        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Interview users about the feature:"))
                    .reply(request -> "users want " + userMessageAfter(request, "feature:"))
                    // the scout calls its tool, then answers from the result
                    .when(userMessageContains("Scout the code base of").and(lastMessageIsToolResult("searchCode")))
                    .reply(request -> "code map: " + lastToolResult(request))
                    .when(userMessageContains("Scout the code base of")).callTool("searchCode", "{\"query\": \"export\"}")
                    .when(userMessageContains("Write the requirements of")).reply("3 requirements")
                    .when(userMessageContains("Assess the delivery risks of")).reply("1 risk: large exports")
                    .when(userMessageContains("Estimate the effort of")).reply("5 days")
                    .when(userMessageContains("Sketch the screens of the feature:")).reply("2 screens")
                    .when(userMessageContains("Write the spec from the requirements"))
                    .reply("Spec: 3 requirements, mitigating large exports")
                    .when(userMessageContains("Implement this spec:")).reply("Streaming export (v1)")
                    .when(userMessageContains("on the readability of the code:")).reply("APPROVE")
                    .when(userMessageContains("on the security of the code:"))
                    .reply(request -> version(request) < 2 ? "REJECT" : "APPROVE")
                    .when(userMessageContains("on the performance of the code:"))
                    .reply(request -> version(request) < 2 ? "REJECT" : "APPROVE")
                    .when(userMessageContains("Refactor the code the reviewers rejected:"))
                    .reply(request -> "Streaming export (v" + (version(request) + 1) + ")")
                    .when(userMessageContains("Plan a staged rollout of the code:"))
                    .reply(request -> "10% then 100% of " + userMessageAfter(request, "code:"))
                    .when(userMessageContains("Write the changelog of this rollout:")).reply("Exports now stream")
                    .when(userMessageContains("Announce the feature release with this changelog:"))
                    .reply(request -> "Released: " + userMessageAfter(request, "changelog:"))
                    .when(userMessageContains("is postponed, the product owner answered:"))
                    .reply(request -> "Postponed: " + userMessageAfter(request, "answered:"));
        }

        private static int version(ChatRequest request) {
            Matcher matcher = VERSION.matcher(lastUserMessage(request));
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
        }
    }
}
