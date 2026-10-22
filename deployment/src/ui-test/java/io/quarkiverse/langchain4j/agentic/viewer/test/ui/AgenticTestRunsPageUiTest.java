package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.WaitForSelectorState;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * The Agentic Test Runs page (TASK-98): a system run N times with the same inputs, one run after the other, followed
 * live, then how the results differ; a run replays on the graph, two compare, a series stops.
 */
public class AgenticTestRunsPageUiTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(NewsletterWorkflow.class, NewsletterWorkflow.Researcher.class,
                            NewsletterWorkflow.Writer.class, NewsletterWorkflow.EntryPoint.class,
                            NewsletterWorkflow.SlowEchoChatModel.class, ApprovalWorkflow.class,
                            ApprovalWorkflow.Drafter.class, ApprovalWorkflow.Approval.class, ApprovalWorkflow.EntryPoint.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.enable-integration=false\n"),
                            "application.properties"));

    private static final String PAGE = "http://localhost:8080/q/dev-ui/quarkus-langchain4j-agentic-viewer/agentic-test-runs";
    private static final String NEWSLETTER = NewsletterWorkflow.class.getName() + "#write";
    private static final String APPROVAL = ApprovalWorkflow.class.getName() + "#approve";

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
    void aSeriesRunsTheSystemNTimesAndShowsHowTheResultsDiffer() {
        openSystem(NEWSLETTER);
        count(3);
        testRuns().locator("vaadin-text-field[data-input=\"topic\"] input").fill("a lighthouse");
        testRuns().locator("vaadin-text-area[data-input=\"sources\"] textarea").fill("[\"almanac\"]");
        assertThat(submit()).hasText("Run 3 times");
        submit().click();
        confirm();

        // followed live: the run going on lights up the graph
        assertThat(testRuns().locator("[data-series-status]")).hasAttribute("data-series-status", "RUNNING");
        waitFor(() -> "running".equals(stateOf(NEWSLETTER + "/Researcher")), "Researcher running");
        assertThat(testRuns().locator("[data-series-status]")).hasAttribute("data-series-status", "SUCCEEDED",
                new LocatorAssertions.HasAttributeOptions().setTimeout(60_000));

        Locator runs = testRuns().locator("[data-series-run]");
        assertThat(runs).hasCount(3);
        assertThat(testRuns().locator("[data-series-summary]")).containsText("3/3");
        assertThat(testRuns().locator("[data-distinct-output-count]")).hasText("1");
        assertThat(testRuns().locator("[data-distinct-path-count]")).hasText("1");
        assertThat(testRuns().locator("[data-output-group]")).hasAttribute("data-output-group", "3");
        assertThat(testRuns().locator("[data-distinct-paths] td").nth(2)).hasText("research → write");

        // a run replays on the graph and the timeline
        runs.nth(1).click();
        assertThat(testRuns().locator("[data-shown-run]")).hasText("Run #2");
        waitFor(() -> "done".equals(stateOf(NEWSLETTER + "/Writer")), "the run replayed");
        assertTrue(testRuns().locator("qwc-agentic-timeline .row").count() >= 3);

        // two runs compare
        testRuns().locator("input[data-compare-run=\"1\"]").click();
        testRuns().locator("input[data-compare-run=\"3\"]").click();
        testRuns().locator("vaadin-button[data-action=\"compare-series-runs\"]").click();
        Locator compare = page.locator("qwc-agentic-run-compare");
        compare.locator("[data-compare-rows]").waitFor();
        assertThat(compare.locator("h5", new Locator.LocatorOptions().setHasText("Agents"))).containsText("no difference");

        // the series is listed, and the runs stay out of the Agentic Systems history
        assertThat(testRuns().locator("[data-past-series]")).hasCount(1);
    }

    @Test
    void aStoppedSeriesStartsNoFurtherRun() {
        openSystem(NEWSLETTER);
        count(5);
        testRuns().locator("vaadin-text-field[data-input=\"topic\"] input").fill("a mill");
        testRuns().locator("vaadin-text-area[data-input=\"sources\"] textarea").fill("[\"almanac\"]");
        submit().click();
        confirm();
        waitFor(() -> "running".equals(stateOf(NEWSLETTER + "/Researcher")), "the first run going on");

        testRuns().locator("vaadin-button[data-action=\"stop-series\"]").click();

        assertThat(testRuns().locator("[data-series-status]")).hasAttribute("data-series-status", "CANCELLED");
        assertThat(testRuns().locator("[data-series-run]")).hasCount(1);
    }

    @Test
    void theHumanStepsOfTheRunsAreAnsweredOnThePage() {
        openSystem(APPROVAL);
        count(2);
        testRuns().locator("vaadin-text-field[data-input=\"subject\"] input").fill("the roof");
        submit().click();
        confirm();

        for (int run = 1; run <= 2; run++) {
            Locator prompt = testRuns().locator("[data-human-prompt]");
            assertThat(prompt).hasText("Approve the draft?", new LocatorAssertions.HasTextOptions().setTimeout(30_000));
            testRuns().locator("vaadin-text-area[data-human-answer] textarea").fill("approved " + run);
            testRuns().locator("vaadin-button[data-action=\"answer\"]").click();
            assertThat(prompt).hasCount(0);
        }

        assertThat(testRuns().locator("[data-series-status]")).hasAttribute("data-series-status", "SUCCEEDED");
        assertThat(testRuns().locator("[data-distinct-output-count]")).hasText("2");
    }

    /** A late failure for a system no longer selected does not reach the next one; selecting clears an error (TASK-99). */
    @Test
    void aLateFailureOfAnotherSystemIsNotShown() {
        openSystem(NEWSLETTER);
        count(1);
        testRuns().locator("vaadin-text-field[data-input=\"topic\"] input").fill("a pier");
        testRuns().locator("vaadin-text-area[data-input=\"sources\"] textarea").fill("[\"almanac\"]");
        submit().click();
        confirm();
        assertThat(testRuns().locator("[data-series-status]")).hasAttribute("data-series-status", "SUCCEEDED");
        assertThat(testRuns().locator("[data-past-series]")).hasCount(1);

        // the approval system's calls fail: at once, then late
        testRuns().evaluate("(el, a) => { const real = el.jsonRpc; el.failAfter = 0; el.jsonRpc = new Proxy(real, { get: (target, name) => "
                + "['getTopology', 'getInputSchema', 'listSeries'].includes(name) ? params => params.systemId === a "
                + "? new Promise((_, reject) => setTimeout(() => reject({ error: { message: 'late failure' } }), el.failAfter)) "
                + ": target[name](params) : target[name] }); }", APPROVAL);
        testRuns().locator("select[data-test-system]").selectOption(APPROVAL);
        assertThat(testRuns().locator(".error")).containsText("late failure");
        testRuns().locator("select[data-test-system]").selectOption(NEWSLETTER);
        assertThat(testRuns().locator(".error")).hasCount(0); // selecting clears the error

        testRuns().evaluate("el => el.failAfter = 1500");
        testRuns().locator("select[data-test-system]").selectOption(APPROVAL);
        testRuns().locator("select[data-test-system]").selectOption(NEWSLETTER);
        page.waitForTimeout(2500);
        assertThat(testRuns().locator(".error")).hasCount(0);
        assertThat(testRuns().locator("[data-past-series]")).hasCount(1);
    }

    private void openSystem(String systemId) {
        page.navigate(PAGE);
        testRuns().locator("select[data-test-system]").selectOption(systemId);
        testRuns().locator("qwc-agentic-run-form").waitFor();
        waitFor(() -> Boolean.TRUE.equals(graph().evaluate("g => g.layoutDone === true")), "graph laid out");
    }

    private void count(int runs) {
        Locator field = testRuns().locator("vaadin-integer-field[data-series-count] input");
        field.fill(String.valueOf(runs));
        field.press("Tab");
    }

    private Locator submit() {
        return testRuns().locator("qwc-agentic-run-form vaadin-button[data-action=\"submit-form\"]");
    }

    private void confirm() {
        page.locator("vaadin-dialog[opened]").first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        assertThat(page.locator("vaadin-button[data-confirm=\"run\"]")).containsText("Start the");
        page.locator("vaadin-button[data-confirm=\"run\"]").click();
    }

    private Locator testRuns() {
        return page.locator("qwc-agentic-test-runs");
    }

    private Locator graph() {
        return testRuns().locator("qwc-agentic-graph");
    }

    private String stateOf(String nodeId) {
        return (String) graph().evaluate("(g, id) => g.stateOf(id)", nodeId);
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
