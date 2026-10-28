package avsa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionReportTest {

    private static Defect defect(DefectType type, Severity severity) {
        return new Defect(type, "door", severity, null, null);
    }

    @Test
    void noDefectsIsExcellent() {
        ConditionReport r = ConditionReport.noDefects(null);
        assertEquals(0, r.severityScore());
        assertEquals(ConditionGrade.EXCELLENT, r.suggestedGrade());
        assertEquals(ConditionGrade.EXCELLENT, r.effectiveGrade());
    }

    @Test
    void scoreAddsSeverityWeightsAndDoublesMechanical() {
        ConditionReport r = new ConditionReport(null, List.of(
                defect(DefectType.SCRATCH, Severity.MINOR),      // 1
                defect(DefectType.DENT, Severity.MODERATE),      // 3
                defect(DefectType.MECHANICAL, Severity.MINOR))); // 2
        assertEquals(6, r.severityScore());
        assertEquals(ConditionGrade.AVERAGE, r.suggestedGrade());
    }

    @Test
    void suggestedGradeThresholds() {
        assertEquals(ConditionGrade.CLEAN,
                new ConditionReport(null, List.of(defect(DefectType.SCRATCH, Severity.MODERATE))).suggestedGrade());
        assertEquals(ConditionGrade.ROUGH,
                new ConditionReport(null, List.of(defect(DefectType.MECHANICAL, Severity.SEVERE))).suggestedGrade());
    }

    @Test
    void conflictOnlyWhenChosenGradeIsBetterThanDefectsSupport() {
        List<Defect> severe = List.of(defect(DefectType.RUST, Severity.SEVERE));
        assertTrue(new ConditionReport(ConditionGrade.EXCELLENT, severe).gradeConflictsWithDefects());
        assertFalse(new ConditionReport(ConditionGrade.AVERAGE, severe).gradeConflictsWithDefects());
        assertFalse(new ConditionReport(ConditionGrade.ROUGH, List.of()).gradeConflictsWithDefects(),
                "a worse grade than the defects suggest is the seller's choice");
        assertFalse(new ConditionReport(null, severe).gradeConflictsWithDefects());
    }

    @Test
    void chosenGradeOverridesSuggestion() {
        ConditionReport r = new ConditionReport(ConditionGrade.CLEAN, List.of());
        assertEquals(ConditionGrade.CLEAN, r.effectiveGrade());
    }
}
