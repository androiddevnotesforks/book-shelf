package com.grayseal.bookshelf.screens.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.Text
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.grayseal.bookshelf.R
import com.grayseal.bookshelf.components.Category
import com.grayseal.bookshelf.components.NavBar
import com.grayseal.bookshelf.components.Reading
import com.grayseal.bookshelf.data.categories
import com.grayseal.bookshelf.model.Book
import com.grayseal.bookshelf.model.MyUser
import com.grayseal.bookshelf.navigation.BookShelfScreens
import com.grayseal.bookshelf.screens.login.LoginScreen
import com.grayseal.bookshelf.screens.login.LoginScreenViewModel
import com.grayseal.bookshelf.screens.login.StoreUserName
import com.grayseal.bookshelf.screens.search.SearchBookViewModel
import com.grayseal.bookshelf.ui.theme.*
import com.grayseal.bookshelf.utils.rememberFirebaseAuthLauncher
import com.grayseal.bookshelf.widgets.BookShelfNavigationDrawerItem
import kotlinx.coroutines.launch
import java.util.*

/**
 * Composable function that displays the Home Screen of the app.
 *
 * @param navController The NavController object for navigating between screens.
 * @param viewModel The LoginScreenViewModel object for managing user login and authentication.
 * @param searchBookViewModel The SearchBookViewModel object for managing book search functionality.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: LoginScreenViewModel = hiltViewModel(),
    searchBookViewModel: SearchBookViewModel
) {
    var user by remember { mutableStateOf(Firebase.auth.currentUser) }
    var readingList by remember {
        mutableStateOf(mutableListOf<Book>())
    }
    val context = LocalContext.current
    val userId = user?.uid

    var loading by remember {
        mutableStateOf(true)
    }

    // Get books in the reading now shelf
    if (userId != null) {
        val db = FirebaseFirestore.getInstance().collection("users").document(userId)
        db.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val shelves = documentSnapshot.toObject<MyUser>()?.shelves
                if (shelves != null) {
                    val shelf = shelves.find { it.name == "Reading Now 📖" }
                    if (shelf != null) {
                        val books = shelf.books as MutableList<Book>
                        readingList = books

                    } else {
                        Toast.makeText(context, "Error fetching reading List", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } else {
                Toast.makeText(context, "User does not exist", Toast.LENGTH_SHORT).show()
            }
            loading = false
        }.addOnFailureListener {
            Toast.makeText(context, "Error fetching reading List", Toast.LENGTH_SHORT).show()
        }
    }

    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.profile)

    var avatar: Bitmap = bitmap

    // Retrieve avatar from dataStore is user has set any
    val imageDataStore = StoreProfileImage(context)
    val session = StoreSession(context)
    val imagePath = imageDataStore.getImagePath.collectAsState(initial = "").value

    if (imagePath != "") {
        val imageUri = Uri.parse(imagePath)

        if (!session.isFirstTime) {
            try {
                // convert imageUri to bitmap
                imageUri?.let {
                    avatar = if (Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images
                            .Media.getBitmap(context.contentResolver, it)

                    } else {
                        val source = ImageDecoder
                            .createSource(context.contentResolver, it)
                        ImageDecoder.decodeBitmap(source)
                    }
                }
            } catch (e: Exception) {
                // Toast.makeText(context, "Failed to fetch stored profile Image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scope = rememberCoroutineScope()
    // Retrieve user name from dataStore
    val nameDataStore = StoreUserName(context)
    val launcher: ManagedActivityResultLauncher<Intent, ActivityResult> =
        rememberFirebaseAuthLauncher(
            onAuthComplete = { result ->
                user = result.user
                scope.launch {
                    user?.displayName?.let { nameDataStore.saveName(it) }
                }
            },
            onAuthError = {
                user = null
            }
        )
    if (user == null) {
        LoginScreen(navController, launcher, viewModel, nameDataStore)
    } else {
        val name = nameDataStore.getName.collectAsState(initial = "")
        // Main Screen Content
        HomeContent(
            user = user!!,
            name = name.value,
            avatar = avatar,
            navController = navController,
            searchBookViewModel = searchBookViewModel,
            imageDataStore = imageDataStore,
            session = session,
            reading = readingList,
            loading = loading
        )
    }
}

/**
 * A composable function that displays the home screen for the user, including a drawer that contains
 * the options to log out, delete the account, and open the user settings. This composable function
 * takes several parameters to display user data and allow the user to interact with the app.
 *
 * @param user the FirebaseUser object that represents the user that is currently logged in
 * @param name a String object that represents the name of the user
 * @param avatar a Bitmap object that represents the user's profile image
 * @param navController a NavController object that controls navigation within the app
 * @param searchBookViewModel a SearchBookViewModel object that allows the user to search for books within the app
 * @param imageDataStore a StoreProfileImage object that stores the user's profile image
 * @param session a StoreSession object that stores the user's session information
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    user: FirebaseUser,
    name: String?,
    avatar: Bitmap,
    navController: NavController,
    searchBookViewModel: SearchBookViewModel,
    imageDataStore: StoreProfileImage,
    session: StoreSession,
    reading: List<Book>,
    loading: Boolean
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var currentRead:Book by remember {
        mutableStateOf(Book())
    }
    currentRead = try{
        reading.first()
    }
    catch (e:Exception){
        Book()
    }

    val readingBooksTotal = reading.size
    val scope = rememberCoroutineScope()
    val items = mapOf(
        "Settings" to R.drawable.settings,
        "Log Out" to R.drawable.logout,
        "Delete Account" to R.drawable.delete
    )
    val selectedItem = remember { mutableStateOf(items["Settings"]) }
    var openDialog by remember {
        mutableStateOf(false)
    }

    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }
    val context = LocalContext.current
    val bitmap = remember {
        mutableStateOf<Bitmap>(avatar)
    }
    // Retrieve an image from the device gallery
    val launcher = rememberLauncherForActivityResult(
        contract =
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        // take a persistable URI permission to access the content of the URI outside of the scope of app's process
        val contentResolver = context.contentResolver
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Toast.makeText(
                    context,
                    "Failed to take permission: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        imageUri = uri
    }

    imageUri?.let {
        if (Build.VERSION.SDK_INT < 28) {
            bitmap.value = MediaStore.Images
                .Media.getBitmap(context.contentResolver, it)

        } else {
            val source = ImageDecoder
                .createSource(context.contentResolver, it)
            bitmap.value = ImageDecoder.decodeBitmap(source)
        }
        // Update user profile image on dataStore and set firstSession to false
        scope.launch {
            val path = imageUri ?: return@launch
            Log.d("PATH", "$path")
            imageDataStore.saveImagePath(path)
            session.setIsFirstTimeLaunch(false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerShape = RectangleShape,
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerTonalElevation = 0.dp,
            ) {
                Spacer(Modifier.height(30.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 20.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConstraintLayout {
                        val (profile, edit) = createRefs()
                        Surface(
                            modifier = Modifier
                                .size(60.dp)
                                .background(color = Color.Transparent, shape = CircleShape)
                                .constrainAs(profile) {
                                    top.linkTo(parent.top)
                                    start.linkTo(parent.start)
                                }
                                .clickable(onClick = {
                                    launcher.launch(arrayOf("image/*"))
                                }),
                            shape = CircleShape,
                        ) {
                            val image = if (session.isFirstTime) {
                                bitmap.value
                            } else {
                                avatar
                            }
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = {
                                        launcher.launch(arrayOf("image/*"))
                                    }),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .size(25.dp)
                                // Clip image to be shaped as a circle
                                .clip(CircleShape)
                                .constrainAs(edit) {
                                    top.linkTo(profile.bottom)
                                    end.linkTo(profile.absoluteRight)
                                    baseline.linkTo(profile.baseline)
                                    bottom.linkTo(profile.bottom)
                                }
                                .clickable(onClick = {
                                    launcher.launch(arrayOf("image/*"))
                                }),
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            border = BorderStroke(width = 1.dp, color = Color.White)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.pen),
                                contentDescription = "Update Profile Picture",
                                modifier = Modifier
                                    .padding(3.dp)
                            )
                        }

                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Hi, ${name}!", fontFamily = loraFamily,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "$readingBooksTotal books in your reading list", fontFamily = poppinsFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(10.dp))
                    Divider(color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(20.dp))
                    items.forEach { item ->
                        val selectedValue = selectedItem.value
                        val selectedKey = items.entries.find { it.value == selectedValue }?.key
                        AlertDialog(
                            openDialog = openDialog,
                            title = if (selectedKey == "Log Out") {
                                "Confirm Logout"
                            } else {
                                "Delete Account"
                            },
                            details = if (selectedKey == "Log Out") {
                                "Are you sure you want to end your session?"
                            } else {
                                "You are about to delete your account. Is this what you want?"
                            },
                            onDismiss = {
                                openDialog = false
                            },
                            onClick = {
                                if (selectedKey == "Log Out") {
                                    Firebase.auth.signOut()
                                    navController.navigate(BookShelfScreens.HomeScreen.name)
                                } else {
                                    user.delete()
                                    navController.navigate(BookShelfScreens.HomeScreen.name)
                                }
                            }
                        )
                        BookShelfNavigationDrawerItem(
                            icon = {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(id = item.value),
                                    contentDescription = item.key,
                                    modifier = Modifier
                                        .size(35.dp)
                                        .background(color = Color.Transparent)
                                )
                            },
                            label = {
                                androidx.compose.material3.Text(
                                    item.key,
                                    fontFamily = poppinsFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            },
                            selected = item.value == selectedItem.value,
                            onClick = {
                                selectedItem.value = item.value
                                if (item.key == "Log Out" || item.key == "Delete Account") {
                                    openDialog = true
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.background,
                                unselectedContainerColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                }
            }
        },
        scrimColor = Color.Transparent,
        content = {
            Scaffold(content = { padding ->
                Column(
                    modifier = Modifier
                        .padding(top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    TopHeader(navController, searchBookViewModel, avatar = avatar) {
                        scope.launch {
                            drawerState.open()
                        }
                    }
                    MainCard(currentRead, navController)
                    Categories(navController = navController)
                        ReadingList(
                            navController,
                            loading = loading,
                            readingList = reading
                        )
                }
            },
                bottomBar = {
                    NavBar(navController)
                })
        }
    )
}

/**
 * A composable function that displays the top header of a bookshelf app with a profile picture
 * and a search button. Clicking on the profile picture opens the user's profile, and clicking
 * on the search button navigates to the search screen.
 *
 * @param navController The NavController object used to navigate between different screens.
 * @param viewModel The SearchBookViewModel object that contains the state of the search screen.
 * @param avatar The Bitmap object that represents the user's profile picture.
 * @param onProfileClick A lambda function that is executed when the user clicks on the profile picture.
 */
