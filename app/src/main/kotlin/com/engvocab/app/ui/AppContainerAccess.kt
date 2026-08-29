package com.engvocab.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.engvocab.app.EngVocabApplication
import com.engvocab.app.di.AppContainer

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current.applicationContext as EngVocabApplication
    return context.container
}
