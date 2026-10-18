package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * An exception, as shown in the Dev UI.
 *
 * @param exceptionClass fully qualified class name of the exception
 * @param message exception message, null when none
 * @param stackTrace stack trace text, causes included
 * @param rootCauseClass class of the deepest cause, null when the exception has no cause: langchain4j wraps what an
 *        agent threw (AgentInvocationException), the cause tells what went wrong
 * @param rootCauseMessage message of the deepest cause, null when none
 */
public record ErrorInfo(String exceptionClass, String message, String stackTrace, String rootCauseClass,
        String rootCauseMessage) {

    public ErrorInfo(String exceptionClass, String message, String stackTrace) {
        this(exceptionClass, message, stackTrace, null, null);
    }

    public static ErrorInfo of(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        Throwable root = error;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 20; depth++) {
            root = root.getCause();
        }
        return root == error ? new ErrorInfo(error.getClass().getName(), error.getMessage(), trace.toString())
                : new ErrorInfo(error.getClass().getName(), error.getMessage(), trace.toString(), root.getClass().getName(),
                        root.getMessage());
    }
}
