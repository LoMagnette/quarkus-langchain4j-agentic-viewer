package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;

/**
 * Human-in-the-loop steps of Dev UI runs wait for the Dev UI's answer: tests that are not about them answer at once.
 */
final class HumanInputAnswers {

    private HumanInputAnswers() {
    }

    /** Answers every step of the manager's runs with {@link HumanInput#DEFAULT_ANSWER} as soon as it waits. */
    static void answerInTheDevUi(RunManager manager) {
        manager.addListener(event -> {
            if (event instanceof AwaitingHumanInput waiting) {
                manager.provideHumanInput(waiting.runId(), HumanInput.DEFAULT_ANSWER);
            }
        });
    }
}
