# Quarkus LangChain4j Agentic Viewer

A Quarkus extension adding an **Agentic Systems** page to the Dev UI for
[LangChain4j agentic](https://docs.langchain4j.dev/tutorials/agents) systems built with
[quarkus-langchain4j](https://github.com/quarkiverse/quarkus-langchain4j).

- **See** every agentic system of the application as a graph, declarative or built with the `AgenticServices`
  builders (produced as CDI beans, or anywhere with `builder-discovery=transform`): its structure (sequences, parallel
  branches, loops, conditions, supervisors, planners, human-in-the-loop steps, A2A and MCP agents) and its data flow
  (the AgenticScope keys the agents share), with the prompts, models and tools of each agent.
- **Run** a system from a form generated from its inputs, and follow it live: agents highlighted as they run, with
  durations and tokens, a timeline of every invocation and tool call, the AgenticScope state, errors and
  `@ErrorHandler` recoveries, past runs replayed on the graph.
- **Answer** `@HumanInTheLoop` steps from the Dev UI.
- **Understand** supervisors and planners: the path a supervisor took, each planner decision as a numbered step, and
  dedicated views for the goal-oriented (with a path preview), peer-to-peer and blackboard planners of
  `langchain4j-agentic-patterns`.

It only exists in dev mode: nothing of it is part of a production or native build.

## Usage

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-agentic-viewer</artifactId>
    <version>${agentic-viewer.version}</version>
</dependency>
```

Start the application with `mvn quarkus:dev`, open the Dev UI and click **Agentic Systems**.

A run executes the system for real (models and tools included): prefer a local or fake model in dev. See the
[documentation](docs/modules/ROOT/pages/index.adoc) for the views, the tools confirmation, the configuration and the
custom planner topology SPI.

## Building

```bash
mvn clean install              # without langchain4j-agentic-patterns
mvn clean install -Ppatterns   # with it (CI builds both)
```

Try it on the sample app, which runs on a scripted fake model (no API key):

```bash
mvn quarkus:dev -pl integration-tests -Ppatterns
```

or on a local Ollama through its OpenAI-compatible API:

```bash
mvn quarkus:dev -pl integration-tests -Dquarkus.profile=dev,ollama
```

## Status

Experimental. Builder systems are found among the CDI beans by default; `builder-discovery=transform` also finds the
ones that are no beans. The live views rely on internal parts of `langchain4j-agentic` for which there is no public API
yet; an incompatible version of the library disables some live information, never the application.
