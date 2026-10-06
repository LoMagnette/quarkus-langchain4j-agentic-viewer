package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Goal-oriented (GOAP): the planner searches the agents' input and output keys for a path to the goal
 * key {@code itinerary}. The restaurant critic is not needed for the goal, so it is never called.
 */
public interface TripGoalPlanner {

    @PlannerAgent(description = "Builds an itinerary by calling only the agents needed for it", outputKey = "itinerary", subAgents = {
            WeatherForecaster.class, HotelFinder.class, ActivityPlanner.class, RestaurantCritic.class,
            ItineraryBuilder.class })
    String plan(@V("city") String city, @V("budget") String budget);

    @PlannerSupplier
    static Planner planner() {
        return new GoalOrientedPlanner();
    }

    interface WeatherForecaster {
        @UserMessage("Forecast the weather in {{city}}")
        @Agent(description = "Forecasts the weather", outputKey = "forecast")
        String forecast(@V("city") String city);
    }

    interface HotelFinder {
        @UserMessage("Find a hotel in {{city}} for a {{budget}} budget")
        @Agent(description = "Finds a hotel", outputKey = "hotel")
        String findHotel(@V("city") String city, @V("budget") String budget);
    }

    interface ActivityPlanner {
        @UserMessage("Suggest activities for this weather: {{forecast}}")
        @Agent(description = "Suggests activities for the weather", outputKey = "activities")
        String suggestActivities(@V("forecast") String forecast);
    }

    interface RestaurantCritic {
        @UserMessage("Review the best restaurant in {{city}}")
        @Agent(description = "Reviews restaurants", outputKey = "review")
        String reviewRestaurant(@V("city") String city);
    }

    interface ItineraryBuilder {
        @UserMessage("Build an itinerary with the hotel {{hotel}} and these activities: {{activities}}")
        @Agent(description = "Builds the itinerary", outputKey = "itinerary")
        String buildItinerary(@V("hotel") String hotel, @V("activities") String activities);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Forecast the weather in")).reply("sunny")
                    .when(userMessageContains("Find a hotel in")).reply(request -> "Hotel Central in "
                            + userMessageAfter(request, "hotel in").replaceFirst(" for a .*", ""))
                    .when(userMessageContains("Suggest activities for this weather:")).reply("a boat tour and a picnic")
                    .when(userMessageContains("Review the best restaurant in")).reply("Five stars")
                    .when(userMessageContains("Build an itinerary with the hotel"))
                    .reply(request -> "Stay at " + userMessageAfter(request, "the hotel").replace(" and these activities: ", ", enjoy "));
        }
    }
}
