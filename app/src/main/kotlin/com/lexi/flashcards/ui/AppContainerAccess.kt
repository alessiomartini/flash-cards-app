package com.lexi.flashcards.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lexi.flashcards.LexiApplication
import com.lexi.flashcards.di.AppContainer

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current.applicationContext as LexiApplication
    return context.container
}
