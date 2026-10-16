package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Field;
import java.util.function.Function;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Observes the error handling of a run. langchain4j applies the {@code errorHandler} of the AgenticScope
 * ({@code DefaultAgenticScope.handleError}) when an agent fails, including before the agent is invoked (a missing
 * argument), which listeners never see; every workflow sets that handler on the scope when it starts.
 * <p>
 * Internal API (a private field of DefaultAgenticScope), isolated here: if it changes, errors are simply not observed.
 */
final class ErrorHandlers {

    private static final Logger LOG = Logger.getLogger(ErrorHandlers.class);
    private static final Field HANDLER = handlerField();
    private static volatile boolean warned;

    /** What an observed handler reports: the context, what the handler decided and the original error if it threw. */
    interface Observer {
        void handled(ErrorContext context, ErrorRecoveryResult result, Throwable handlerFailure);
    }

    /** Wraps the current handler of the scope, unless it already is. */
    private static final class Observed implements Function<ErrorContext, ErrorRecoveryResult> {
        final Function<ErrorContext, ErrorRecoveryResult> delegate;
        final Observer observer;

        Observed(Function<ErrorContext, ErrorRecoveryResult> delegate, Observer observer) {
            this.delegate = delegate;
            this.observer = observer;
        }

        @Override
        public ErrorRecoveryResult apply(ErrorContext context) {
            ErrorRecoveryResult result;
            try {
                result = delegate.apply(context);
            } catch (RuntimeException e) {
                observer.handled(context, ErrorRecoveryResult.throwException(), e);
                throw e;
            }
            observer.handled(context, result, null);
            return result;
        }
    }

    private ErrorHandlers() {
    }

    private static Field handlerField() {
        try {
            Field field = Class.forName("dev.langchain4j.agentic.scope.DefaultAgenticScope").getDeclaredField("errorHandler");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debugf(e, "Agentic viewer: error handlers cannot be observed");
            return null;
        }
    }

    /**
     * Makes sure the scope's error handler reports to the observer (a workflow may have set its own since).
     */
    @SuppressWarnings("unchecked")
    static void observe(AgenticScope scope, Observer observer) {
        if (HANDLER == null || scope == null || !HANDLER.getDeclaringClass().isInstance(scope)) {
            return;
        }
        try {
            Object current = HANDLER.get(scope);
            if (current instanceof Observed observed && observed.observer == observer) {
                return;
            }
            if (current instanceof Function<?, ?> handler) {
                HANDLER.set(scope, new Observed((Function<ErrorContext, ErrorRecoveryResult>) handler, observer));
            }
        } catch (IllegalAccessException | RuntimeException e) {
            if (!warned) {
                warned = true;
                LOG.warnf(e, "Agentic viewer: cannot observe the error handler of a run, recovered errors will not show");
            }
        }
    }
}
