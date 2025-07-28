package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.Book
import org.springframework.data.jpa.repository.JpaRepository

interface BookRepository : JpaRepository<Book, String>
