package avsa.presentation;

import avsa.domain.Money;
import avsa.domain.PriceAdjustment;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.StrategyType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** F03: choose a strategy, see the price ladder and adjustments, accept AI proposals one by one, and accept the price. */
final class PricingTab extends FeatureTab {

    private final ToggleGroup strategy = new ToggleGroup();
    private final RadioButton maxProfit = new RadioButton("Maximum profit: sell privately at a retail price");
    private final RadioButton quickSale = new RadioButton("Quick sale: sell to a dealer at a wholesale price");
    private final TextField margin = new TextField();
    private final TextField spread = new TextField();
    private final TextField baseline = new TextField();
    private final Button calculate = new Button("Calculate");
    private final PriceLadder ladder = new PriceLadder();
    private final VBox result = new VBox(8);
    private final VBox active = new VBox(4);
    private final List<CheckBox> proposalBoxes = new ArrayList<>();
    private PriceRecommendation shown;

    PricingTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Pricing";
    }

    @Override
    Node content() {
        maxProfit.setToggleGroup(strategy);
        quickSale.setToggleGroup(strategy);
        maxProfit.setUserData(StrategyType.MAX_PROFIT);
        quickSale.setUserData(StrategyType.QUICK_SALE);
        PricingParameters defaults = PricingParameters.defaults();
        margin.setText(String.valueOf(defaults.negotiationMargin()));
        spread.setText(String.valueOf(defaults.dealerSpread()));
        baseline.setPromptText("Only used when there are too few comparables");
        GridPane advanced = Ui.form();
        Ui.row(advanced, "Negotiation margin", margin);
        Ui.row(advanced, "Dealer spread", spread);
        Ui.row(advanced, "Your own estimate ($)", baseline);
        TitledPane advancedPane = new TitledPane("Advanced", advanced);
        advancedPane.setExpanded(false);
        calculate.disableProperty().bind(strategy.selectedToggleProperty().isNull());
        calculate.setDefaultButton(true);
        calculate.setOnAction(e -> calculate());
        VBox choose = Ui.card("Strategy (choose one)", maxProfit, quickSale, advancedPane, calculate);
        return Ui.column(Ui.heading("Wholesale and retail pricing"),
                Ui.muted("Prices come from the comparables in the Market tab, adjusted for odometer, condition and "
                        + "history. The AI explains the result and may propose small adjustments; none applies unless "
                        + "you tick it."), choose, active, ladder, result);
    }

    private void calculate() {
        StrategyType type = (StrategyType) strategy.getSelectedToggle().getUserData();
        PricingParameters params;
        Money manual;
        try {
            params = PricingParameters.defaults().withNegotiationMargin(Ui.decimal(margin.getText(), "Negotiation margin"))
                    .withDealerSpread(Ui.decimal(spread.getText(), "Dealer spread"));
            manual = Ui.parseMoney(baseline.getText(), "Your own estimate");
        } catch (IllegalArgumentException e) {
            ctx.problem(e);
            return;
        }
        ctx.run("Pricing the vehicle and asking the AI to explain the result...",
                () -> controller().recommendPrice(type, params, manual), this::showResult);
    }

    private void showResult(PriceRecommendation rec) {
        shown = rec;
        ladder.show(rec);
        result.getChildren().clear();
        proposalBoxes.clear();

        HBox numbers = new HBox(28, number("Retail", Ui.cad(rec.retail())), number("Wholesale", Ui.cad(rec.wholesale())),
                number("Asking", Ui.cad(rec.askingPrice())), number("Walk away below", Ui.cad(rec.walkAwayFloor())),
                number("Confidence", Ui.label(rec.confidence())));
        result.getChildren().add(numbers);

        List<Node> adjustments = new ArrayList<>();
        adjustments.add(Ui.text("Starting value from the comparables: " + Ui.cad(rec.baseline())));
        for (PriceAdjustment a : rec.adjustments()) {
            adjustments.add(Ui.text(a.label() + ": " + (a.amount().isPositive() ? "+" : "") + Ui.cad(a.amount())
                    + "   (" + Ui.label(a.kind()) + ")"));
        }
        rec.assumptions().forEach(a -> adjustments.add(Ui.muted("Assumption: " + a)));
        result.getChildren().add(Ui.card("How the price was built", adjustments.toArray(Node[]::new)));
        rec.warnings().forEach(w -> result.getChildren().add(Ui.warning(w)));

        rec.explanation().ifPresent(text -> result.getChildren().add(Ui.card("Explanation",
                Ui.source(!rec.templateExplanation(), "Template (AI unavailable)"), Ui.text(text))));

        if (!rec.proposals().isEmpty()) {
            VBox box = Ui.card("AI proposals (not applied unless ticked; each is capped at "
                    + String.format(Locale.ROOT, "%.0f%%", rec.parameters().aiAdjustmentCap() * 100) + ")");
            for (PriceAdjustment p : rec.proposals()) {
                CheckBox check = new CheckBox(p.label() + "  " + (p.amount().isPositive() ? "+" : "") + Ui.cad(p.amount()));
                check.setUserData(p.id());
                proposalBoxes.add(check);
                box.getChildren().add(check);
            }
            result.getChildren().add(box);
        }
        Button accept = new Button("Accept price");
        accept.setOnAction(e -> accept(rec));
        result.getChildren().add(accept);
    }

    private void accept(PriceRecommendation rec) {
        List<UUID> chosen = proposalBoxes.stream().filter(CheckBox::isSelected).map(c -> (UUID) c.getUserData()).toList();
        ctx.run("Accepting the price...", () -> controller().acceptPrice(rec, chosen), p -> {
            shown = null;
            if (chosen.isEmpty()) {
                result.getChildren().remove(result.getChildren().size() - 1);
                result.getChildren().add(Ui.ok("Accepted as the active price."));
            } else {
                result.getChildren().setAll(Ui.ok("Accepted with " + chosen.size() + " AI adjustment(s); the active "
                        + "price above includes them."));
            }
        });
    }

    private static VBox number(String label, String value) {
        return new VBox(2, Ui.muted(label), Ui.styled(new Label(value), "big-number"));
    }

    @Override
    void refresh() {
        active.getChildren().clear();
        Optional<PriceRecommendation> current = session().flatMap(s -> s.activePrice());
        if (current.isEmpty()) {
            active.getChildren().add(Ui.muted(session().flatMap(s -> s.latestSnapshot()).isPresent()
                    ? "No price accepted yet." : "Find comparables in the Market tab first."));
        } else {
            PriceRecommendation p = current.get();
            active.getChildren().add(Ui.card("Active price", Ui.text("Asking " + Ui.cad(p.askingPrice()) + ", retail "
                    + Ui.cad(p.retail()) + ", wholesale " + Ui.cad(p.wholesale()) + ", walk away below "
                    + Ui.cad(p.walkAwayFloor())
                    + "   (" + Ui.label(p.strategy()) + ", accepted " + Ui.when(p.createdAt()) + ")")));
            if (p.stale()) {
                active.getChildren().add(Ui.warning("The profile, history or comparables changed since this price was "
                        + "accepted. Calculate again."));
            }
        }
        if (shown == null) {
            ladder.show(current.orElse(null));
        }
    }
}
