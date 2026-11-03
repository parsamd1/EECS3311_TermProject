package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.ComparableListing;
import avsa.domain.ListingFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Asks the model to flag problems in the top comparables from a fixed list of codes (F02). */
public final class CompReviewTask implements AiTask<CompReview> {

    private final List<ComparableListing> listings;
    private final String sellerTrim;

    public CompReviewTask(List<ComparableListing> listings, String sellerTrim) {
        this.listings = List.copyOf(listings);
        this.sellerTrim = sellerTrim;
    }

    @Override
    public String name() {
        return "comp-review";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.compReview(listings, sellerTrim);
    }

    @Override
    public Class<CompReview> resultType() {
        return CompReview.class;
    }

    @Override
    public List<String> validate(CompReview result) {
        if (result.flags() == null) {
            return List.of("The JSON must have a \"flags\" array.");
        }
        Set<String> ids = listings.stream().map(ComparableListing::listingId).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        for (ReviewFlag flag : result.flags()) {
            if (flag.code() == null) {
                problems.add("Every flag needs a code from the list.");
            } else if (!ids.contains(flag.listingId())) {
                problems.add("There is no listing with id " + flag.listingId() + ".");
            }
        }
        return problems;
    }

    /** Without the review the listings simply stay unreviewed. */
    @Override
    public CompReview fallback() {
        return new CompReview(List.of());
    }

    public static Map<String, List<ListingFlag>> byListing(CompReview review) {
        Map<String, List<ListingFlag>> flags = new HashMap<>();
        for (ReviewFlag flag : review.flags()) {
            flags.computeIfAbsent(flag.listingId(), id -> new ArrayList<>()).add(new ListingFlag(flag.code(), flag.reason()));
        }
        return flags;
    }
}
