package avsa.presentation;

import avsa.domain.BuyerType;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** F11: judge a dealer's or buyer's offer against the price ladder and get a counter-offer script. */
final class OffersTab extends FeatureTab {

    private final TextField amount = new TextField();
    private final ComboBox<BuyerType> buyer = Ui.choice(BuyerType.class, BuyerType.DEALER);
    private final TextArea notes = new TextArea();
    private final VBox verdict = new VBox(6);
    private final TableView<OfferEvaluation> log = Ui.table(160, List.of(
            Ui.col("Received", e -> Ui.when(e.offer().receivedAt()), 150),
            Ui.col("Amount", e -> Ui.cad(e.offer().amount()), 90),
            Ui.col("From", e -> Ui.label(e.offer().buyerType()), 80),
            Ui.col("Verdict", e -> Ui.label(e.verdict()), 110),
            Ui.col("Counter", e -> e.counterAmount().map(Ui::cad).orElse(""), 90),
            Ui.col("Notes", e -> e.offer().notes(), 250)));

    OffersTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Offers";
    }

    @Override
    Node content() {
        amount.setPromptText("23,500");
        notes.setPromptText("What the buyer said, for example \"the market is soft and it needs tires\"");
        notes.setPrefRowCount(2);
        notes.setWrapText(true);
        GridPane form = Ui.form();
        Ui.row(form, "Offer ($)", amount);
        Ui.row(form, "From", buyer);
        Ui.row(form, "Notes", notes);
        Button evaluate = new Button("Evaluate offer");
        evaluate.setDefaultButton(true);
        evaluate.setOnAction(e -> evaluate());
        log.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) {
                show(b);
            }
        });
        return Ui.column(Ui.heading("Offers"),
                Ui.muted("The verdict comes from your price ladder. The AI only writes the counter-offer script, and "
                        + "may quote only the numbers computed here."),
                Ui.card("Add offer", form, evaluate), verdict, Ui.heading("Offer log"), log);
    }

    private void evaluate() {
        Offer offer;
        try {
            Money value = Ui.parseMoney(amount.getText(), "Offer");
            if (value == null) {
                throw new IllegalArgumentException("Enter the amount offered.");
            }
            offer = new Offer(value, buyer.getValue(), notes.getText().strip(), Instant.now());
        } catch (IllegalArgumentException e) {
            ctx.problem(e);
            return;
        }
        // An offer above the retail range is more likely a typo than a real offer (F11).
        Optional<Money> retailTop = session().flatMap(s -> s.activePrice()).map(p -> p.retail().high());
        if (retailTop.isPresent() && offer.amount().compareTo(retailTop.get()) > 0
                && !ctx.confirm(Ui.cad(offer.amount()) + " is above your retail range (up to " + Ui.cad(retailTop.get())
                        + "). Is the amount correct?")) {
            return;
        }
        ctx.run("Evaluating the offer and writing a counter-offer script...", () -> controller().evaluateOffer(offer),
                e -> {
                    amount.clear();
                    notes.clear();
                    show(e);
                });
    }

    private void show(OfferEvaluation e) {
        List<Node> rows = new ArrayList<>();
        Label headline = Ui.styled(new Label(Ui.label(e.verdict()) + ": " + Ui.cad(e.offer().amount()) + " from a "
                + Ui.label(e.offer().buyerType()).toLowerCase(Locale.ROOT)), "big-number");
        rows.add(headline);
        double position = Math.max(0, Math.min(1, e.positionInWholesale()));
        ProgressBar bar = new ProgressBar(position);
        bar.setPrefWidth(300);
        rows.add(new HBox(8, Ui.muted("Wholesale range"), bar, Ui.text(String.format(Locale.ROOT,
                "%.0f%% of the way to the top", position * 100))));
        rows.add(Ui.text("Gap to the top of the wholesale range: " + Ui.cad(e.gapToWholesaleTop())));
        e.counterAmount().ifPresent(c -> rows.add(Ui.styled(Ui.text("Counter at " + Ui.cad(c)), "card-title")));
        if (e.belowWalkAwayFloor()) {
            rows.add(Ui.error("This offer is below your walk-away floor."));
        }
        if (e.aboveRetailRange()) {
            rows.add(Ui.ok("This offer is above the retail range."));
        }
        e.netIfWaiting().ifPresent(n -> rows.add(Ui.text("Waiting for a private buyer is expected to net about "
                + Ui.cad(n) + " after depreciation and holding costs.")));
        e.warnings().forEach(w -> rows.add(Ui.warning(w)));
        e.script().ifPresent(s -> {
            rows.add(new HBox(8, Ui.styled(new Label("What to say"), "card-title"),
                    Ui.source(!e.templateScript(), "Template (AI unavailable)")));
            rows.add(Ui.text(s));
        });
        verdict.getChildren().setAll(Ui.card(null, rows.toArray(Node[]::new)));
    }

    @Override
    void refresh() {
        List<OfferEvaluation> offers = session().map(s -> s.offerLog()).orElse(List.of());
        log.getItems().setAll(offers.reversed());
        if (offers.isEmpty()) {
            verdict.getChildren().setAll(Ui.muted(session().flatMap(s -> s.activePrice()).isPresent()
                    ? "No offers yet." : "Accept a price in the Pricing tab first; offers are judged against it."));
        }
    }
}
