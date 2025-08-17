package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.dto.BookDTO
import com.luizgasparetto.backend.monolito.models.Book
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(private val bookRepository: BookRepository) {

    private fun toDto(it: Book): BookDTO {
        val s = it.stock ?: 0
        return BookDTO(
            id = it.id,
            title = it.title,
            imageUrl = it.imageUrl,
            price = it.price,
            description = it.description,
            author = it.author ?: "Desconhecido",
            category = it.category ?: "Desconhecido",
            stock = s,
            available = s > 0
        )
    }

    fun getAllBooks(): List<BookDTO> =
        bookRepository.findAll().map(::toDto)

    fun getBookDtoById(id: String): BookDTO =
        bookRepository.findById(id).map(::toDto)
            .orElseThrow { NoSuchElementException("Livro $id não encontrado") }

    fun getBookById(id: String): Book =
        bookRepository.findById(id).orElseThrow { RuntimeException("Livro não encontrado") }

    fun validateStock(id: String, amount: Int) {
        val book = getBookById(id)
        if ((book.stock ?: 0) < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
    }

    fun getImageUrl(bookId: String): String {
        val book = bookRepository.findById(bookId)
            .orElseThrow { RuntimeException("Livro $bookId não encontrado") }
        return book.imageUrl ?: ""
    }

    @Transactional
    fun updateStock(id: String, amount: Int) {
        val book = getBookById(id)
        if ((book.stock ?: 0) < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
        book.stock = (book.stock ?: 0) - amount
        bookRepository.save(book)
    }
}