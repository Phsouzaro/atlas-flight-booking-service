package com.atlas.shared.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import io.micrometer.tracing.Tracer;

/**
 * Põe {@code traceId} em todo erro problem+json, venha ele de qualquer módulo ou do próprio Spring. A UI mostra
 * esse código em erro inesperado; com ele se acha o trace e os logs do request.
 */
@RestControllerAdvice
class ProblemTraceAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectProvider<Tracer> tracer;

    ProblemTraceAdvice(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            TraceHeaders.currentTraceId(tracer).ifPresent(id -> problem.setProperty("traceId", id));
        }
        return body;
    }
}
