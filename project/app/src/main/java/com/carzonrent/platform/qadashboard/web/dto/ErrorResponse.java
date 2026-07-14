package com.carzonrent.platform.qadashboard.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String path,
        String correlationId
) {
    public static ErrorResponse of(int status, String error, String path, String correlationId) {
        return new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                status,
                error,
                path,
                correlationId
        );
    }
}
