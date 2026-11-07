package avsa.domain;

/** The status of a guided workflow run; each value has a matching run state (F12). */
public enum PlanStatus { RUNNING, AWAITING_APPROVAL, PAUSED, COMPLETED, CANCELLED }
