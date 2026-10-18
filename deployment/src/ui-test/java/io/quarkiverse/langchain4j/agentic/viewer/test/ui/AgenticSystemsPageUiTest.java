package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.ReducedMotion;
import com.microsoft.playwright.options.WaitForSelectorState;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * The Agentic Systems page in a browser (TASK-62): system list, graph, run form validation, live highlighting and replay
 * of a past run. The Dev UI only exists in dev mode, hence Playwright driven from a QuarkusDevModeTest (quarkus-playwright
 * targets @QuarkusTest). Playwright downloads its browser on first use; run with {@code -Pui-tests}.
 */
public class AgenticSystemsPageUiTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(NewsletterWorkflow.class, NewsletterWorkflow.Researcher.class,
                            NewsletterWorkflow.Writer.class, NewsletterWorkflow.EntryPoint.class,
                            NewsletterWorkflow.SlowEchoChatModel.class, OptionsWorkflow.class,
                            OptionsWorkflow.NoteTaker.class, OptionsWorkflow.EntryPoint.class, ApprovalWorkflow.class,
                            ApprovalWorkflow.Drafter.class, ApprovalWorkflow.Approval.class, ApprovalWorkflow.EntryPoint.class,
                            ToolPlanner.class, ToolPlanner.OneShot.class, ToolPlanner.Clerk.class, ToolPlanner.Clock.class,
                            ToolPlanner.EntryPoint.class)
                    // the provider extension is only there to wire the fixture model into the AI agents
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.enable-integration=false\n"),
                            "application.properties"));

    private static final String PAGE = "http://localhost:8080/q/dev-ui/quarkus-langchain4j-agentic-viewer/agentic-systems";
    private static final String ROOT = NewsletterWorkflow.class.getName() + "#write";
    private static final String RESEARCHER = ROOT + "/Researcher";
    private static final String WRITER = ROOT + "/Writer";

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    private Page page;
    private final List<String> pageErrors = new ArrayList<>();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1500, 1000));
        context.setDefaultTimeout(60_000);
        page = context.newPage();
        page.onPageError(pageErrors::add);
    }

    @AfterEach
    void closePage() {
        context.close();
        assertEquals(List.of(), pageErrors, "no script error on the page");
    }

    @Test
    void systemList() {
        page.navigate(PAGE);

        Locator row = page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(
                Pattern.compile("^NewsletterWorkflow$")));
        assertThat(row).hasCount(1);
        assertThat(page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText("declarative")))
                .hasCount(4); // and the other fixtures (OptionsWorkflow, ApprovalWorkflow, ToolPlanner)
        assertThat(page.locator("vaadin-grid-cell-content",
                new Page.LocatorOptions().setHasText(NewsletterWorkflow.class.getName() + "#write"))).hasCount(1);
    }

    @Test
    void graphRender() {
        openSystem();

        Locator graph = graph();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> nodes = (Map<String, Map<String, Object>>) graph.evaluate("g => g.renderedNodes()");
        assertTrue(nodes.keySet().containsAll(List.of(ROOT, RESEARCHER, WRITER)), nodes::toString);
        assertEquals(ROOT, nodes.get(RESEARCHER).get("parent"));
        assertEquals(ROOT, nodes.get(WRITER).get("parent"));
        // laid out in declaration order, inside the sequence
        assertTrue(number(nodes.get(RESEARCHER), "x2") <= number(nodes.get(WRITER), "x1")
                || number(nodes.get(RESEARCHER), "y2") <= number(nodes.get(WRITER), "y1"), nodes::toString);
        assertTrue(number(nodes.get(ROOT), "x1") <= number(nodes.get(RESEARCHER), "x1")
                && number(nodes.get(WRITER), "x2") <= number(nodes.get(ROOT), "x2"), nodes::toString);
        assertThat(graph.locator("canvas").first()).isVisible();

        // node details on a tap
        graph.evaluate("(g, id) => g._cy.getElementById(id).emit('tap')", WRITER);
        assertThat(page.locator("qwc-agentic-node-details")).containsText("Writes the newsletter");
    }

    @Test
    void runFormValidation() {
        openRunForm();
        Locator sources = form().locator("vaadin-text-area[data-input=\"sources\"]");
        form().locator("vaadin-text-field[data-input=\"topic\"] input").fill("a lighthouse");

        sources.locator("textarea").fill("");
        clickRun();
        assertError(sources, "a value is required");

        // an edit clears the error until the next submit; one click is enough to submit again
        sources.locator("textarea").fill("[ \"almanac\", ");
        assertThat(sources).not().hasAttribute("invalid", "");
        clickRun();
        assertError(sources, "invalid JSON");

        sources.locator("textarea").fill("{ \"almanac\": 1 }");
        clickRun();
        Object typeError = assertError(sources, "");
        assertFalse(String.valueOf(typeError).isBlank() || String.valueOf(typeError).startsWith("invalid JSON"),
                () -> "type error: " + typeError);

        // nothing was started
        assertThat(page.locator("vaadin-dialog[opened]")).hasCount(0);
        assertThat(panel().locator("[data-run-status]")).hasCount(0);

        // once valid, the run is asked for
        sources.locator("textarea").fill("[\"almanac\"]");
        clickRun();
        page.locator("vaadin-dialog[opened]").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        assertThat(sources).not().hasAttribute("invalid", "");
        page.locator("vaadin-button[data-confirm=\"cancel\"]").click();
        assertThat(panel().locator("[data-run-status]")).hasCount(0);
    }

    /** The field is flagged and shows the message (Vaadin only shows the message of an invalid field). */
    private static Object assertError(Locator field, String messageStart) {
        assertThat(field).hasAttribute("invalid", "");
        Object message = field.evaluate("f => f.errorMessage");
        assertTrue(String.valueOf(message).startsWith(messageStart), () -> "error message: " + message);
        assertThat(field.locator("[slot=\"error-message\"]")).isVisible();
        return message;
    }

    @Test
    void liveHighlighting() {
        openRunForm();
        fill("a lighthouse");
        clickRun();
        confirmIfAsked();

        // the agents light up in turn while the run goes on
        waitFor(() -> "running".equals(stateOf(RESEARCHER)), "Researcher running");
        assertNotEquals("done", stateOf(WRITER));
        // the running agent pulses: its halo changes over time (waited for, not sampled at fixed times: slow runners)
        double halo = halo(RESEARCHER);
        assertTrue(halo > 0, "a halo around the running agent");
        waitFor(() -> halo(RESEARCHER) != halo, "pulsing halo around the running agent");
        assertThat(status()).hasAttribute("data-run-status", "running");
        // the running sequence box pulses its border
        double border = number(ROOT, "border-width");
        waitFor(() -> number(ROOT, "border-width") != border, "pulsing border of the running box");
        waitFor(() -> "running".equals(stateOf(WRITER)), "Writer running");
        assertEquals("done", stateOf(RESEARCHER));
        // the agent that just finished keeps a fading halo for a moment (fast agents would show nothing otherwise)
        assertTrue(halo(RESEARCHER) > 0, "afterglow of the agent that just finished");

        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        assertEquals("done", stateOf(RESEARCHER));
        assertEquals("done", stateOf(WRITER));
        waitFor(() -> halo(RESEARCHER) == 0.0 && halo(WRITER) == 0.0, "no halo a moment after");
        assertThat(output()).containsText("a lighthouse");
        assertTrue(page.locator("qwc-agentic-timeline .row").count() >= 3, "root and agents in the timeline");
        assertThat(page.locator("qwc-agentic-scope-state [data-key=\"facts\"]")).containsText("a lighthouse");

        // the timeline tags each row with the kind of its node
        Locator timeline = page.locator("qwc-agentic-timeline");
        Locator research = timeline.locator(".row.agent", new Locator.LocatorOptions().setHasText("research"));
        assertThat(research.locator(".kind")).hasText("AI agent");
        assertThat(timeline.locator(".row.agent").first().locator(".kind")).hasText("Sequence");
        // an agent row opens on its inputs (key by key, text as text) and its output
        research.click();
        Locator inputs = timeline.locator("[data-details-inputs]");
        assertThat(inputs.locator("qwc-agentic-value dt", new Locator.LocatorOptions().setHasText(Pattern.compile("^topic$"))))
                .hasCount(1);
        assertThat(inputs).containsText("a lighthouse");
        assertThat(inputs.locator("qwc-agentic-value li")).hasText(new String[] { "almanac" });
        assertThat(timeline.locator("[data-details-output]")).containsText("echo: Collect facts about a lighthouse from [almanac]");
        research.click();
        assertThat(timeline.locator("[data-details]")).hasCount(0);
        // the run panel leads to the Output tab
        panel().locator("vaadin-button[data-action=\"show-output\"]").click();
        assertThat(page.locator("qwc-agentic-output")).isVisible();
        assertThat(page.locator("qwc-agentic-output [data-run-output]")).containsText("echo: Write a newsletter from");
    }

    /** With reduced motion nothing moves, but the running and just-finished agents are still marked. */
    @Test
    void reducedMotionKeepsTheFeedback() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1500, 1000)
                .setReducedMotion(ReducedMotion.REDUCE));
        context.setDefaultTimeout(60_000);
        page = context.newPage();
        page.onPageError(pageErrors::add);
        openRunForm();
        fill("a harbour");
        clickRun();
        confirmIfAsked();

        waitFor(() -> "running".equals(stateOf(RESEARCHER)), "Researcher running");
        double halo = halo(RESEARCHER);
        page.waitForTimeout(300);
        assertTrue(halo > 0, "a halo around the running agent");
        assertEquals(halo, halo(RESEARCHER), "standing still");
        waitFor(() -> "running".equals(stateOf(WRITER)), "Writer running");
        assertTrue(halo(RESEARCHER) > 0, "afterglow of the agent that just finished, still");
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
    }

    @Test
    void replayOfAPastRun() {
        openRunForm();
        fill("a windmill");
        clickRun();
        confirmIfAsked();
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        panel().locator("vaadin-button[data-action=\"edit\"]").click();
        fill("a bridge");
        clickRun();
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        assertThat(output()).containsText("a bridge");

        // newest first: the second entry is the windmill run
        panel().locator("[data-history-run]").nth(1).click();
        assertReplayed("a windmill");

        // runs are kept by the application: still there after a refresh of the page
        openRunForm();
        panel().locator("[data-history-run]").nth(1).click();
        assertReplayed("a windmill");
    }

    @Test
    void editsSurviveARerenderOfThePanel() {
        openRunForm();
        fill("a windmill");
        clickRun();
        confirmIfAsked();
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        panel().locator("vaadin-button[data-action=\"edit\"]").click();
        Locator topic = form().locator("vaadin-text-field[data-input=\"topic\"] input");
        assertThat(topic).hasValue("a windmill");
        topic.fill("a harbour");

        // any reactive update of the panel renders the form again: the history refreshed, a goal path preview...
        panel().evaluate("p => { p._history = [...(p._history ?? [])]; return p.updateComplete; }");

        assertThat(topic).hasValue("a harbour");
    }

    @Test
    void optionalInputsLeftEmptyAreNotSent() {
        page.navigate(PAGE);
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(
                Pattern.compile("^OptionsWorkflow$"))).click();
        page.locator("vaadin-button[data-action=\"open-run\"]").click();
        form().locator("vaadin-text-field[data-input=\"subject\"] input").fill("the roof");

        assertThat(form().locator("vaadin-text-area[data-input=\"tags\"] textarea")).hasValue("");
        assertEquals(List.of("subject"), form().evaluate("f => Object.keys(f.collect().inputs)"));

        form().locator("vaadin-checkbox[data-input=\"urgent\"] input").check();
        assertEquals(List.of("subject", "urgent"), form().evaluate("f => Object.keys(f.collect().inputs).sort()"));
    }

    @Test
    void aCancelledRunLeavesNothingRunning() {
        openRunForm();
        fill("a mill");
        clickRun();
        confirmIfAsked();
        waitFor(() -> "running".equals(stateOf(RESEARCHER)), "researcher running");

        panel().locator("vaadin-button[data-action=\"cancel\"]").click();

        assertThat(status()).hasAttribute("data-run-status", "cancelled");
        waitFor(() -> "interrupted".equals(stateOf(RESEARCHER)), "researcher interrupted");
        assertTrue(!"running".equals(stateOf(ROOT)), "nothing running: " + stateOf(ROOT));
        Locator research = page.locator("qwc-agentic-timeline .row.agent",
                new Page.LocatorOptions().setHasText("research"));
        assertThat(research.locator(".duration")).hasText("interrupted");
    }

    @Test
    void aFailingStreamEndsTheLiveView() {
        openRunForm();
        fill("a dam");
        clickRun();
        confirmIfAsked();
        waitFor(() -> "running".equals(stateOf(RESEARCHER)), "researcher running");

        // what the Dev UI client calls when the server answers the stream with a JSON-RPC error (e.g. an evicted run)
        panel().evaluate("p => p._observer.onErrorCallback({ error: { message: 'Unknown run' } })");

        assertThat(status()).hasAttribute("data-run-status", "lost");
        assertThat(panel().locator("[data-run-error]")).containsText("Cannot follow the run");
        assertTrue(!"running".equals(stateOf(RESEARCHER)), "researcher no longer shown running");
    }

    @Test
    void aDisconnectEndsTheLiveView() {
        openRunForm();
        fill("a canal");
        clickRun();
        confirmIfAsked();
        waitFor(() -> "running".equals(stateOf(RESEARCHER)), "researcher running");

        // the Dev UI's connection state, as its client sets it when the websocket closes
        page.evaluate("async () => { const { connectionState } = await import('connection-state'); "
                + "const uri = connectionState.current.serverUri; connectionState.disconnected(uri); "
                + "connectionState.connected(uri); }");

        assertThat(status()).hasAttribute("data-run-status", "lost");
        assertThat(panel().locator("[data-run-error]")).containsText("Disconnected");
        assertTrue(!"running".equals(stateOf(RESEARCHER)), "researcher no longer shown running");
    }

    @Test
    void aLateStartResponseDoesNotReplaceTheRunOpenedSince() {
        openRunForm();
        fill("a windmill");
        clickRun();
        confirmIfAsked();
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        panel().locator("vaadin-button[data-action=\"edit\"]").click();
        // the next start answers late
        panel().evaluate("p => { const start = p.jsonRpc.startRun; "
                + "p.jsonRpc.startRun = params => new Promise(r => setTimeout(r, 1500)).then(() => start(params)); }");
        fill("a bridge");
        clickRun();
        panel().locator("[data-history-run]").first().click(); // the windmill run, while the bridge one is starting
        assertReplayed("a windmill");

        waitFor(() -> panel().locator("[data-history-run]").count() == 2, "the bridge run listed");
        page.waitForTimeout(300);
        assertReplayed("a windmill");
    }

    @Test
    void stepsWithoutInvocationIdAndToolCallsWithoutIdEnd() {
        openRunForm();
        fill("a gate");
        clickRun();
        confirmIfAsked();
        assertThat(status()).hasAttribute("data-run-status", "succeeded");

        // a waiting step known by its node only ends with the agent of its node
        assertEquals(Boolean.TRUE, panel().evaluate("p => { const live = p._live; "
                + "live.apply({ type: 'AwaitingHumanInput', nodeId: 'n', invocationId: null, timestamp: 1 }); "
                + "live.apply({ type: 'AgentFinished', nodeId: 'n', invocationId: 'inv-x', durationMillis: 1 }); "
                + "return live.pendingInput() === undefined; }"));

        // two calls of one tool without ids, in one invocation, end in order
        assertEquals(List.of("done:first", "done:second"), page.evaluate("async () => { "
                + "const { buildTimeline } = await import(location.pathname.replace(/[^/]*$/, '') + 'qwc-agentic-timeline.js'); "
                + "const tool = (type, timestamp, result) => ({ type, timestamp, invocationId: 'inv-1', toolName: 'search', result }); "
                + "const { rows } = buildTimeline([{ type: 'RunStarted', timestamp: 0 }, "
                + "{ type: 'AgentStarted', timestamp: 0, invocationId: 'inv-1', nodeId: 'a', agentName: 'a' }, "
                + "tool('ToolStarted', 1), tool('ToolStarted', 2), tool('ToolFinished', 3, 'first'), tool('ToolFinished', 4, 'second')], 10); "
                + "return rows.filter(row => row.kind === 'tool').map(row => row.status + ':' + row.result); }"));
    }

    @Test
    void aContributionWithTwoTransitionsBetweenTheSameNodesRendersBoth() {
        openSystem();
        // a planner node drawn by a topology contributor: two transitions a -> b, and a node given twice
        Object counts = page.evaluate("async () => { "
                + "const graph = document.createElement('qwc-agentic-graph'); "
                + "graph.style.cssText = 'position: fixed; top: 0; left: 0; width: 600px; height: 400px'; "
                + "document.body.appendChild(graph); "
                + "const agent = id => ({ id, name: id, kind: 'AI_AGENT', children: [] }); "
                + "graph.system = { id: 'r', name: 'r', root: { id: 'r', name: 'r', kind: 'PLANNER', plannerInfo: {}, "
                + "children: [agent('r/a'), agent('r/b')] }, contributions: { r: { "
                + "nodes: [{ id: 'r::x', label: 'x' }, { id: 'r::x', label: 'x' }], "
                + "edges: [{ source: 'r/a', target: 'r/b', label: 'approve' }, { source: 'r/a', target: 'r/b', label: 'retry' }] } } }; "
                + "for (let i = 0; i < 200 && !graph.layoutDone; i++) { await new Promise(r => setTimeout(r, 50)); } "
                + "const result = graph._cy ? [graph._cy.edges('[source = \"r/a\"][target = \"r/b\"]').length, "
                + "graph._cy.getElementById('r::x').length] : 'graph failed'; graph.remove(); return result; }");
        assertEquals(List.of(2, 1), counts);
    }

    @Test
    void theUserZoomIsKeptWhenTheGraphResizes() {
        openSystem();
        graph().evaluate("g => g._cy.zoom(2)");

        page.setViewportSize(900, 600); // the graph area shrinks, as when the timeline grows

        page.waitForTimeout(300);
        assertEquals(2.0, ((Number) graph().evaluate("g => g._cy.zoom()")).doubleValue(), 0.001);
    }

    @Test
    void aLateTopologyErrorIsNotShownOnTheNextSystem() {
        page.navigate(PAGE);
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(
                Pattern.compile("^NewsletterWorkflow$"))).waitFor();
        // the Newsletter topology fails, late; meanwhile the user opens another system
        page.locator("qwc-agentic-systems").evaluate("el => { const rpc = el.jsonRpc; "
                + "el.jsonRpc = new Proxy(rpc, { get: (target, name) => name !== 'getTopology' ? target[name] "
                + ": params => params.systemId.includes('Newsletter') "
                + "? new Promise((resolve, reject) => setTimeout(() => reject({ error: { message: 'boom' } }), 800)) "
                + ": target.getTopology(params) }); "
                + "el._open(el._systems.find(s => s.name === 'NewsletterWorkflow')); "
                + "el._open(el._systems.find(s => s.name === 'OptionsWorkflow')); }");

        waitFor(() -> Boolean.TRUE.equals(graph().evaluate("g => g.layoutDone === true")), "graph laid out");
        page.waitForTimeout(1200);
        assertThat(page.locator("qwc-agentic-systems .error")).hasCount(0);
    }

    @Test
    void aHumanStepIsAnsweredInTheRunPanel() {
        page.navigate(PAGE);
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(
                Pattern.compile("^ApprovalWorkflow$"))).click();
        waitFor(() -> Boolean.TRUE.equals(graph().evaluate("g => g.layoutDone === true")), "graph laid out");
        page.locator("vaadin-button[data-action=\"open-run\"]").click();
        form().locator("vaadin-text-field[data-input=\"subject\"] input").fill("the roof");
        clickRun();
        confirmIfAsked();

        assertThat(status()).hasAttribute("data-run-status", "waiting",
                new LocatorAssertions.HasAttributeOptions().setTimeout(30_000));
        String step = ApprovalWorkflow.class.getName() + "#approve/Approval";
        assertEquals("waiting", stateOf(step));
        assertThat(panel().locator("[data-human-prompt]")).hasText("Approve the draft?");
        assertThat(panel().locator("[data-human-inputs]")).containsText("echo: Draft a note about the roof");
        panel().locator("vaadin-text-area[data-human-answer] textarea").fill("approved in the Dev UI");
        panel().locator("vaadin-button[data-action=\"answer\"]").click();

        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        assertEquals("done", stateOf(step));
        assertThat(output()).containsText("approved in the Dev UI");
    }

    @Test
    void aFailedRunShowsTheFailingAgentAndTheError() {
        openRunForm();
        fill(NewsletterWorkflow.SlowEchoChatModel.FAILING_TOPIC);
        clickRun();
        confirmIfAsked();

        assertThat(status()).hasAttribute("data-run-status", "failed");
        assertThat(panel().locator("[data-run-failure]")).containsText("research");
        assertThat(panel().locator("[data-run-error]")).containsText("the model is down");
        waitFor(() -> "failed".equals(stateOf(RESEARCHER)), "researcher failed");
        assertNotEquals("done", stateOf(WRITER));
    }

    @Test
    void theDataFlowViewLinksTheAgentsByTheKeysTheyShare() {
        openSystem();
        graph().locator("vaadin-button[data-view=\"dataflow\"]").click();
        waitFor(() -> Boolean.TRUE.equals(graph().evaluate("g => g.layoutDone === true && g.view === 'dataflow'")),
                "data flow laid out");

        assertEquals(List.of("sources", "topic"), graph().evaluate(
                "g => g._cy.nodes().filter(n => n.data('kind') === 'INPUT_KEY').map(n => n.data('label')).sort()"));
        assertEquals("facts", graph().evaluate(
                "(g, [from, to]) => g._cy.edges().filter(e => e.source().id() === from && e.target().id() === to)"
                        + ".map(e => e.data('label')).join()", List.of(RESEARCHER, WRITER)));
    }

    @Test
    void runEventsReachTheBrowserAsPlainJson() throws Exception {
        List<String> frames = new CopyOnWriteArrayList<>();
        page.onWebSocket(socket -> socket.onFrameReceived(frame -> frames.add(frame.text())));
        page.navigate(PAGE);
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(
                Pattern.compile("^ToolPlanner$"))).click();
        waitFor(() -> Boolean.TRUE.equals(graph().evaluate("g => g.layoutDone === true")), "graph laid out");
        page.locator("vaadin-button[data-action=\"open-run\"]").click();
        form().locator("vaadin-text-field[data-input=\"question\"] input").fill("what time is it?");
        clickRun();
        confirmIfAsked();
        assertThat(status()).hasAttribute("data-run-status", "succeeded",
                new LocatorAssertions.HasAttributeOptions().setTimeout(30_000));

        // the events as the Dev UI serializer wrote them on the websocket (streamRun subscription messages)
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> firstByType = new LinkedHashMap<>();
        for (String frame : frames) {
            JsonNode message = frame == null ? null : mapper.readTree(frame);
            JsonNode event = message == null ? null : message.path("result").path("object");
            if (message != null && "SubscriptionMessage".equals(message.path("result").path("messageType").asText())
                    && event.has("type")) {
                firstByType.putIfAbsent(event.path("type").asText(), event);
            }
        }
        assertTrue(firstByType.keySet().containsAll(List.of("RunStarted", "AgentStarted", "PlannerDecision", "ToolStarted",
                "ToolFinished", "AgentFinished", "ScopeStateChanged", "RunFinished")), firstByType::toString);
        String root = ToolPlanner.class.getName() + "#ask";
        JsonNode decision = firstByType.get("PlannerDecision");
        assertEquals(root, decision.path("nodeId").asText());
        assertEquals(root + "/Clerk", decision.path("selectedNodeIds").get(0).asText());
        assertEquals("time", firstByType.get("ToolStarted").path("toolName").asText());
        assertEquals("{}", firstByType.get("ToolStarted").path("arguments").asText());
        assertEquals("noon", firstByType.get("ToolFinished").path("result").asText());
        JsonNode state = firstByType.get("ScopeStateChanged");
        assertEquals("answer", state.path("key").asText());
        // plain values, not a Jackson tree written as a bean
        assertTrue(state.path("value").isTextual(), state::toString);
        assertEquals("echo: the tool said noon", state.path("value").asText());
        assertEquals("what time is it?", firstByType.get("RunStarted").path("inputs").path("question").asText());
        assertTrue(firstByType.get("RunFinished").path("output").isTextual(), () -> firstByType.get("RunFinished").toString());
    }

    private void assertReplayed(String topic) {
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        assertThat(output()).containsText(topic);
        assertThat(page.locator("qwc-agentic-scope-state [data-key=\"topic\"]")).containsText(topic);
        waitFor(() -> "done".equals(stateOf(RESEARCHER)) && "done".equals(stateOf(WRITER)), "agents shown done");
    }

    private void openSystem() {
        page.navigate(PAGE);
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(
                Pattern.compile("^NewsletterWorkflow$"))).click();
        waitFor(() -> Boolean.TRUE.equals(graph().evaluate("g => g.layoutDone === true")), "graph laid out");
    }

    private void openRunForm() {
        openSystem();
        page.locator("vaadin-button[data-action=\"open-run\"]").click();
        assertThat(form().locator("vaadin-button", new Locator.LocatorOptions().setHasText("Run"))).isVisible();
    }

    private void fill(String topic) {
        form().locator("vaadin-text-field[data-input=\"topic\"] input").fill(topic);
        form().locator("vaadin-text-area[data-input=\"sources\"] textarea").fill("[\"almanac\"]");
    }

    private void clickRun() {
        form().locator("vaadin-button", new Locator.LocatorOptions().setHasText("Run")).click();
    }

    /** The first run of a system in a page asks for a confirmation (it calls the real models and tools). */
    private void confirmIfAsked() {
        page.locator("vaadin-dialog[opened], qwc-agentic-run-panel [data-run-status]").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        if (page.locator("vaadin-dialog[opened]").count() > 0) {
            page.locator("vaadin-button[data-confirm=\"run\"]").click();
        }
    }

    /** The opacity of a node's halo (0 without one). */
    private double halo(String nodeId) {
        return number(nodeId, "underlay-opacity");
    }

    /** A numeric style of a graph node ("4px" -> 4). */
    private double number(String nodeId, String style) {
        return Double.parseDouble(String.valueOf(graph().evaluate("(g, [id, style]) => g._cy.getElementById(id).style(style)",
                List.of(nodeId, style))).replace("px", ""));
    }

    private String stateOf(String nodeId) {
        return (String) graph().evaluate("(g, id) => g.stateOf(id)", nodeId);
    }

    private Locator graph() {
        return page.locator("qwc-agentic-graph");
    }

    private Locator panel() {
        return page.locator("qwc-agentic-run-panel");
    }

    private Locator form() {
        return page.locator("qwc-agentic-run-form");
    }

    /** The output of the run shown, in the Output tab (read without switching to it). */
    private Locator output() {
        return page.locator("qwc-agentic-output [data-run-output]");
    }

    private Locator status() {
        return panel().locator("[data-run-status]");
    }

    private static double number(Map<String, Object> box, String key) {
        return ((Number) box.get(key)).doubleValue();
    }

    private void waitFor(Supplier<Boolean> condition, String what) {
        long end = System.currentTimeMillis() + 30_000;
        while (!condition.get()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out waiting for: " + what);
            }
            page.waitForTimeout(50);
        }
    }
}
