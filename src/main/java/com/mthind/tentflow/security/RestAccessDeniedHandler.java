package com.mthind.tentflow.security;

import com.mthind.tentflow.audit.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter errorWriter;
    private final AuditService auditService;

    public RestAccessDeniedHandler(
            ApiErrorWriter errorWriter,
            AuditService auditService
    ) {
        this.errorWriter = errorWriter;
        this.auditService = auditService;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        auditService.failureInNewTransaction(
                "ACCESS_DENIED",
                "HTTP_REQUEST",
                null,
                "path=" + request.getRequestURI()
        );
        errorWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action."
        );
    }
}