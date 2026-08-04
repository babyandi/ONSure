package io.onsure.platform;

import io.onsure.platform.ValidationModel.StageResult;

/** One ordered stage of the generic Validator Engine. */
public interface ValidatorStage {
    String stageId();

    boolean supports(ValidationContext context);

    StageResult execute(ValidationContext context) throws Exception;
}
