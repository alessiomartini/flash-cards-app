package com.lexi.flashcards

import android.app.Application
import com.lexi.flashcards.di.AppContainer

class LexiApplication : Application() {
    val container by lazy { AppContainer(this) }
}
