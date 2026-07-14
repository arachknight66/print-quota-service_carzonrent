package com.carzonrent.platform.qadashboard.web;

import com.carzonrent.platform.qadashboard.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Controller
@ControllerAdvice
public class GlobalExceptionHandler implements ErrorController {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        HttpStatus.NOT_FOUND.getReasonPhrase(),
                        request.getRequestURI(),
                        MDC.get("correlationId")
                ));
    }

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute("jakarta.servlet.error.status_code");
        int statusCode = statusAttribute instanceof Integer code ? code : 500;
        HttpStatus status = HttpStatus.resolve(statusCode);
        HttpStatus resolvedStatus = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        String path = String.valueOf(request.getAttribute("jakarta.servlet.error.request_uri"));
        String correlationId = MDC.get("correlationId");
        return ResponseEntity
                .status(resolvedStatus)
                .body(ErrorResponse.of(statusCode, resolvedStatus.getReasonPhrase(), path, correlationId));
    }
}
