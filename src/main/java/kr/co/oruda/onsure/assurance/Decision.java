package kr.co.oruda.onsure.assurance;

public enum Decision {
    PASS,
    FAIL,
    HOLD,
    BLOCKED,
    NOT_RUN,
    INCONCLUSIVE;

    public boolean isFinalPassEligible() {
        return this == PASS;
    }
}
