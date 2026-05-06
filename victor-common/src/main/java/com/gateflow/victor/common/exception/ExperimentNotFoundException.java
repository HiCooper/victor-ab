package com.gateflow.victor.common.exception;

/**
 * 实验不存在异常
 */
public class ExperimentNotFoundException extends VictorException {

    public static final String ERROR_CODE = "EXPERIMENT_NOT_FOUND";

    public ExperimentNotFoundException(String experimentId) {
        super(ERROR_CODE, "Experiment not found: " + experimentId);
    }

    public ExperimentNotFoundException(String experimentId, Throwable cause) {
        super(ERROR_CODE, "Experiment not found: " + experimentId, cause);
    }
}