package com.genealogy.platform.services.tenant.web;

import com.genealogy.platform.services.tenant.application.EntitlementCommandService;
import com.genealogy.platform.services.tenant.application.MembershipCommandService;
import com.genealogy.platform.services.tenant.application.TenantCommandService;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates application-layer exceptions into RFC 9457
 * {@code application/problem+json} responses. Every handler returns
 * the canonical {@link ProblemDetail} shape produced by
 * {@link TenantProblems} so the wire format stays uniform across
 * controllers.
 *
 * <p>Cross-tenant attempts (a request that names {@code tenantId=A}
 * but the trusted context carries {@code tenantId=B}) are answered
 * with {@code 404 Not Found} rather than {@code 403 Forbidden} so a
 * probe cannot distinguish "tenant exists, you cannot access it"
 * from "tenant does not exist".
 */
@RestControllerAdvice(basePackages = "com.genealogy.platform.services.tenant.web")
public class TenantExceptionHandler {

    @ExceptionHandler({
            TenantCommandService.TenantNotFoundException.class,
            TenantCommandService.SlugAlreadyExistsException.class,
            MembershipCommandService.MembershipNotFoundException.class,
            MembershipCommandService.InvalidInviteTokenException.class,
            MembershipCommandService.InvalidMembershipStateException.class,
            MembershipCommandService.CrossTenantMembershipException.class,
            EntitlementCommandService.EntitlementNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleDomainErrors(RuntimeException ex) {
        HttpStatus status;
        String errorCode;
        if (ex instanceof TenantCommandService.TenantNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            errorCode = TenantProblems.ERR_TENANT_NOT_FOUND;
        } else if (ex instanceof TenantCommandService.SlugAlreadyExistsException) {
            status = HttpStatus.CONFLICT;
            errorCode = TenantProblems.ERR_SLUG_CONFLICT;
        } else if (ex instanceof MembershipCommandService.MembershipNotFoundException
                || ex instanceof MembershipCommandService.CrossTenantMembershipException) {
            status = HttpStatus.NOT_FOUND;
            errorCode = TenantProblems.ERR_MEMBERSHIP_NOT_FOUND;
        } else if (ex instanceof MembershipCommandService.InvalidInviteTokenException) {
            status = HttpStatus.BAD_REQUEST;
            errorCode = TenantProblems.ERR_INVALID_INVITE;
        } else if (ex instanceof MembershipCommandService.InvalidMembershipStateException) {
            status = HttpStatus.CONFLICT;
            errorCode = TenantProblems.ERR_IDEMPOTENCY_CONFLICT;
        } else if (ex instanceof EntitlementCommandService.EntitlementNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            errorCode = TenantProblems.ERR_ENTITLEMENT_NOT_FOUND;
        } else {
            status = HttpStatus.BAD_REQUEST;
            errorCode = TenantProblems.ERR_INVALID_REQUEST;
        }
        return ResponseEntity.status(status)
                .contentType(TenantProblems.contentType())
                .body(TenantProblems.of(status, errorCode, ex.getMessage()));
    }

    @ExceptionHandler({
            TenantCommandService.OptimisticConcurrencyException.class,
            TenantRepository.OptimisticConcurrencyException.class
    })
    public ResponseEntity<ProblemDetail> handleOptimisticConcurrency(RuntimeException ex) {
        HttpStatus status = HttpStatus.PRECONDITION_FAILED;
        ProblemDetail body = TenantProblems.of(status,
                TenantProblems.ERR_INVALID_ETAG, ex.getMessage());
        return ResponseEntity.status(status)
                .header(HttpHeaders.ETAG, "\"\"")
                .contentType(TenantProblems.contentType())
                .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .contentType(TenantProblems.contentType())
                .body(TenantProblems.of(status,
                        TenantProblems.ERR_INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElseGet(ex::getMessage);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .contentType(TenantProblems.contentType())
                .body(TenantProblems.of(status,
                        TenantProblems.ERR_INVALID_REQUEST, detail));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(TenantProblems.of(status,
                        TenantProblems.ERR_IDEMPOTENCY_CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleMissing(java.util.NoSuchElementException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .contentType(TenantProblems.contentType())
                .body(TenantProblems.of(status,
                        TenantProblems.ERR_TENANT_NOT_FOUND, ex.getMessage()));
    }
}
