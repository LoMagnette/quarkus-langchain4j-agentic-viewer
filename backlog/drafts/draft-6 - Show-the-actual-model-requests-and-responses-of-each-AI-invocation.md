---
id: DRAFT-6
title: Show the actual model requests and responses of each AI invocation
status: Draft
assignee: []
created_date: '2026-10-17 21:49'
labels:
  - idea
  - dev-ui
  - observability
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The timeline shows agent inputs and outputs, not what the model received: rendered system and user messages, tool specifications, raw response, each tool-call round, model name and parameters. Feasibility: a ChatModelListener bean (quarkus-langchain4j) correlated to the agent invocation (thread and scope), events bounded by PayloadTruncator. High value for prompt debugging.
<!-- SECTION:DESCRIPTION:END -->
