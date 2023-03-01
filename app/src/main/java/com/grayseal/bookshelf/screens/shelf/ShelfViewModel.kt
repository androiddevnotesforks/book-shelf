package com.grayseal.bookshelf.screens.shelf

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.grayseal.bookshelf.model.Book
import com.grayseal.bookshelf.model.MyUser
import com.grayseal.bookshelf.model.Shelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ShelfViewModel : ViewModel() {
    var booksInShelf: MutableState<MutableList<Book>> = mutableStateOf(mutableListOf())
    var shelves: MutableState<List<Shelf>> = mutableStateOf(mutableListOf())
    var favourites: MutableState<List<Book>> = mutableStateOf(mutableListOf())

    // Get books in a particular shelf
    fun getBooksInAShelf(
        userId: String?,
        context: Context,
        shelfName: String,
        onDone: () -> Unit
    ): MutableList<Book> {
        if (userId != null) {
            val db = FirebaseFirestore.getInstance().collection("users").document(userId)
            db.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val shelves = documentSnapshot.toObject<MyUser>()?.shelves
                    if (shelves != null) {
                        val shelf = shelves.find { it.name == shelfName }
                        if (shelf != null) {
                            booksInShelf.value = shelf.books as MutableList<Book>
                        } else {
                            Toast.makeText(
                                context,
                                "Error fetching books",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Error fetching books",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onDone()
            }.addOnFailureListener {
                Toast.makeText(
                    context,
                    "$it",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return booksInShelf.value
    }

    // Get shelves belonging to a particular user
    fun getShelves(
        userId: String?,
        context: Context,
        onDone: () -> Unit
    ): List<Shelf> {
        if (userId != null) {
            val db = FirebaseFirestore.getInstance().collection("users").document(userId)
            db.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val userShelves = documentSnapshot.toObject<MyUser>()?.shelves
                    if (userShelves != null) {
                        shelves.value = userShelves
                    } else {
                        Toast.makeText(context, "Error fetching Shelves", Toast.LENGTH_SHORT)
                            .show()
                    }
                    onDone()
                }
            }
        }
        return shelves.value
    }

    suspend fun addFavourite(
        userId: String?,
        book: Book,
    ): Boolean = withContext(Dispatchers.IO){
        if (userId != null) {
            val db = FirebaseFirestore.getInstance().collection("users").document(userId)
            db.get().await().let { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val favourites =
                        documentSnapshot.toObject<MyUser>()?.favourites as MutableList<Book>
                    favourites.add(book)
                    db.update("favourites", favourites).await()
                    return@withContext true
                }
            }
        }
        return@withContext false
    }

    // Remove book from favourites
    suspend fun removeFavourite(
        userId: String?,
        book: Book,
    ): Boolean = withContext(Dispatchers.IO){
        if (userId != null) {
            val db = FirebaseFirestore.getInstance().collection("users").document(userId)
            db.get().await().let { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val favourites =
                        documentSnapshot.toObject<MyUser>()?.favourites as MutableList<Book>
                    favourites.remove(book)
                    db.update("favourites", favourites).await()
                    return@withContext true
                }
            }
        }
        return@withContext false
    }

    // Fetch favourites
    fun fetchFavourites(
        userId: String?,
        onDone: () -> Unit
    ): List<Book> {
        if (userId != null) {
            val db = FirebaseFirestore.getInstance().collection("users").document(userId)
            db.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    favourites.value =
                        documentSnapshot.toObject<MyUser>()?.favourites as MutableList<Book>
                    onDone()
                }
            }
        }
        return favourites.value
    }
}