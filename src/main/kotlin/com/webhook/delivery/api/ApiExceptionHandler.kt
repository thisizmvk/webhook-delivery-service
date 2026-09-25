package com.webhook.delivery.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(DeliveryNotFoundException::class)
    fun handleNotFound(ex: DeliveryNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message).apply {
            title = "Delivery not found"
        }

    // Adds a field -> message map so clients can see exactly what was wrong.
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        ex.body.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") },
        )
        return super.handleMethodArgumentNotValid(ex, headers, status, request)
    }
}