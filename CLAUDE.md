# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

EECS3311 (Software Design) term project: **AI Vehicle Sales Assistant (AVSA)**. Currently in Stage 1 (design only): there is no source code, build system, or test suite yet. Planned stack (Stage 2): Java 17+, JavaFX, JUnit 5.

- `Stage1_Report.md` — the Stage 1 deliverable. All nine sections are written; the "Report Contents" list at the top links to them. Section 2 (features F01–F12) is the source of truth for behaviour; diagrams and later sections must stay consistent with it.
- `diagrams/` — PlantUML sources for the report's diagrams; rendered output in `diagrams/out/` (PNG embedded in the report, SVG alongside).
- `Test/test.puml` — scratch diagram used to verify the toolchain; not part of the deliverable.

## Diagrams (PlantUML)

The `plantuml` CLI is not installed; use `plantuml.jar` in the project root. Render from inside `diagrams/` (`!include style.iuml` is relative; `-o` is relative to the source file). Large diagrams exceed PlantUML's default 4096px limit, hence the flag:

```sh
cd diagrams
/opt/homebrew/opt/openjdk/bin/java -DPLANTUML_LIMIT_SIZE=8192 -jar ../plantuml.jar -tpng -o out *.puml
/opt/homebrew/opt/openjdk/bin/java -DPLANTUML_LIMIT_SIZE=8192 -jar ../plantuml.jar -tsvg -o out *.puml
# quick look in the terminal (sequence diagrams only):
/opt/homebrew/opt/openjdk/bin/java -jar ../plantuml.jar -tutxt -pipe < some-sequence.puml
```

Diagram files: `class-N-*.puml` are the Section 3 class views, `plan-run-states.puml` is Figure 4.1 (the only Section 4 figure; patterns point to the class views instead), `use-case.puml` / `use-case-workflow.puml` are Figures 5.1 / 5.2, `sdNN-*.puml` are the nine Section 7 sequence diagrams SD00–SD08, with related features sharing a diagram (they include `seq-style.iuml`; AI steps use `ref over … : SD00` instead of repeating the AI failure handling). Use-case descriptions (Section 6) number alternative flows by the step they branch from ("3a"); keep them consistent with the error cases in Section 2.

In use-case diagrams, write «extend» arrows as `Base <.. Extension` so extensions are laid out to the right and the actor's lines don't wrap around.

Conventions for the class diagrams:
- Every diagram includes `style.iuml` (shared skinparams). A class owned by another view is declared with the `<<ref>>` stereotype, which the style renders as a grey, member-less box; its package label says `(see 3.x)` pointing at the report subsection that defines it.
- Diagram titles and `see 3.x` references use **report subsection numbers** (3.2–3.8), not the `class-N-` file numbers (1–7).
- Pattern roles are shown as stereotypes (`<<Facade>>`, `<<Strategy>>`, `<<Proxy>>`, `<<Command>>`, `<<Invoker>>`, ...). The nine patterns (Facade, Observer, Strategy, Factory Method, Template Method, Adapter, Command, Proxy, State) are explained in Section 4; a class or method renamed in a class view must also be renamed in the sequence diagrams and the report text.
- Sequence diagrams may only call methods that exist on the class diagrams; if a flow needs a new method, add it to the class view first.
- Avoid names that suggest a pattern the class doesn't implement (that's why it's `PromptComposer`, not `PromptBuilder`, and `ManualHistoryImporter`, not an Adapter).
- After rendering, view the PNG to check the layout; if a diagram becomes very wide, split it or use `left to right direction`.

## Open inputs from the author

The default **dealer spread** (`PricingParameters.dealerSpread`, F03) is to be calibrated from the author's own dealer offers and has not been provided yet. Don't invent a number; mark it as pending in worked examples.

## Scope

The author asked to keep the report close to the handout's requirements (stage1.pdf): don't add diagrams, patterns, or sections beyond what it asks for without checking first. Every design element in Stage 1 becomes Stage 2 implementation work.
