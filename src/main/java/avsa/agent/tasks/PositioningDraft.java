package avsa.agent.tasks;

import avsa.domain.ClaimKind;
import java.util.List;

/** The AI's positioning claims; evidence names comparables as C1, C2... or metrics as metric:name. */
public record PositioningDraft(List<DraftClaim> claims, String priceJustification) {

    public record DraftClaim(ClaimKind kind, String text, List<String> evidence) {
    }
}
