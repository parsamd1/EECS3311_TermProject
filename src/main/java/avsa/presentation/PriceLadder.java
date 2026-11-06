package avsa.presentation;

import avsa.domain.Money;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/** F03's price ladder: walk-away floor, wholesale range, asking price and retail range on one scale. */
final class PriceLadder extends Pane {

    private static final double WIDTH = 760;
    private static final double BAR_Y = 44;
    private static final double BAR_HEIGHT = 26;

    PriceLadder() {
        setPrefSize(WIDTH, 120);
        setMinHeight(120);
    }

    void show(PriceRecommendation rec) {
        getChildren().clear();
        if (rec == null) {
            return;
        }
        double low = Math.min(rec.walkAwayFloor().toDouble(), rec.wholesale().low().toDouble()) * 0.97;
        double high = Math.max(rec.askingPrice().toDouble(), rec.retail().high().toDouble()) * 1.02;
        Line axis = new Line(x(low, low, high), BAR_Y + BAR_HEIGHT / 2, x(high, low, high), BAR_Y + BAR_HEIGHT / 2);
        axis.setStroke(Color.web("#bbbbbb"));
        getChildren().add(axis);
        band(rec.wholesale(), "Wholesale (dealer) " + Ui.cad(rec.wholesale()), Color.web("#4a78c2"), low, high);
        band(rec.retail(), "Retail (private) " + Ui.cad(rec.retail()), Color.web("#2f9e5b"), low, high);
        marker(rec.walkAwayFloor(), "Walk-away floor " + Ui.cad(rec.walkAwayFloor()), Color.web("#c0392b"), low, high, 0);
        marker(rec.askingPrice(), "Asking " + Ui.cad(rec.askingPrice()), Color.web("#5b3cc4"), low, high, 18);
    }

    private void band(PriceRange range, String text, Color color, double low, double high) {
        double from = x(range.low().toDouble(), low, high);
        double to = x(range.high().toDouble(), low, high);
        Rectangle r = new Rectangle(from, BAR_Y, Math.max(4, to - from), BAR_HEIGHT);
        r.setFill(color.deriveColor(0, 1, 1, 0.75));
        r.setArcWidth(6);
        r.setArcHeight(6);
        Label label = new Label(text);
        label.setLayoutX(clamp(from, 230));
        label.setLayoutY(BAR_Y + BAR_HEIGHT + 6);
        label.setTextFill(color.darker());
        getChildren().addAll(r, label);
    }

    private void marker(Money value, String text, Color color, double low, double high, double raise) {
        double at = x(value.toDouble(), low, high);
        Line line = new Line(at, BAR_Y - 6, at, BAR_Y + BAR_HEIGHT + 4);
        line.setStroke(color);
        line.setStrokeWidth(3);
        Label label = new Label(text);
        label.setLayoutX(clamp(at - 60, 170));
        label.setLayoutY(BAR_Y - 26 - raise);
        label.setTextFill(color);
        label.setStyle("-fx-font-weight: bold;");
        getChildren().addAll(line, label);
    }

    private static double x(double value, double low, double high) {
        return 10 + (value - low) / (high - low) * (WIDTH - 20);
    }

    private static double clamp(double x, double labelWidth) {
        return Math.max(0, Math.min(WIDTH - labelWidth, x));
    }
}
