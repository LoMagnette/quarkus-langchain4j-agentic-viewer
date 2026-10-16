package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.ListenerNotifierUtil;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;

/**
 * Lets the Dev UI answer the {@code @HumanInTheLoop} steps of its runs (T2.11; upstream has no standard way yet, U4).
 * <p>
 * A declarative human-in-the-loop step is an {@link AgentExecutor} whose invoker calls the annotated method. Each one
 * is replaced in its workflow by an executor whose invoker, in a Dev UI run, waits for the answer given in the Dev UI
 * instead of calling the method; anywhere else it calls the method as before.
 * <p>
 * Internal API (the sub-agent lists of {@code AbstractServiceBuilder} and {@code PlannerBasedInvocationHandler},
 * {@link AgentTrees}), isolated here: if it changes, steps are left as they are and Dev UI runs call the annotated methods.
 */
final class HumanInTheLoopSteps {

    private static final Logger LOG = Logger.getLogger(HumanInTheLoopSteps.class);

    private HumanInTheLoopSteps() {
    }

    /**
     * Intercepts the human-in-the-loop steps of a root agent and of all the workflows under it.
     *
     * @return the number of steps intercepted
     */
    static int intercept(Object root, DevUiAgentListener listener) {
        try {
            int intercepted = 0;
            for (Object handler : AgentTrees.handlers(root)) {
                intercepted += interceptSteps(handler, listener);
            }
            return intercepted;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warnf(e, "Agentic viewer: cannot intercept the human-in-the-loop steps of %s, Dev UI runs will call them",
                    root.getClass().getName());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static int interceptSteps(Object handler, DevUiAgentListener listener) throws ReflectiveOperationException {
        // per-scope copies of a workflow take its builder's list; root calls use the workflow's own copy
        List<Object> serviceAgents = AgentTrees.serviceSubagents(handler);
        Map<Object, Object> replaced = new IdentityHashMap<>();
        for (int i = 0; i < serviceAgents.size(); i++) {
            if (serviceAgents.get(i) instanceof AgentExecutor executor && executor.agent() instanceof HumanInTheLoop step) {
                AgentExecutor wrapped = new AgentExecutor(new DevUiStep(executor.agentInvoker(), step, listener), step);
                serviceAgents.set(i, wrapped);
                replaced.put(executor, wrapped);
            }
        }
        if (!replaced.isEmpty()) {
            Field own = AgentTrees.field(handler.getClass(), "subagents");
            List<Object> agents = new ArrayList<>((List<Object>) own.get(handler));
            agents.replaceAll(subagent -> replaced.getOrDefault(subagent, subagent));
            own.set(handler, List.copyOf(agents));
        }
        return replaced.size();
    }

    /**
     * The invoker of an intercepted step: everything is the original invoker's, except an invocation in a Dev UI run
     * of the listener's manager, which notifies the listeners as the original does and waits for the Dev UI's answer.
     */
    static final class DevUiStep implements AgentInvoker {

        private final AgentInvoker delegate;
        private final HumanInTheLoop step;
        private final DevUiAgentListener listener;

        DevUiStep(AgentInvoker delegate, HumanInTheLoop step, DevUiAgentListener listener) {
            this.delegate = delegate;
            this.step = step;
            this.listener = listener;
        }

        @Override
        public Object invoke(DefaultAgenticScope scope, Object agent, AgentInvocationArguments arguments)
                throws AgentInvocationException {
            Run run = listener.run(scope);
            if (run == null) {
                return delegate.invoke(scope, agent, arguments);
            }
            AgentListener agentListener = delegate.listener();
            Map<String, Object> inputs = arguments.namedArgs();
            ListenerNotifierUtil.beforeAgentInvocation(agentListener, scope, delegate, inputs);
            Object answer;
            try {
                answer = listener.awaitHumanInput(run, delegate, step.description(), step.outputKey(),
                        delegate.method().getGenericReturnType(), inputs);
            } catch (RuntimeException e) {
                ListenerNotifierUtil.agentError(agentListener, scope, delegate, inputs, e);
                throw new AgentInvocationException("Human input of " + delegate.name() + " not given", e);
            }
            ListenerNotifierUtil.afterAgentInvocation(agentListener, scope, delegate, inputs, answer);
            return answer;
        }

        @Override
        public Method method() {
            return delegate.method();
        }

        @Override
        public AgentInvocationArguments toInvocationArguments(AgenticScope scope) throws MissingArgumentException {
            return delegate.toInvocationArguments(scope);
        }

        @Override
        public Class<?> type() {
            return delegate.type();
        }

        @Override
        public Class<? extends Planner> plannerType() {
            return delegate.plannerType();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String agentId() {
            return delegate.agentId();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public Type outputType() {
            return delegate.outputType();
        }

        @Override
        public String outputKey() {
            return delegate.outputKey();
        }

        @Override
        public boolean async() {
            return delegate.async();
        }

        @Override
        public boolean optional() {
            return delegate.optional();
        }

        @Override
        public List<AgentArgument> arguments() {
            return delegate.arguments();
        }

        @Override
        public AgentInstance parent() {
            return delegate.parent();
        }

        @Override
        public List<AgentInstance> subagents() {
            return delegate.subagents();
        }

        @Override
        public boolean leaf() {
            return delegate.leaf();
        }

        @Override
        public AgenticSystemTopology topology() {
            return delegate.topology();
        }

        @Override
        public <T extends AgentInstance> T as(Class<T> type) {
            return delegate.as(type);
        }

        @Override
        public void setParent(InternalAgent parent) {
            delegate.setParent(parent);
        }

        @Override
        public void registerInheritedParentListener(AgentListener parentListener) {
            delegate.registerInheritedParentListener(parentListener);
        }

        @Override
        public void appendId(String idSuffix) {
            delegate.appendId(idSuffix);
        }

        @Override
        public void setAgentId(String agentId) {
            delegate.setAgentId(agentId);
        }

        @Override
        public AgentListener listener() {
            return delegate.listener();
        }

        @Override
        public boolean allowStreamingOutput() {
            return delegate.allowStreamingOutput();
        }

        @Override
        public boolean allowChatMemory() {
            return delegate.allowChatMemory();
        }

        @Override
        public boolean compensateOnError() {
            return delegate.compensateOnError();
        }

        @Override
        public void enableCrossAgentCompensation() {
            delegate.enableCrossAgentCompensation();
        }

        @Override
        public String toString() {
            return "DevUiStep[" + delegate + "]";
        }
    }
}
