package avsa.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One practice negotiation with a simulated buyer (F09). */
public final class SimulationSession {

    public static final int DEFAULT_MAX_TURNS = 10;

    private final UUID id;
    private final Persona persona;
    private final int maxTurns;
    private final Money askingPrice;
    private final Money floor;
    private final GeneratedListing listing;
    private SimulationStatus status = SimulationStatus.ACTIVE;
    private List<ChatTurn> transcript = new ArrayList<>();
    private List<String> listingGaps = new ArrayList<>();
    private CoachingReport coaching;

    public SimulationSession(UUID id, Persona persona, int maxTurns, Money askingPrice, Money floor,
                             GeneratedListing listing) {
        this.id = Objects.requireNonNull(id);
        this.persona = Objects.requireNonNull(persona);
        this.maxTurns = maxTurns;
        this.askingPrice = Objects.requireNonNull(askingPrice);
        this.floor = Objects.requireNonNull(floor);
        this.listing = Objects.requireNonNull(listing);
    }

    public UUID id() { return id; }
    public Persona persona() { return persona; }
    public int maxTurns() { return maxTurns; }
    public Money askingPrice() { return askingPrice; }
    public Money floor() { return floor; }
    public GeneratedListing listing() { return listing; }
    public SimulationStatus status() { return status; }
    public List<ChatTurn> transcript() { return List.copyOf(transcript); }
    /** Questions the buyer asked that the listing could not answer. */
    public List<String> listingGaps() { return List.copyOf(listingGaps); }
    public Optional<CoachingReport> coaching() { return Optional.ofNullable(coaching); }

    public long buyerTurns() {
        return transcript.stream().filter(t -> t.speaker() == Speaker.BUYER).count();
    }

    public Optional<Money> latestBuyerOffer() {
        for (int i = transcript.size() - 1; i >= 0; i--) {
            if (transcript.get(i).speaker() == Speaker.BUYER && transcript.get(i).offerAmount() != null) {
                return Optional.of(transcript.get(i).offerAmount());
            }
        }
        return Optional.empty();
    }

    public void add(ChatTurn turn) { transcript.add(turn); }

    /** A gap that mostly repeats one already noted, in other words, is not added again. */
    public void addListingGap(String gap) {
        java.util.Set<String> words = words(gap);
        for (String existing : listingGaps) {
            java.util.Set<String> shared = new java.util.HashSet<>(words(existing));
            shared.retainAll(words);
            if (shared.size() * 2 >= Math.min(words.size(), words(existing).size())) {
                return;
            }
        }
        listingGaps.add(gap);
    }

    private static java.util.Set<String> words(String text) {
        java.util.Set<String> words = new java.util.HashSet<>();
        for (String w : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 3) {
                words.add(w);
            }
        }
        return words;
    }

    public void setStatus(SimulationStatus newStatus) { this.status = newStatus; }
    public void setCoaching(CoachingReport report) { this.coaching = report; }
}
