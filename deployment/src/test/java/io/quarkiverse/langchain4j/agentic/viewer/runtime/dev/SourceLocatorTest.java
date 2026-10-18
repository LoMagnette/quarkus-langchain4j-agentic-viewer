package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AiAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.ToolInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;

/**
 * The code behind a node is found in the application sources and resources of dev mode, at the line declaring it.
 */
class SourceLocatorTest {

    private static final List<String> STORY = List.of(
            "package com.example;",
            "",
            "/** Writes a story(); see write(). */",
            "public interface Story {",
            "",
            "    // story() is the entry point",
            "    String story(String topic);",
            "",
            "    interface Writer {",
            "        String story(String draft);",
            "",
            "        static String write(String topic) {",
            "            return story(topic);",
            "        }",
            "    }",
            "}");

    @TempDir
    Path dir;

    private SourceLocator locator() throws Exception {
        Path sources = Files.createDirectories(dir.resolve("src/main/java/com/example"));
        Files.write(sources.resolve("Story.java"), STORY);
        Path resources = Files.createDirectories(dir.resolve("src/main/resources/prompts"));
        Files.writeString(resources.resolve("story.txt"), "Write a story about {{topic}}");
        return new SourceLocator(List.of(dir.resolve("src/main/java")), List.of(dir.resolve("src/main/resources")));
    }

    private int line(SourceLocator locator, CodeRef ref) {
        return locator.locate(ref).orElseThrow(() -> new AssertionError("not found: " + ref)).lineNumber();
    }

    @Test
    void methodsAndClassesAtTheirDeclaration() throws Exception {
        SourceLocator locator = locator();
        assertEquals(7, line(locator, CodeRef.method(CodeRef.Role.AGENT, "com.example.Story", "story")),
                "not the Javadoc nor the comment");
        assertEquals(10, line(locator, CodeRef.method(CodeRef.Role.AGENT, "com.example.Story$Writer", "story")),
                "the nested class's own method");
        assertEquals(12, line(locator, CodeRef.method(CodeRef.Role.AGENT, "com.example.Story$Writer", "write")));
        assertEquals(9, line(locator, CodeRef.method(CodeRef.Role.AGENT, "com.example.Story$Writer", null)));
        assertEquals(13, line(locator, new CodeRef(CodeRef.Role.CALL_SITE, "com.example.Story", "story", null, 13)));
        SourceLocator.Location location = locator.locate(CodeRef.method(CodeRef.Role.AGENT, "com.example.Story", "story"))
                .orElseThrow();
        assertEquals("java", location.lang());
        assertTrue(location.fileName().startsWith("file:") && location.fileName().endsWith("com/example/Story.java"),
                location.fileName());
    }

    @Test
    void promptResources() throws Exception {
        SourceLocator.Location location = locator().locate(new CodeRef(CodeRef.Role.USER_MESSAGE, "com.example.Story", null,
                "/prompts/story.txt", null)).orElseThrow();
        assertTrue(location.fileName().endsWith("prompts/story.txt"), location.fileName());
        assertEquals("txt", location.lang());
        assertEquals(1, location.lineNumber());
    }

    @Test
    void codeOutsideTheApplicationIsNotFound() throws Exception {
        SourceLocator locator = locator();
        assertEquals(Optional.empty(), locator.locate(CodeRef.method(CodeRef.Role.AGENT, "dev.langchain4j.Lib", "x")));
        assertEquals(Optional.empty(), locator.locate(new CodeRef(CodeRef.Role.SYSTEM_MESSAGE, "com.example.Story", null,
                "prompts/missing.txt", null)));
        assertEquals(Optional.empty(), new SourceLocator(List.of(), List.of())
                .locate(CodeRef.method(CodeRef.Role.AGENT, "com.example.Story", "story")), "outside dev mode");
    }

    @Test
    void theJsonRpcServiceListsTheCodeOfANodeWithItsTools() throws Exception {
        AgentNode writer = new AgentNode("story/Writer", "write", NodeKind.AI_AGENT, null, List.of("topic"), "story", false,
                false, List.of(), null, null, new AiAgentDetails(null, null, null,
                        List.of(new ToolInfo("lookup", "dev.langchain4j.Lib", "lookup", null)), List.of()), null,
                List.of(CodeRef.method(CodeRef.Role.AGENT, "com.example.Story$Writer", "write"),
                        new CodeRef(CodeRef.Role.USER_MESSAGE, "com.example.Story", null, "prompts/story.txt", null)));
        AgentNode root = new AgentNode("story", "story", NodeKind.SEQUENCE, null, List.of("topic"), "story", false, false,
                List.of(writer), null, null, null, null);
        AgenticSystem system = new AgenticSystem("story", "Story", "story", SystemOrigin.DECLARATIVE,
                new SourceRef("com.example.Story", "story", null), List.of(), root);
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        registry.register(system);
        RunManager runManager = new RunManager(id -> Optional.of(system), (s, inputs) -> null);
        try {
            AgenticViewerJsonRpcService service = new AgenticViewerJsonRpcService(registry, runManager,
                    PatternAdapters.none(), locator());

            List<Map<String, Object>> code = service.getNodeSources("story", "story/Writer");

            assertEquals(List.of("AGENT", "USER_MESSAGE", "TOOL"), code.stream().map(entry -> entry.get("role")).toList());
            assertEquals(12, code.get(0).get("lineNumber"));
            assertTrue(String.valueOf(code.get(1).get("fileName")).endsWith("prompts/story.txt"));
            assertEquals("dev.langchain4j.Lib", code.get(2).get("className"));
            assertTrue(!code.get(2).containsKey("fileName"), "a dependency's tool is not in the application sources");
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> service.getNodeSources("story", "story/Nope"));
        } finally {
            runManager.shutdown();
        }
    }
}
