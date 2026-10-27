# AI Vehicle Sales Assistant (AVSA)

EECS3311 (Fall 2026) course project · Parsa Majdol Hosseini · 221625579

AVSA is an AI agent that helps private sellers in Canada sell a used vehicle at a fair price. The seller describes the vehicle once; the agent finds comparable listings, computes wholesale and retail price ranges, forecasts the cost of waiting, writes an honest listing, evaluates dealer offers, and lets the seller practise negotiating. Every number is computed by deterministic code, and every AI output is validated before the seller sees it.

## Getting Started

### Prerequisites

- **JDK 21 or newer** (`java -version` to check).
- An internet connection for the first build. Maven itself is downloaded automatically by the included wrapper (`./mvnw`), so it does not need to be installed.
- An Anthropic API key for the AI features. The app uses Claude Haiku 4.5 by default.

### Local configuration (API key)

API keys are never committed. Copy the example file and put your own key in the copy:

```sh
cp .env.example .env
```

Then edit `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your key. `.env` is listed in `.gitignore`. An `ANTHROPIC_API_KEY` environment variable, if set, takes precedence over the file.

### Build and test

```sh
./mvnw test
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

### Run

| Interface | Command |
|---|---|
| GUI | `./mvnw javafx:run` |
| CLI | `./mvnw -q exec:java -Dexec.args="<command>"` |

CLI examples:

```sh
./mvnw -q exec:java -Dexec.args="--help"     # list commands
./mvnw -q exec:java -Dexec.args="config"     # check the model and whether a key is configured
```

## Stage 1: Software and Agent Design

The full design report is **[Stage1_Report.md](Stage1_Report.md)**:

1. [Project Overview](Stage1_Report.md#1-project-overview)
2. [Detailed Feature Specifications](Stage1_Report.md#2-detailed-feature-specifications)
3. [UML Class Diagram](Stage1_Report.md#3-uml-class-diagram)
4. [Design Pattern Explanations](Stage1_Report.md#4-design-pattern-explanations)
5. [Use-Case Diagram](Stage1_Report.md#5-use-case-diagram)
6. [Detailed Use-Case Descriptions](Stage1_Report.md#6-detailed-use-case-descriptions)
7. [Sequence Diagrams](Stage1_Report.md#7-sequence-diagrams)
8. [Feature-to-Design Traceability Table](Stage1_Report.md#8-feature-to-design-traceability-table)
9. [Feature Implementation Explanations](Stage1_Report.md#9-feature-implementation-explanations)

## Repository Layout

| Path | Contents |
|---|---|
| `src/main/java/avsa/` | Application source code |
| `src/test/java/avsa/` | Unit tests |
| `pom.xml`, `mvnw` | Maven build and wrapper |
| `.env.example` | Example configuration (no real keys) |
| `Stage1_Report.md` | Stage 1 design report |
| `diagrams/` | PlantUML sources for all UML diagrams |
| `diagrams/out/` | Rendered diagrams (PNG, used in the report, and SVG) |
