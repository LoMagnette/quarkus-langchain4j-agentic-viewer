package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import io.quarkus.runtime.annotations.RecordableConstructor;

/**
 * A condition guarding part of a workflow.
 * <ul>
 * <li>On a {@link NodeKind#LOOP} node: its exit condition, with {@code maxIterations} and {@code testExitAtLoopEnd}.</li>
 * <li>On a branch (child) of a {@link NodeKind#CONDITIONAL} node: its activation condition; {@code maxIterations}
 * and {@code testExitAtLoopEnd} are null.</li>
 * <li>On a loop that is a branch of a conditional: its exit condition, and its activation in {@code activation}.</li>
 * </ul>
 *
 * @param label method name or description of the condition for declarative systems, {@code "lambda"} for builder ones
 * @param maxIterations maximum number of loop iterations, null for activation conditions
 * @param testExitAtLoopEnd whether the exit condition is only tested at the end of an iteration, null for activation
 *        conditions
 * @param activation the activation condition of a loop that is a conditional branch, null otherwise
 */
public record Guard(String label, Integer maxIterations, Boolean testExitAtLoopEnd, String activation) {

    /** The one the build-time recorder uses (a record with several constructors needs it told). */
    @RecordableConstructor
    public Guard {
    }

    public Guard(String label, Integer maxIterations, Boolean testExitAtLoopEnd) {
        this(label, maxIterations, testExitAtLoopEnd, null);
    }

    /**
     * @return this loop exit condition with the activation condition of the branch the loop is, unchanged for null
     */
    public Guard withActivation(Guard branch) {
        return branch == null ? this : new Guard(label, maxIterations, testExitAtLoopEnd, branch.label());
    }

    public static Guard loopExit(String label, int maxIterations, boolean testExitAtLoopEnd) {
        return new Guard(label, maxIterations, testExitAtLoopEnd);
    }

    public static Guard activation(String label) {
        return new Guard(label, null, null);
    }
}
