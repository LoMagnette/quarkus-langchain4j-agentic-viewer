package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
                            NewsletterWorkflow.SlowEchoChatModel.class)
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
                .hasCount(1);
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
        assertThat(status()).hasAttribute("data-run-status", "running");
        waitFor(() -> "running".equals(stateOf(WRITER)), "Writer running");
        assertEquals("done", stateOf(RESEARCHER));

        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        assertEquals("done", stateOf(RESEARCHER));
        assertEquals("done", stateOf(WRITER));
        assertThat(panel().locator("[data-run-output]")).containsText("a lighthouse");
        assertTrue(page.locator("qwc-agentic-timeline .row").count() >= 3, "root and agents in the timeline");
        assertThat(page.locator("qwc-agentic-scope-state [data-key=\"facts\"]")).containsText("a lighthouse");
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
        assertThat(panel().locator("[data-run-output]")).containsText("a bridge");

        // newest first: the second entry is the windmill run
        panel().locator("[data-history-run]").nth(1).click();
        assertReplayed("a windmill");

        // runs are kept by the application: still there after a refresh of the page
        openRunForm();
        panel().locator("[data-history-run]").nth(1).click();
        assertReplayed("a windmill");
    }

    private void assertReplayed(String topic) {
        assertThat(status()).hasAttribute("data-run-status", "succeeded");
        assertThat(panel().locator("[data-run-output]")).containsText(topic);
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
