package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * An exception, as shown in the Dev UI.
 *
 * @param exceptionClass fully qualified class name of the exception
 * @param message exception message, null when none
 * @param stackTrace stack trace text, causes included
 */
public record ErrorInfo(String exceptionClass, String message, String stackTrace) {

    public static ErrorInfo of(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        return new ErrorInfo(error.getClass().getName(), error.getMessage(), trace.toString());
    }
}
