package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import com.luizgasparetto.backend.monolito.dto.CheckoutResponse
import com.luizgasparetto.backend.monolito.models.Book
import com.luizgasparetto.backend.monolito.services.BookService
import com.luizgasparetto.backend.monolito.services.CheckoutService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/checkout")
class CheckoutController(
    private val checkoutService: CheckoutService
) {

    @PostMapping
    fun checkout(@RequestBody request: CheckoutRequest): ResponseEntity<CheckoutResponse> {
        val response = checkoutService.processCheckout(request)
        return ResponseEntity.ok(response)
    }

    @RestController
    @RequestMapping("/api/books")
    class BookController(private val bookService: BookService) {

        @GetMapping
        fun listBooks(): ResponseEntity<List<Book>> =
            ResponseEntity.ok(bookService.getAllBooks())
    }
}