@Composable
fun TopHeader(
    navController: NavController,
    viewModel: SearchBookViewModel,
    avatar: Bitmap,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .background(color = Color.Transparent, shape = CircleShape),
            shape = CircleShape,
        ) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = true,
                        onClick = {
                            onProfileClick()
                        },
                    ),
                contentScale = ContentScale.FillBounds
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = true, onClick = {
                        viewModel.loading.value = false
                        viewModel.listOfBooks.value = listOf()
                        navController.navigate(route = BookShelfScreens.SearchScreen.name)
                    }),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(
                    width = 0.9.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = "Search",
                    modifier = Modifier
                        .size(20.dp)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(color = Color.Transparent, shape = CircleShape),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
                )
            }
        }
    }
}

/**

 * A composable function that creates a main card UI element.
 * The card displays an image with the title "Track your reading activity" and a book with a title and a "Continue reading" text.
 */
@Composable
fun MainCard(currentRead: Book, navController: NavController) {
    var loading by remember {
        mutableStateOf(false)
    }
    Card(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.card),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Track your", fontFamily = poppinsFamily,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "reading activity", fontFamily = poppinsFamily,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .padding(15.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(15.dp)

                        )
                        .clickable(onClick = {
                            navController.navigate(route = BookShelfScreens.BookScreen.name + "/${currentRead.bookID}")
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .background(color = Color.Transparent, shape = CircleShape),
                            shape = CircleShape,
                            border = BorderStroke(
                                width = 0.dp,
                                color = Pink200
                            )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentRead.imageLinks.thumbnail?.replace("http", "https"))
                                    .build(),
                                contentDescription = "Book Image",
                                contentScale = ContentScale.Inside,
                                modifier = Modifier
                                    .scale(2.2f),
                                onLoading = {
                                    loading = true
                                },
                                onSuccess = {
                                    loading= false
                                }
                            )
                            if (loading) {
                                androidx.compose.material3.CircularProgressIndicator(color = Yellow)
                            }
                        }
                        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Text(
                                currentRead.title,
                                fontFamily = poppinsFamily,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                "Continue reading", fontFamily = poppinsFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A composable function that displays a list of categories using a LazyRow layout. Each category is
 * represented by a Category composable that is clickable and navigates to the corresponding screen.
 * @param navController The NavController used for navigating between screens.
 */
@Composable
fun Categories(navController: NavController) {
    Text(
        "Categories",
        fontFamily = poppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp)
    )
    val keysList = categories.keys.toList()
    LazyRow(
        modifier = Modifier.padding(bottom = 20.dp, end = 0.dp, start = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        itemsIndexed(items = keysList) { index: Int, item: String ->
            if (index == 0) {
                Spacer(modifier = Modifier.width(20.dp))
                categories[item]?.let {
                    Category(
                        category = item,
                        image = it,
                        onClick = { navController.navigate(route = BookShelfScreens.CategoryScreen.name + "/$item") })
                }
            } else {
                Category(
                    category = item,
                    image = categories[item]!!,
                    onClick = { navController.navigate(route = BookShelfScreens.CategoryScreen.name + "/$item") })
            }
        }
    }
}

/**

 * Composable function that displays the user's reading list, with the book cover images,
 * the title and author of the book, and an onClick listener that navigates to a book detail screen.
 * @param onClick The function that handles clicks on the reading list items.
 * */

@Composable
fun ReadingList(navController: NavController, loading: Boolean, readingList: List<Book>) {
    Text(
        "Reading List",
        fontFamily = poppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp)
    )
    if(loading){
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.LinearProgressIndicator(color = Yellow)
        }
    } else {
        LazyRow(
            modifier = Modifier
                .padding(start = 0.dp, end = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(items = readingList) { index: Int, item: Book ->
                if (index == 0) {
                    Spacer(modifier = Modifier.width(20.dp))
                    item.imageLinks.thumbnail?.let {
                        Reading(
                            bookAuthor = item.authors[0],
                            bookTitle = item.title,
                            imageUrl = it.replace("http", "https"),
                            onClick = { navController.navigate(route = BookShelfScreens.BookScreen.name + "/${item.bookID}") },
                        )
                    }
                } else {
                    item.imageLinks.thumbnail?.let {
                        Reading(
                            bookAuthor = item.authors[0],
                            bookTitle = item.title,
                            imageUrl = it.replace("http", "https"),
                            onClick = { navController.navigate(route = BookShelfScreens.BookScreen.name + "/${item.bookID}") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A composable function that displays an alert dialog with a title, details, a confirm button,
 * and a cancel button.
 * @param openDialog A boolean that represents whether the alert dialog should be displayed.
 * @param title A string representing the title of the alert dialog.
 * @param details A string representing the details of the alert dialog.
 * @param onDismiss A lambda function that is called when the user dismisses the alert dialog.
 * @param onClick A lambda function that is called when the user confirms the alert dialog.
 */
@Composable
fun AlertDialog(
    openDialog: Boolean,
    title: String,
    details: String,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    if (openDialog) {
        AlertDialog(
            /* Dismiss the dialog when the user clicks outside the dialog or on the back
                   button. */
            onDismissRequest = onDismiss,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Info",
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        title,
                        fontSize = 18.sp,
                        fontFamily = poppinsFamily,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = details,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontFamily = poppinsFamily,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onClick) {
                    Text(
                        "Confirm",
                        fontSize = 15.sp,
                        fontFamily = poppinsFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        fontSize = 15.sp,
                        fontFamily = poppinsFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}
