package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * T6.3 (TASK-61): render time and live-view responsiveness of a generated 50-node system. Prints the measures; the
 * bounds are loose, to catch a regression by an order of magnitude, not to benchmark the machine.
 */
public class LargeSystemUiTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(LargeSystem.class, NewsletterWorkflow.SlowEchoChatModel.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.enable-integration=false\n"),
                            "application.properties"));

    private static final String PAGE = "http://localhost:8080/q/dev-ui/quarkus-langchain4j-agentic-viewer/agentic-systems";

    private static Playwright playwright;
    private static Browser browser;

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

    @Test
    void fiftyNodeSystem() {
        Page page = browser.newContext(new Browser.NewContextOptions().setViewportSize(1500, 1000)).newPage();
        page.setDefaultTimeout(60_000);
        List<String> errors = new java.util.ArrayList<>();
        page.onPageError(errors::add);
        page.navigate(PAGE);
        // long tasks of the main thread (Chromium): what makes the page unresponsive
        page.evaluate("() => { window.__longTasks = []; new PerformanceObserver(list => list.getEntries()"
                + ".forEach(e => window.__longTasks.push(e.duration))).observe({ type: 'longtask', buffered: true }); }");
        Locator graph = page.locator("qwc-agentic-graph");

        long start = System.nanoTime();
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(Pattern.compile("^large$"))).click();
        page.waitForFunction("() => { const find = root => root.querySelector('qwc-agentic-graph') ?? "
                + "[...root.querySelectorAll('*')].map(e => e.shadowRoot && find(e.shadowRoot)).find(Boolean);"
                + " return find(document)?.layoutDone === true; }");
        long renderMillis = (System.nanoTime() - start) / 1_000_000;
        int nodes = ((Number) graph.evaluate("g => g._cy.nodes().filter(n => !n.data('helper')).length")).intValue();
        double renderLongest = longest(page);

        page.locator("vaadin-button[data-action=\"open-run\"]").click();
        page.evaluate("() => window.__longTasks = []");
        long runStart = System.nanoTime();
        page.locator("qwc-agentic-run-form vaadin-button", new Page.LocatorOptions().setHasText("Run")).click();
        // the first run of a system may ask for a confirmation
        page.locator("vaadin-dialog[opened], qwc-agentic-run-panel [data-run-status]").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        if (page.locator("vaadin-dialog[opened]").count() > 0) {
            page.locator("vaadin-button[data-confirm=\"run\"]").click();
        }
        assertThat(page.locator("qwc-agentic-run-panel [data-run-status]")).hasAttribute("data-run-status", "succeeded");
        long runMillis = (System.nanoTime() - runStart) / 1_000_000;
        double runLongest = longest(page);
        int done = ((Number) graph.evaluate("g => g._cy.nodes().filter(n => g.stateOf(n.id()) === 'done').length")).intValue();
        int rows = ((Number) page.locator("qwc-agentic-timeline").evaluate("t => t.shadowRoot.querySelectorAll('.row').length")).intValue();

        System.out.printf("%nLARGE SYSTEM (%d nodes)%n  render: %d ms (longest main-thread task %.0f ms)%n"
                + "  run: %d ms from Run to succeeded (longest main-thread task %.0f ms), %d nodes done, %d timeline rows%n",
                nodes, renderMillis, renderLongest, runMillis, runLongest, done, rows);

        assertEquals(LargeSystem.NODES, nodes);
        assertEquals(LargeSystem.NODES, done, "every node lit as done at the end");
        assertTrue(renderMillis < budget("render", 10_000), "render under the budget: " + renderMillis);
        assertTrue(runLongest < budget("task", 1_000), "the page stays responsive during the run: " + runLongest);
        assertEquals(List.of(), errors);
    }

    @Test
    void longRun() {
        Page page = browser.newContext(new Browser.NewContextOptions().setViewportSize(1500, 1000)).newPage();
        page.setDefaultTimeout(60_000);
        List<String> errors = new java.util.ArrayList<>();
        page.onPageError(errors::add);
        page.navigate(PAGE);
        page.evaluate("() => { window.__longTasks = []; new PerformanceObserver(list => list.getEntries()"
                + ".forEach(e => window.__longTasks.push(e.duration))).observe({ type: 'longtask', buffered: true }); }");
        page.locator("vaadin-grid-cell-content", new Page.LocatorOptions().setHasText(Pattern.compile("^chatty$"))).click();
        page.locator("vaadin-button[data-action=\"open-run\"]").click();
        page.evaluate("() => window.__longTasks = []");

        long runStart = System.nanoTime();
        page.locator("qwc-agentic-run-form vaadin-button", new Page.LocatorOptions().setHasText("Run")).click();
        page.locator("vaadin-dialog[opened], qwc-agentic-run-panel [data-run-status]").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        if (page.locator("vaadin-dialog[opened]").count() > 0) {
            page.locator("vaadin-button[data-confirm=\"run\"]").click();
        }
        assertThat(page.locator("qwc-agentic-run-panel [data-run-status]")).hasAttribute("data-run-status", "succeeded",
                new LocatorAssertions.HasAttributeOptions().setTimeout(60_000));
        page.waitForFunction("n => { const find = root => root.querySelector('qwc-agentic-timeline') ?? "
                + "[...root.querySelectorAll('*')].map(e => e.shadowRoot && find(e.shadowRoot)).find(Boolean);"
                + " return find(document)?.shadowRoot.querySelectorAll('.row.agent').length > n; }", LargeSystem.CHATTY_ITERATIONS);
        long runMillis = (System.nanoTime() - runStart) / 1_000_000;
        double runLongest = longest(page);
        System.out.printf("%nLONG RUN (%d iterations, %d events)%n  %d ms from Run to every row shown (longest main-thread "
                + "task %.0f ms)%n", LargeSystem.CHATTY_ITERATIONS, 2 * LargeSystem.CHATTY_ITERATIONS + 4, runMillis, runLongest);

        assertTrue(runMillis < budget("long-run", 20_000), "followed within the budget: " + runMillis);
        assertTrue(runLongest < budget("task", 1_000), "the page stays responsive during the run: " + runLongest);
        assertEquals(List.of(), errors);
    }

    /**
     * A time budget in ms, generous for shared CI runners (measured locally: render 0.5 s, long run 3 s, longest
     * main-thread task under 100 ms); {@code -Dui-budget.<name>=<ms>} overrides it on a slower machine.
     */
    private static long budget(String name, long millis) {
        return Long.getLong("ui-budget." + name, millis);
    }

    private static double longest(Page page) {
        return ((Number) page.evaluate("() => Math.max(0, ...window.__longTasks)")).doubleValue();
    }
}
