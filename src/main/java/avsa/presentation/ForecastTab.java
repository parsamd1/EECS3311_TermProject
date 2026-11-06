package avsa.presentation;

import avsa.domain.DepreciationForecast;
import avsa.domain.ForecastPoint;
import avsa.domain.Money;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** F06: projected value at 3, 6 and 12 months, the net after holding costs, and when waiting stops paying off. */
final class ForecastTab extends FeatureTab {

    private final TextField holding = new TextField("0");
    private final VBox summary = new VBox(6);
    private final NumberAxis months = new NumberAxis(0, 12, 1);
    private final NumberAxis value = new NumberAxis();
    private final LineChart<Number, Number> chart = new LineChart<>(months, value);
    private final TableView<ForecastPoint> table = Ui.table(130, List.of(
            Ui.col("After", p -> p.months() + " months", 90),
            Ui.col("Worth (low - high)", p -> Ui.cad(p.lowValue()) + " - " + Ui.cad(p.highValue()), 170),
            Ui.col("Value lost", p -> Ui.cad(p.lossDollars()) + String.format(Locale.ROOT, " (%.1f%%)", p.lossPercent()), 140),
            Ui.col("Net if sold then", p -> Ui.cad(p.netIfSoldThen()), 140)));

    ForecastTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Forecast";
    }

    @Override
    Node content() {
        holding.setPromptText("Insurance, loan payment, parking");
        GridPane form = Ui.form();
        Ui.row(form, "Holding cost per month ($)", holding);
        Button calculate = new Button("Forecast");
        calculate.setDefaultButton(true);
        calculate.setOnAction(e -> {
            Money cost;
            try {
                cost = Ui.parseMoney(holding.getText(), "Holding cost");
            } catch (IllegalArgumentException ex) {
                ctx.problem(ex);
                return;
            }
            ctx.run("Projecting the value...", () -> controller().forecast(cost == null ? Money.ZERO : cost), f -> { });
        });
        months.setLabel("Months from now");
        value.setForceZeroInRange(false);
        value.setLabel("Value ($)");
        chart.setTitle("Projected value");
        chart.setAnimated(false);
        chart.setPrefHeight(300);
        return Ui.column(Ui.heading("Depreciation forecast"),
                Ui.muted("Starts from the expected retail price of the active price and uses the depreciation rates "
                        + "for the vehicle's segment and age (config/depreciation-rates.csv)."),
                Ui.card(null, form, calculate), summary, chart, table);
    }

    @Override
    void refresh() {
        summary.getChildren().clear();
        chart.getData().clear();
        DepreciationForecast f = session().flatMap(s -> s.forecast()).orElse(null);
        table.getItems().setAll(f == null ? List.of() : f.points());
        if (f == null) {
            summary.getChildren().add(Ui.muted(session().flatMap(s -> s.activePrice()).isPresent()
                    ? "No forecast yet." : "Accept a price in the Pricing tab first."));
            return;
        }
        summary.getChildren().add(Ui.text("Starting from " + Ui.cad(f.referencePrice()) + " (" + Ui.label(f.segment())
                + " rates), holding cost " + Ui.cad(f.monthlyHoldingCost()) + " a month. A dealer pays about "
                + Ui.cad(f.wholesaleToday()) + " today."));
        summary.getChildren().add(Ui.styled(Ui.text(f.breakEvenMonth()
                .map(m -> "From month " + m + ", waiting nets less than selling to a dealer today.")
                .orElse("Within 12 months, waiting still nets more than a dealer pays today.")), "card-title"));
        f.warnings().forEach(w -> summary.getChildren().add(Ui.warning(w)));
        XYChart.Series<Number, Number> high = series("High estimate", f, true);
        XYChart.Series<Number, Number> low = series("Low estimate", f, false);
        XYChart.Series<Number, Number> net = new XYChart.Series<>();
        net.setName("Net if sold then (after holding costs)");
        net.getData().add(new XYChart.Data<>(0, f.referencePrice().toDouble()));
        f.points().forEach(p -> net.getData().add(new XYChart.Data<>(p.months(), p.netIfSoldThen().toDouble())));
        chart.getData().addAll(List.of(high, low, net));
    }

    private static XYChart.Series<Number, Number> series(String name, DepreciationForecast f, boolean high) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        s.getData().add(new XYChart.Data<>(0, f.referencePrice().toDouble()));
        for (ForecastPoint p : f.points()) {
            s.getData().add(new XYChart.Data<>(p.months(), (high ? p.highValue() : p.lowValue()).toDouble()));
        }
        return s;
    }
}
