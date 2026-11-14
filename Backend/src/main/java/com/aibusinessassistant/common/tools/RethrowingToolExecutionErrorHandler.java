package com.aibusinessassistant.common.tools;

import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import io.quarkiverse.langchain4j.DefaultToolExecutionErrorHandler;

/**
 * Fails the request when a tool throws, for every AI service without its own handler. The default handler would
 * send the exception message to the model as the tool result, so the model would answer without the data.
 * Invalid tool arguments and unknown tool names are handled elsewhere and keep their default behaviour.
 */
@DefaultToolExecutionErrorHandler
public class RethrowingToolExecutionErrorHandler implements ToolExecutionErrorHandler {

    @Override
    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
        // rawError is the exception the tool threw; error may be only its cause.
        Exception raw = context.rawError();
        if (raw instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("Tool " + context.toolExecutionRequest().name() + " failed", raw);
    }
}
