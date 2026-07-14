package com.printkeep.quota.core.processing.pipeline;

import com.printkeep.quota.core.processing.exception.PrintProcessingException;

/**
 * Interface defining a single step/stage in the print processing pipeline.
 */
public interface PipelineStage {

    /**
     * Executes the business logic for this stage in the context of the current request.
     *
     * @param context the current request pipeline context.
     * @throws PrintProcessingException if processing fails.
     */
    void process(PipelineContext context) throws PrintProcessingException;
}
