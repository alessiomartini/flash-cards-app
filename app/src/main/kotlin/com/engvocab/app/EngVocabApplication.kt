package com.engvocab.app

import android.app.Application
import com.engvocab.app.di.AppContainer

class EngVocabApplication : Application() {
    val container by lazy { AppContainer(this) }
}
