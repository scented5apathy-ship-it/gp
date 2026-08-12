package com.genealogy.platform.services.research.web;

import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.persistence.RepositorySupport;
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
 * {@code application/problem+json} responses. Every handler
 * returns the canonical {@link ProblemDetail} shape produced by
 * {@link ResearchProblems} so the wire format stays uniform
 * across controllers.
 *
 * <p>Cross-tenant attempts (a request that names a resource id
 * but the trusted context carries a different tenant) are
 * answered with {@code 404 Not Found} rather than
 * {@code 403 Forbidden} so a probe cannot distinguish "tenant
 * exists, you cannot access it" from "tenant does not exist".
 */
@RestControllerAdvice(basePackages = "com.genealogy.platform.services.research.web")
public class ResearchExceptionHandler {

    @ExceptionHandler({
            ResearchCommandService.RepositoryNotFoundException.class,
            ResearchCommandService.SourceNotFoundException.class,
            ResearchCommandService.CitationNotFoundException.class,
            ResearchCommandService.ResearchTaskNotFoundException.class,
            ResearchCommandService.HypothesisNotFoundException.class,
            ResearchCommandService.ConflictNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleDomainErrors(RuntimeException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        String errorCode;
        if (ex instanceof ResearchCommandService.RepositoryNotFoundException) {
            errorCode = ResearchProblems.ERR_REPOSITORY_NOT_FOUND;
        } else if (ex instanceof ResearchCommandService.SourceNotFoundException) {
            errorCode = ResearchProblems.ERR_SOURCE_NOT_FOUND;
        } else if (ex instanceof ResearchCommandService.CitationNotFoundException) {
            errorCode = ResearchProblems.ERR_CITATION_NOT_FOUND;
        } else if (ex instanceof ResearchCommandService.ResearchTaskNotFoundException) {
            errorCode = ResearchProblems.ERR_RESEARCH_TASK_NOT_FOUND;
        } else if (ex instanceof ResearchCommandService.HypothesisNotFoundException) {
            errorCode = ResearchProblems.ERR_HYPOTHESIS_NOT_FOUND;
        } else {
            errorCode = ResearchProblems.ERR_CONFLICT_NOT_FOUND;
        }
        return ResponseEntity.status(status)
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status, errorCode, ex.getMessage()));
    }

    @ExceptionHandler({
            ResearchCommandService.OptimisticConcurrencyException.class,
            RepositorySupport.OptimisticConcurrencyException.class
    })
    public ResponseEntity<ProblemDetail> handleOptimisticConcurrency(RuntimeException ex) {
        HttpStatus status = HttpStatus.PRECONDITION_FAILED;
        return ResponseEntity.status(status)
                .header(HttpHeaders.ETAG, "\"\"")
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_INVALID_ETAG, ex.getMessage()));
    }

    @ExceptionHandler(ResearchCommandService.InvalidTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(
            ResearchCommandService.InvalidTransitionException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_INVALID_TRANSITION, ex.getMessage()));
    }

    @ExceptionHandler(DraftDomainMapper.InvariantViolationException.class)
    public ResponseEntity<ProblemDetail> handleInvariant(DraftDomainMapper.InvariantViolationException ex) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_INVARIANT_VIOLATION,
                        ex.getMessage() + " (code=" + ex.finding().code().name() + ")"));
    }

    @ExceptionHandler(DraftDomainMapper.InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest(DraftDomainMapper.InvalidRequestException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElseGet(ex::getMessage);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .contentType(ResearchProblems.contentType())
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_INVALID_REQUEST, detail));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ResearchProblems.of(status,
                        ResearchProblems.ERR_IDEMPOTENCY_CONFLICT, ex.getMessage()));
    }
}
