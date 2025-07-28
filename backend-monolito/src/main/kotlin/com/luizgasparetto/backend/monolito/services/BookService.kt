package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.Book
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.springframework.stereotype.Service

@Service
class BookService(private val bookRepository: BookRepository) {

    fun getAllBooks(): List<Book> = bookRepository.findAll()

    fun getBookById(id: String): Book =
        bookRepository.findById(id).orElseThrow { RuntimeException("Livro não encontrado") }

    fun updateStock(id: String, amount: Int) {
        val book = getBookById(id)
        book.stock -= amount
        bookRepository.save(book)
    }
}
