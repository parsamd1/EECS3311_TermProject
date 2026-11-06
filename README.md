# AI Vehicle Sales Assistant (AVSA)

EECS3311 (Fall 2026) course project · Parsa Majdol Hosseini · 221625579

AVSA is an AI agent that helps private sellers in Canada sell a used vehicle at a fair price. The seller describes the vehicle once; the agent finds comparable listings, computes wholesale and retail price ranges, forecasts the cost of waiting, writes an honest listing, evaluates dealer offers, and lets the seller practise negotiating. Every number is computed by deterministic code, and every AI output is validated before the seller sees it.

## Getting Started

### Prerequisites

- **JDK 21 or newer** (`java -version` to check).
- An internet connection for the first build. Maven itself is downloaded automatically by the included wrapper (`./mvnw`), so it does not need to be installed.
- An Anthropic API key for the AI features. The app uses Claude Haiku 5.5 by default.

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

The GUI has one tab per step, in the order a seller uses them: Vehicle, History, Market, Pricing, Positioning, Forecast, Listing, Export, Offers, Simulator and Sessions. The bottom bar shows the open vehicle, whether every change has been saved, and whether the AI is on. AI-written text carries an **AI** badge; text from a fallback template is labelled as such. GUI and CLI share the same data folder, so a session started in one continues in the other.

CLI examples:

```sh
./mvnw -q exec:java -Dexec.args="--help"     # list commands
./mvnw -q exec:java -Dexec.args="config"     # check the model, the key and the data folder
./mvnw -q exec:java -Dexec.args="profile set --make Mazda --model CX-5 --year 2022 --trim GS --body suv --km 49000 --city Toronto --province ON --defect 'scratch:rear door:minor'"
./mvnw -q exec:java -Dexec.args="comps search"                 # comparables from the seeded dataset
./mvnw -q exec:java -Dexec.args="comps search --source live"   # live search of AutoTrader.ca, Kijiji and CarGurus (needs a key)
./mvnw -q exec:java -Dexec.args="comps exclude 3"              # leave comparable 3 out of pricing (pin, unpin, include too)
./mvnw -q exec:java -Dexec.args="price --strategy max-profit --accept"
./mvnw -q exec:java -Dexec.args="session backup"               # copy all saved sessions to a dated folder
./mvnw -q exec:java -Dexec.args="doctor --live"                # pre-demo check: key, connection and one rehearsal search
```

Live search takes one to three minutes and costs a few cents per search. If it fails, the app shows the last successful live result with its date, or else the seeded dataset, and says which. Sessions and saved live results are kept in `~/.avsa` (set `AVSA_DATA_DIR` to change it), outside the project, because a session can contain the vehicle's VIN. Add `--json` to any command to print the agent trace.

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
| `config/` | Editable depreciation rates and marketplace posting limits |
| `data/listings.csv` | Seeded dataset: real CX-5 listings collected from AutoTrader.ca and Kijiji |
| `data/listings-template.csv` | Column headers for importing your own listings |
| `Stage1_Report.md` | Stage 1 design report |
| `diagrams/` | PlantUML sources for all UML diagrams |
| `diagrams/out/` | Rendered diagrams (PNG, used in the report, and SVG) |
