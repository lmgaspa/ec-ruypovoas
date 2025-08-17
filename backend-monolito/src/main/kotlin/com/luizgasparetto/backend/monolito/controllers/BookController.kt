package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.dto.BookDTO
import com.luizgasparetto.backend.monolito.services.BookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/books")
class BookController(private val bookService: BookService) {

    @GetMapping
    fun listBooks(): ResponseEntity<List<BookDTO>> =
        ResponseEntity.ok(bookService.getAllBooks())

    @GetMapping("/{id}")
    fun getBook(@PathVariable id: String): ResponseEntity<BookDTO> =
        ResponseEntity.ok(bookService.getBookDtoById(id))
}

