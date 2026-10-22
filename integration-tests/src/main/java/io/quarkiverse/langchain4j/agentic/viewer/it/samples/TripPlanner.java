package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Human in the loop: the traveler approves the proposed destination before the itinerary is written.
 */
public interface TripPlanner {

    @SequenceAgent(description = "Proposes a destination, asks the traveler, then writes an itinerary", outputKey = "itinerary", subAgents = {
            DestinationProposer.class, TravelerApproval.class, ItineraryWriter.class })
    String plan(@V("wish") String wish);

    interface DestinationProposer {
        @UserMessage("Propose one travel destination for this wish: {{wish}}")
        @Agent(description = "Proposes a destination", outputKey = "destination")
        String propose(@V("wish") String wish);
    }

    interface TravelerApproval {
        @HumanInTheLoop(description = "Asks the traveler to approve the destination", outputKey = "approval")
        @Agent(description = "Asks the traveler to approve the destination")
        static String askTraveler(@V("destination") String destination) {
            return HumanInput.ask("Do you want to travel to " + destination + "?");
        }
    }

    interface ItineraryWriter {
        @UserMessage("Write a 3-day itinerary for {{destination}}. The traveler answered: {{approval}}")
        @Agent(description = "Writes the itinerary", outputKey = "itinerary")
        String write(@V("destination") String destination, @V("approval") String approval);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Propose one travel destination for this wish:"))
                    .reply(request -> userMessageAfter(request, "wish:").contains("sea") ? "Lisbon" : "Vienna")
                    .when(userMessageContains("Write a 3-day itinerary for").and(userMessageContains("answered: Yes")))
                    .reply(request -> "Day 1, 2 and 3 in " + destination(userMessageAfter(request, "itinerary for")))
                    .when(userMessageContains("Write a 3-day itinerary for"))
                    .reply("No itinerary: the traveler did not approve the destination.");
        }

        private static String destination(String textAfterFor) {
            int end = textAfterFor.indexOf(". The traveler answered:");
            return end < 0 ? textAfterFor : textAfterFor.substring(0, end);
        }
    }
}
