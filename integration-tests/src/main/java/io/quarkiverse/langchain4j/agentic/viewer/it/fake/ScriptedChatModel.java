package io.quarkiverse.langchain4j.agentic.viewer.it.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Deterministic fake {@link ChatModel}: replies are scripted as ordered rules matching the request content.
 * <p>
 * The first matching rule wins. Rules are stateless on purpose: agents running in parallel call the model
 * concurrently, so a reply must depend only on the request, never on how many calls happened before.
 * Multi-step flows are scripted from what the request already contains:
 * <ul>
 * <li>tools: request a tool call, then reply once the last message is that tool's result;</li>
 * <li>supervisors: pick the next agent from the "last received response" in the supervisor prompt.</li>
 * </ul>
 * A request matching no rule fails with its content, so a missing script is obvious.
 */
public final class ScriptedChatModel implements ChatModel {

    private final List<Rule> rules;
    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

    private ScriptedChatModel(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        for (Rule rule : rules) {
            if (rule.matcher().test(request)) {
                return ChatResponse.builder().aiMessage(rule.reply().apply(request)).build();
            }
        }
        throw new IllegalStateException("No scripted reply matches the chat request:\n" + describe(request));
    }

    /**
     * @return the requests served so far, in arrival order
     */
    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    public void clearRequests() {
        requests.clear();
    }

    // ---- Matchers ----

    public static Predicate<ChatRequest> userMessageContains(String text) {
        return request -> lastUserMessage(request).contains(text);
    }

    public static Predicate<ChatRequest> systemMessageContains(String text) {
        return request -> request.messages().stream()
                .anyMatch(message -> message instanceof SystemMessage system && system.text().contains(text));
    }

    /**
     * Matches a supervisor planning request whose last received agent response contains the given text.
     * Use an empty string to match the first planning step, where no agent has answered yet.
     */
    public static Predicate<ChatRequest> supervisorLastResponseContains(String text) {
        return request -> {
            String userMessage = lastUserMessage(request);
            String marker = "The last received response is: '";
            int start = userMessage.indexOf(marker);
            if (start < 0) {
                return false;
            }
            int from = start + marker.length();
            int end = userMessage.lastIndexOf('\'');
            String lastResponse = end >= from ? userMessage.substring(from, end) : "";
            return text.isEmpty() ? lastResponse.isEmpty() : lastResponse.contains(text);
        };
    }

    public static Predicate<ChatRequest> lastMessageIsToolResult() {
        return request -> lastMessage(request) instanceof ToolExecutionResultMessage;
    }

    public static Predicate<ChatRequest> lastMessageIsToolResult(String toolName) {
        return request -> lastMessage(request) instanceof ToolExecutionResultMessage result
                && toolName.equals(result.toolName());
    }

    // ---- Request helpers, usable in computed replies ----

    public static String lastUserMessage(ChatRequest request) {
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            if (request.messages().get(i) instanceof UserMessage user) {
                return text(user);
            }
        }
        return "";
    }

    /**
     * @return the rest of the line following the given marker in the last user message (e.g. the value of a
     *         prompt variable), or an empty string if the marker is absent. Only that line is returned because
     *         langchain4j appends output format instructions (enum values, JSON schema...) on the next lines.
     */
    public static String userMessageAfter(ChatRequest request, String marker) {
        String userMessage = lastUserMessage(request);
        int index = userMessage.indexOf(marker);
        if (index < 0) {
            return "";
        }
        String rest = userMessage.substring(index + marker.length());
        int lineEnd = rest.indexOf('\n');
        return (lineEnd < 0 ? rest : rest.substring(0, lineEnd)).trim();
    }

    public static String lastToolResult(ChatRequest request) {
        return lastMessage(request) instanceof ToolExecutionResultMessage result ? result.text() : "";
    }

    private static ChatMessage lastMessage(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    private static String text(UserMessage user) {
        if (user.hasSingleText()) {
            return user.singleText();
        }
        return user.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }

    private static String describe(ChatRequest request) {
        return request.messages().stream()
                .map(message -> "  [" + message.type() + "] " + switch (message) {
                    case UserMessage user -> text(user);
                    case SystemMessage system -> system.text();
                    case ToolExecutionResultMessage result -> result.toolName() + " -> " + result.text();
                    case AiMessage ai -> ai.hasToolExecutionRequests() ? ai.toolExecutionRequests().toString() : ai.text();
                    default -> message.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private record Rule(Predicate<ChatRequest> matcher, Function<ChatRequest, AiMessage> reply) {
    }

    public static final class Builder {

        private final List<Rule> rules = new ArrayList<>();

        private Builder() {
        }

        public When when(Predicate<ChatRequest> matcher) {
            return new When(this, matcher);
        }

        public ScriptedChatModel build() {
            return new ScriptedChatModel(rules);
        }
    }

    public static final class When {

        private final Builder builder;
        private final Predicate<ChatRequest> matcher;

        private When(Builder builder, Predicate<ChatRequest> matcher) {
            this.builder = builder;
            this.matcher = matcher;
        }

        public Builder reply(String text) {
            return reply(request -> text);
        }

        public Builder reply(Function<ChatRequest, String> text) {
            return add(request -> AiMessage.from(text.apply(request)));
        }

        /**
         * Asks the agent to execute a tool; script the follow-up with {@link #lastMessageIsToolResult(String)}.
         */
        public Builder callTool(String toolName, String argumentsJson) {
            return add(request -> AiMessage.from(ToolExecutionRequest.builder()
                    .id("call-" + toolName)
                    .name(toolName)
                    .arguments(argumentsJson)
                    .build()));
        }

        /**
         * Supervisor decision: invoke the given sub-agent with the given arguments (a JSON object).
         */
        public Builder invokeAgent(String agentName, String argumentsJson) {
            return reply("{\"agentName\": " + jsonString(agentName) + ", \"arguments\": " + argumentsJson + "}");
        }

        /**
         * Supervisor decision: no further agent is needed.
         */
        public Builder finishSupervisor(String response) {
            return invokeAgent("done", "{\"response\": " + jsonString(response) + "}");
        }

        private Builder add(Function<ChatRequest, AiMessage> reply) {
            builder.rules.add(new Rule(matcher, reply));
            return builder;
        }
    }
}
