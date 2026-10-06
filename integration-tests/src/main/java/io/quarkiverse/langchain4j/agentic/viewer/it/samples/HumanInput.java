package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * Where {@code @HumanInTheLoop} steps of the samples get their answer from.
 * <p>
 * Placeholder until the Dev UI can answer pending human steps (T2.11, upstream U4): by default the human
 * approves, so samples complete unattended; tests can plug in other answers.
 */
public final class HumanInput {

    public static final String DEFAULT_ANSWER = "Yes, sounds great";

    private static volatile UnaryOperator<String> responder = prompt -> DEFAULT_ANSWER;
    private static final List<String> PROMPTS = new CopyOnWriteArrayList<>();

    private HumanInput() {
    }

    public static String ask(String prompt) {
        PROMPTS.add(prompt);
        return responder.apply(prompt);
    }

    public static void answerWith(UnaryOperator<String> newResponder) {
        responder = newResponder;
    }

    /**
     * @return the prompts asked so far
     */
    public static List<String> prompts() {
        return List.copyOf(PROMPTS);
    }

    public static void reset() {
        responder = prompt -> DEFAULT_ANSWER;
        PROMPTS.clear();
    }
}
