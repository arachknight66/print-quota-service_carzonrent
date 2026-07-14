package com.printkeep.quota.core.proxy.controller;

import com.printkeep.quota.core.proxy.service.PrinterProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Controller exposing REST mapping for application/ipp print traffic interception.
 */
@RestController
public class IppProxyController {

    private final PrinterProxyService proxyService;

    public IppProxyController(final PrinterProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * Intercepts, processes, and proxies incoming IPP binary POST payloads.
     *
     * @param printerName target logical printer destination.
     * @param request     HttpServletRequest carrying the print binary payload.
     * @param response    HttpServletResponse to relay printer stream packets.
     * @throws IOException if I/O failures occur during stream relays.
     */
    @PostMapping(value = "/printers/{printerName}", consumes = "application/ipp", produces = "application/ipp")
    public void handleIppRequest(
            @PathVariable("printerName") final String printerName,
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException {

        final String correlationId = MDC.get("correlationId");
        final String clientHost = request.getRemoteHost();

        response.setContentType("application/ipp");

        proxyService.proxyPrintJob(
                request.getInputStream(),
                response.getOutputStream(),
                printerName,
                correlationId,
                clientHost
        );
    }
}
