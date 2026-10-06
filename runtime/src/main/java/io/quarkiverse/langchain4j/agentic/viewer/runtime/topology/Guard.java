package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * A condition guarding part of a workflow.
 * <ul>
 * <li>On a {@link NodeKind#LOOP} node: its exit condition, with {@code maxIterations} and {@code testExitAtLoopEnd}.</li>
 * <li>On a branch (child) of a {@link NodeKind#CONDITIONAL} node: its activation condition; {@code maxIterations}
 * and {@code testExitAtLoopEnd} are null.</li>
 * </ul>
 *
 * @param label method name or description of the condition for declarative systems, {@code "lambda"} for builder ones
 * @param maxIterations maximum number of loop iterations, null for activation conditions
 * @param testExitAtLoopEnd whether the exit condition is only tested at the end of an iteration, null for activation
 *        conditions
 */
public record Guard(String label, Integer maxIterations, Boolean testExitAtLoopEnd) {

    public static Guard loopExit(String label, int maxIterations, boolean testExitAtLoopEnd) {
        return new Guard(label, maxIterations, testExitAtLoopEnd);
    }

    public static Guard activation(String label) {
        return new Guard(label, null, null);
    }
}
