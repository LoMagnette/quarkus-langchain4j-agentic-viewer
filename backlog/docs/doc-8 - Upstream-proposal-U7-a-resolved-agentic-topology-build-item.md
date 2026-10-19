---
id: doc-8
title: 'Upstream proposal U7: a resolved agentic topology build item'
type: specification
created_date: '2026-10-19 09:53'
updated_date: '2026-10-19 09:54'
---
# Upstream proposal U7: a resolved agentic topology build item

Draft, **nothing has been posted**. Target: quarkus-langchain4j (`quarkus-langchain4j-agentic-deployment`). Versions
read: quarkus-langchain4j 1.14.2 (`AgenticProcessor`, bytecode), langchain4j-agentic 1.20.2-beta30. Follows TASK-78.5
(independent review of 2026-10-19, finding 2).

## Problem

A tool extension that shows or checks the agentic systems of an application (the agentic viewer's Dev UI page) has to
know them at build time. quarkus-langchain4j-agentic resolves them to build the agents, but exposes little of it:

| Build item | What it gives |
|---|---|
| `DetectedAiAgentAsMapBuildItem` | AI agent interfaces (with their sub-interfaces) and their agent methods |
| `DetectedNonAiAgentAsMapBuildItem` | non-AI agent classes and their agent methods |
| `DetectedAiAgentBuildItem` | per interface: agent methods, `@ChatModelSupplier` method, model name, `@ToolBox` / `@McpToolBox` / `@Skills` methods |

Nothing about the structure: which agents are the sub-agents of a workflow, the output key and typed keys, the input
keys of each agent, the loop exit and conditional activation conditions, `@Output`, the planner supplier, the parallel
mapper's items provider, human-in-the-loop and remote (A2A, MCP) details. The viewer therefore reads the annotations
again with Jandex, with rules that mirror langchain4j-agentic's; parity tests reduce, but cannot remove, the risk of
drawing a system differently from what the application builds when the rules change upstream.

## What the viewer does today (TASK-78.5)

- Agent identity and the model name / chat model supplier come from the three build items above.
- The structure, keys, conditions, prompts, tool methods, suppliers and settings are read from the index.
- The viewer adds knowingly: `@RegistryAgent` agents (not detected upstream; note: 1.14.2 rejects a `@RegistryAgent`
  sub-agent at build time, "declared as a sub-agent but does not declare any agent method") and the agent methods a
  class inherits from an agent superclass (langchain4j reads `getMethods()`; upstream lists the declaring class only).
- Any other difference is logged in dev mode ("do not agree on these agents").

## Proposal

A `MultiBuildItem` per detected agent method, produced where `AgenticProcessor` validates them (it already resolves
most of this for `validateAgentNames` / `validateAgenticParameterTypes`):

```java
public final class AgenticAgentBuildItem extends MultiBuildItem {
    ClassInfo agentClass();            // the class or interface the agent is built from
    MethodInfo agentMethod();          // may be declared by a supertype
    DotName kind();                    // the agent annotation: @Agent, @SequenceAgent, ...
    String name();
    String description();
    String outputKey();                // resolved with typedOutputKey
    List<String> inputKeys();          // from @V / @K / typed keys, in parameter order
    List<ClassInfo> subAgents();       // declaration order; subAgent of a parallel mapper
    MethodInfo outputMethod();         // @Output, null when none
    MethodInfo exitCondition();        // loops
    Map<ClassInfo, MethodInfo> activationConditions(); // conditional branches
    MethodInfo plannerSupplier();      // planners
    String itemsProvider();            // parallel mappers
    Map<String, Object> attributes();  // the remaining annotation values (optional, async, maxIterations, ...)
}
```

Additive, no behaviour change; the existing items stay. With it, the viewer would drop its own resolution of identity,
keys, sub-agents and conditions and keep reading only presentation details (prompt texts, tool descriptions).

## Open points

- Inherited agent methods of classes: expose them as langchain4j runs them, or document that they are not supported.
- `@RegistryAgent`: detect it (and accept it as a sub-agent) or document it as unsupported under Quarkus.
