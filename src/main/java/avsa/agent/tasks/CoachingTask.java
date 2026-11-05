package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.ChatTurn;
import avsa.domain.Defect;
import avsa.domain.Persona;
import avsa.domain.Speaker;
import avsa.domain.VehicleFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Asks the AI to coach the seller on honesty and on warning signs the buyer showed (F09). */
public final class CoachingTask implements AiTask<CoachingDraft> {

    private final Persona persona;
    private final VehicleFacts facts;
    private final List<ChatTurn> transcript;

    public CoachingTask(Persona persona, VehicleFacts facts, List<ChatTurn> transcript) {
        this.persona = persona;
        this.facts = facts;
        this.transcript = List.copyOf(transcript);
    }

    @Override
    public String name() {
        return "coaching";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.coaching(persona, facts, transcript);
    }

    @Override
    public Class<CoachingDraft> resultType() {
        return CoachingDraft.class;
    }

    @Override
    public List<String> validate(CoachingDraft draft) {
        return draft.disclosureFeedback() == null || draft.disclosureFeedback().isBlank()
                ? List.of("Give disclosure feedback.") : List.of();
    }

    /** Without the AI: whether each recorded defect came up in the seller's messages. */
    @Override
    public CoachingDraft fallback() {
        String said = transcript.stream().filter(t -> t.speaker() == Speaker.SELLER).map(ChatTurn::message)
                .reduce("", (a, b) -> a + " " + b).toLowerCase(Locale.ROOT);
        List<String> notes = new ArrayList<>();
        for (Defect d : facts.defects()) {
            String type = d.type().name().toLowerCase(Locale.ROOT);
            notes.add(said.contains(type) ? "You mentioned the " + type + "." : "You did not mention the " + type
                    + " on the " + d.bodyLocation() + "; buyers will see it at the viewing.");
        }
        return new CoachingDraft(notes.isEmpty() ? "No defects are recorded, so there was nothing to disclose."
                : String.join(" ", notes), List.of());
    }
}
