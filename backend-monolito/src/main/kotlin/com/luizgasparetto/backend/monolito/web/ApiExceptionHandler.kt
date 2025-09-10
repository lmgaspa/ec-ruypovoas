package com.luizgasparetto.backend.monolito.web

import com.luizgasparetto.backend.monolito.exceptions.ReservationConflictException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    data class ApiError(
        val code: String,
        val message: String
    )

    @ExceptionHandler(ReservationConflictException::class)
    fun handleReservationConflict(ex: ReservationConflictException): ResponseEntity<ApiError> {
        log.info("Reserva em conflito para bookId={}: {}", ex.bookId, ex.message)
        return ResponseEntity
            .status(HttpStatus.CONFLICT) // 409
            .body(ApiError(code = "OUT_OF_STOCK", message = ex.message ?: "Indisponível"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(ex: IllegalArgumentException): ResponseEntity<ApiError> {
        // Muitos lugares usam IllegalArgumentException para “estoque insuficiente”
        log.info("Bad request / conflito de negócio: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.CONFLICT) // 409 em vez de 500
            .body(ApiError(code = "OUT_OF_STOCK", message = ex.message ?: "Indisponível"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ApiError> {
        log.error("Erro não tratado", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(code = "INTERNAL_ERROR", message = "Erro ao processar o checkout. Tente novamente."))
    }
}