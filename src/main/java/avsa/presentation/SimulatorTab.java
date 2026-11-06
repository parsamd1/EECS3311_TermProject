package avsa.presentation;

import avsa.domain.ChatTurn;
import avsa.domain.CoachingReport;
import avsa.domain.Persona;
import avsa.domain.SimulationSession;
import avsa.domain.SimulationStatus;
import avsa.domain.Speaker;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * F09: practise a negotiation with an AI buyer about the latest listing, then get coaching. The code
 * sets the buyer's opening offer and ceiling for each persona; the AI only plays the part.
 */
final class SimulatorTab extends FeatureTab {

    private final ComboBox<Persona> persona = new ComboBox<>();
    private final VBox chat = new VBox(8);
    private final TextField message = new TextField();
    private final Button send = new Button("Send");
    private final Button end = new Button("End and get coaching");
    private final VBox side = new VBox(6);
    private final VBox coaching = new VBox(6);

    SimulatorTab(GuiContext ctx) {
        super(ctx);
    }

    @Override
    String title() {
        return "Simulator";
    }

    @Override
    Node content() {
        persona.getItems().setAll(Persona.values());
        persona.setConverter(new StringConverter<>() {
            @Override
            public String toString(Persona p) {
                return p == null ? "" : switch (p) {
                    case SERIOUS -> "Serious buyer: practical questions, reasonable offers";
                    case LOW_BALLER -> "Low-baller: points at every flaw, raises in small steps";
                    case DEALER -> "Dealer: reconditioning costs, margin and wholesale value";
                    case SUSPICIOUS -> "Suspicious buyer: shows common scam warning signs to spot";
                };
            }

            @Override
            public Persona fromString(String s) {
                return null;
            }
        });
        persona.setValue(Persona.SERIOUS);
        Button start = new Button("Start practice");
        start.setOnAction(e -> ctx.run("The buyer is reading your listing...",
                () -> controller().startSimulation(persona.getValue()), s -> coaching.getChildren().clear()));
        message.setPromptText("Your reply to the buyer");
        HBox.setHgrow(message, Priority.ALWAYS);
        send.setDefaultButton(true);
        send.setOnAction(e -> send());
        end.setOnAction(e -> ctx.run("Reviewing the conversation...", () -> controller().endSimulation(), r -> { }));
        chat.setPrefWidth(760);
        side.setPrefWidth(300);
        return Ui.column(Ui.heading("Buyer simulator"),
                Ui.muted("Practise answering a buyer before the real calls. Everything you write is saved with the "
                        + "session, so an unfinished chat is kept."),
                new HBox(8, persona, start),
                new HBox(16, new VBox(10, Ui.card("Conversation", chat), new HBox(8, message, send), end),
                        Ui.card("Negotiation", side)),
                coaching);
    }

    private void send() {
        String text = message.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        ctx.run("The buyer is replying...", () -> controller().sendSellerMessage(text), t -> message.clear());
    }

    @Override
    void refresh() {
        SimulationSession s = session().flatMap(x -> x.latestSimulation()).orElse(null);
        chat.getChildren().clear();
        side.getChildren().clear();
        coaching.getChildren().clear();
        boolean active = s != null && s.status() == SimulationStatus.ACTIVE;
        message.setDisable(!active);
        send.setDisable(!active);
        end.setDisable(s == null || s.coaching().isPresent());
        if (s == null) {
            chat.getChildren().add(Ui.muted(session().flatMap(x -> x.latestListing()).isPresent()
                    ? "Choose a buyer and start." : "Generate a listing first; the buyer reads it."));
            return;
        }
        for (ChatTurn t : s.transcript()) {
            chat.getChildren().add(bubble(t));
        }
        side.getChildren().addAll(Ui.text("Buyer: " + Ui.label(s.persona())),
                Ui.text("Your asking price: " + Ui.cad(s.askingPrice())),
                Ui.text("Your walk-away floor: " + Ui.cad(s.floor())),
                Ui.styled(Ui.text("Latest offer: " + s.latestBuyerOffer().map(Ui::cad).orElse("none yet")), "card-title"),
                Ui.text("Buyer turns: " + s.buyerTurns() + " of " + s.maxTurns()),
                Ui.text("Status: " + Ui.label(s.status())));
        if (!active && s.coaching().isEmpty()) {
            side.getChildren().add(Ui.warning("The negotiation is over. End it to get coaching."));
        }
        s.coaching().ifPresent(r -> coaching.getChildren().add(coachingCard(r, s)));
    }

    private static Node bubble(ChatTurn t) {
        boolean buyer = t.speaker() == Speaker.BUYER;
        Label text = Ui.text((buyer ? "Buyer: " : "You: ") + t.message()
                + t.offer().map(o -> "   [offer " + Ui.cad(o) + "]").orElse(""));
        text.setMaxWidth(560);
        Ui.styled(text, buyer ? "buyer" : "seller");
        HBox row = new HBox(text);
        row.setAlignment(buyer ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        return row;
    }

    private static Node coachingCard(CoachingReport r, SimulationSession s) {
        List<Node> rows = new ArrayList<>();
        rows.add(Ui.source(r.aiWritten(), "Basic check (AI unavailable)"));
        rows.add(Ui.text("Disclosure: " + r.disclosureFeedback()));
        rows.add(Ui.text("Your floor: " + r.floorFeedback()));
        r.redFlags().forEach(f -> rows.add(Ui.warning("Warning sign: " + f)));
        s.listingGaps().forEach(g -> rows.add(Ui.muted("The listing did not answer: " + g)));
        return Ui.card("Coaching", rows.toArray(Node[]::new));
    }
}
