package avsa.agent.tasks;

import java.util.List;

/** The flags a listing review returned; usually few or none. */
public record CompReview(List<ReviewFlag> flags) {
}
