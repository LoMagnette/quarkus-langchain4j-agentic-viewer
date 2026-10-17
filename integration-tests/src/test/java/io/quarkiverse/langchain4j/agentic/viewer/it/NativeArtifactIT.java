package io.quarkiverse.langchain4j.agentic.viewer.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The native executable of the sample app (built with -Dquarkus.native.enabled=true, see CLAUDE.md) carries
 * nothing of the Dev UI (F6.4): none of its class or resource names is in the image. Skipped without a native build.
 */
class NativeArtifactIT {

    private static final List<String> FORBIDDEN = List.of(
            "io.quarkiverse.langchain4j.agentic.viewer.runtime.dev",
            "io/quarkiverse/langchain4j/agentic/viewer/runtime/dev",
            "io.quarkiverse.langchain4j.agentic.viewer.deployment",
            "qwc-agentic-",
            "AgenticViewerJsonRpcService");

    private static Optional<Path> runner() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("target"))) {
            return files.filter(file -> file.getFileName().toString().endsWith("-runner") && Files.isRegularFile(file))
                    .findFirst();
        }
    }

    @Test
    void noDevUiCodeInTheNativeExecutable() throws IOException {
        Optional<Path> runner = runner();
        assumeTrue(runner.isPresent(), "no native executable in target/ (build with -Dquarkus.native.enabled=true)");
        // the image keeps class names (hubs, reflection metadata): search the bytes
        String image = new String(Files.readAllBytes(runner.get()), StandardCharsets.ISO_8859_1);
        assertTrue(image.contains("io.quarkiverse.langchain4j.agentic.viewer.it.samples"), "the sample app is in the image");
        assertEquals(List.of(), FORBIDDEN.stream().filter(image::contains).toList());
    }
}
