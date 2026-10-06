package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastMessageIsToolResult;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastToolResult;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.supervisorLastResponseContains;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.systemMessageContains;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Supervisor: an LLM-driven planner picks the banking agents to call, here exchange, withdraw, then credit.
 * The agents use CDI tool beans.
 */
public interface BankSupervisor {

    @SupervisorAgent(description = "Performs banking operations", outputKey = "result", responseStrategy = SupervisorResponseStrategy.SUMMARY, subAgents = {
            ExchangeAgent.class, WithdrawAgent.class, CreditAgent.class })
    String transact(@V("request") String request);

    interface ExchangeAgent {
        @SystemMessage("You are an operator exchanging money in different currencies.")
        @UserMessage("Convert {{amount}} {{originalCurrency}} to {{targetCurrency}}.")
        @Agent(description = "Converts an amount of money from a currency to another", outputKey = "amountInUSD")
        @ToolBox(ExchangeTool.class)
        double exchange(@V("originalCurrency") String originalCurrency, @V("amount") double amount,
                @V("targetCurrency") String targetCurrency);
    }

    interface WithdrawAgent {
        @SystemMessage("You are a banker that can only withdraw US dollars (USD) from a user account.")
        @UserMessage("Withdraw {{amountInUSD}} USD from {{withdrawUser}}'s account.")
        @Agent(description = "A banker that withdraws USD from an account", outputKey = "withdrawn")
        @ToolBox(BankTool.class)
        String withdraw(@V("withdrawUser") String withdrawUser, @V("amountInUSD") double amountInUSD);
    }

    interface CreditAgent {
        @SystemMessage("You are a banker that can only credit US dollars (USD) to a user account.")
        @UserMessage("Credit {{amountInUSD}} USD to {{creditUser}}'s account.")
        @Agent(description = "A banker that credits USD to an account", outputKey = "credited")
        @ToolBox(BankTool.class)
        String credit(@V("creditUser") String creditUser, @V("amountInUSD") double amountInUSD);
    }

    @ApplicationScoped
    class BankTool {

        private final Map<String, Double> accounts = new ConcurrentHashMap<>();

        public BankTool() {
            reset();
        }

        public void reset() {
            accounts.clear();
            accounts.put("Mario", 1000.0);
            accounts.put("Georgios", 1000.0);
        }

        public double balance(String user) {
            return accounts.get(user);
        }

        @Tool("Withdraws the given amount in USD from the given user's account and returns the new balance")
        public double withdraw(String user, double amount) {
            return accounts.compute(user, (name, balance) -> balance - amount);
        }

        @Tool("Credits the given amount in USD to the given user's account and returns the new balance")
        public double credit(String user, double amount) {
            return accounts.compute(user, (name, balance) -> balance + amount);
        }
    }

    @ApplicationScoped
    class ExchangeTool {
        @Tool("Exchanges the given amount of money from the original to the target currency")
        public double exchange(String originalCurrency, double amount, String targetCurrency) {
            double converted = "EUR".equals(originalCurrency) && "USD".equals(targetCurrency) ? amount * 1.15 : amount;
            return Math.round(converted * 100) / 100.0;
        }
    }

    @ApplicationScoped
    class Script implements ChatModelScript {

        private static final String REQUEST = "Transfer 100 EUR from Mario's account to Georgios' one";

        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script
                    // supervisor decisions, only for this sample's request; most specific last response first
                    .when(planning("Credited")).finishSupervisor("Transferred 100 EUR (115.0 USD) from Mario to Georgios.")
                    .when(planning("Withdrew")).invokeAgent("credit", "{\"creditUser\": \"Georgios\", \"amountInUSD\": 115.0}")
                    .when(planning("115")).invokeAgent("withdraw", "{\"withdrawUser\": \"Mario\", \"amountInUSD\": 115.0}")
                    .when(planning("")).invokeAgent("exchange",
                            "{\"originalCurrency\": \"EUR\", \"amount\": 100, \"targetCurrency\": \"USD\"}")
                    // agents (told apart by their system message): call their tool, then answer from the tool result
                    .when(agent("exchanging money").and(lastMessageIsToolResult("exchange")))
                    .reply(request -> lastToolResult(request))
                    .when(agent("exchanging money")).callTool("exchange",
                            "{\"originalCurrency\": \"EUR\", \"amount\": 100, \"targetCurrency\": \"USD\"}")
                    .when(agent("withdraw US dollars").and(lastMessageIsToolResult("withdraw")))
                    .reply(request -> "Withdrew 115.0 USD from Mario, new balance " + lastToolResult(request))
                    .when(agent("withdraw US dollars")).callTool("withdraw", "{\"user\": \"Mario\", \"amount\": 115.0}")
                    .when(agent("credit US dollars").and(lastMessageIsToolResult("credit")))
                    .reply(request -> "Credited 115.0 USD to Georgios, new balance " + lastToolResult(request))
                    .when(agent("credit US dollars")).callTool("credit", "{\"user\": \"Georgios\", \"amount\": 115.0}");
        }

        private static Predicate<ChatRequest> agent(String systemMessageText) {
            return systemMessageContains(systemMessageText);
        }

        private static Predicate<ChatRequest> planning(String lastResponse) {
            return supervisorLastResponseContains(lastResponse).and(userMessageContains(REQUEST));
        }

        public static String request() {
            return REQUEST;
        }
    }
}
