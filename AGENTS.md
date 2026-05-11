Notes:
- Use `tmp/<context>/**` dir as a primary scratchpad and a target for any temporary files.
- Use newest and freshest Java 25 features, if applicable.
- Refrain from using in-line FQNs; use imports instead.
- If deadlocked, escalate to a human using the ask question tool.
- Use markdown `##` headers to break down your final response into structured, easy-to-scan sections.

Coding Guardrails:
- Avoid overengineering.
- Avoid using `Objects.requireNonNull` if code following it is guaranteed to throw NPE.
- Bias towards human-aligned readability, understandability, maintainability, and no cognitive load bottlenecks.
- Simplify rather than layering workarounds.
- In unit tests prefer existing value objects instead of dummies.
- Testing: JUnit 5 with descriptive method names using snake case except leading `methodName` being under test, like `methodName_scenario_gives_expected_behavior()`
- Testing: Write tests clarifying behavior rather than increasing coupling.