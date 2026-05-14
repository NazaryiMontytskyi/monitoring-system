package com.nmontytskyi.monitoring.server.exception;

/**
 * Thrown when PDF report generation fails due to missing data or an internal error.
 * Results in an HTTP 500 response via
 * {@link com.nmontytskyi.monitoring.server.exception.GlobalExceptionHandler}.
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message) {
        super(message);
    }
}
