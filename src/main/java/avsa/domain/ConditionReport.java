package avsa.domain;

import java.util.List;
import java.util.Optional;

/**
 * The vehicle's physical condition: an optional overall grade chosen by the
 * seller and the list of recorded defects.
 */
public final class ConditionReport {

    private final ConditionGrade overallGrade;
    private final List<Defect> defects;

    public ConditionReport(ConditionGrade overallGrade, List<Defect> defects) {
        this.overallGrade = overallGrade;
        this.defects = List.copyOf(defects);
    }

    public static ConditionReport noDefects(ConditionGrade grade) {
        return new ConditionReport(grade, List.of());
    }

    public Optional<ConditionGrade> overallGrade() {
        return Optional.ofNullable(overallGrade);
    }

    public List<Defect> defects() {
        return defects;
    }

    /**
     * Damage severity score: the sum of each defect's severity weight
     * (minor 1, moderate 3, severe 6). Mechanical defects count double because
     * buyers discount them more than cosmetic damage.
     */
    public int severityScore() {
        int score = 0;
        for (Defect d : defects) {
            int weight = d.severity().weight();
            score += d.type() == DefectType.MECHANICAL ? 2 * weight : weight;
        }
        return score;
    }

    /** The grade implied by the defects alone. */
    public ConditionGrade suggestedGrade() {
        int score = severityScore();
        if (score == 0) {
            return ConditionGrade.EXCELLENT;
        }
        if (score <= 3) {
            return ConditionGrade.CLEAN;
        }
        if (score <= 9) {
            return ConditionGrade.AVERAGE;
        }
        return ConditionGrade.ROUGH;
    }

    /**
     * True when the seller's chosen grade is better than the defects support,
     * for example "excellent" with a severe defect. The seller must confirm it.
     */
    public boolean gradeConflictsWithDefects() {
        return overallGrade != null && overallGrade.ordinal() < suggestedGrade().ordinal();
    }

    /** The chosen grade, or the suggested one if the seller did not choose. */
    public ConditionGrade effectiveGrade() {
        return overallGrade != null ? overallGrade : suggestedGrade();
    }
}
