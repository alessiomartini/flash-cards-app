package com.engvocab.app.data.db

import androidx.room.TypeConverter
import com.engvocab.core.importer.ImportSource
import com.engvocab.core.model.CardState
import com.engvocab.core.model.TargetLanguage

class Converters {
    @TypeConverter
    fun cardStateToString(state: CardState): String = state.name

    @TypeConverter
    fun stringToCardState(value: String): CardState = CardState.valueOf(value)

    @TypeConverter
    fun importSourceToString(source: ImportSource): String = source.name

    @TypeConverter
    fun stringToImportSource(value: String): ImportSource = ImportSource.valueOf(value)

    @TypeConverter
    fun cardTypeToString(type: CardType): String = type.name

    @TypeConverter
    fun stringToCardType(value: String): CardType = CardType.valueOf(value)

    @TypeConverter
    fun targetLanguageToString(language: TargetLanguage): String = language.name

    @TypeConverter
    fun stringToTargetLanguage(value: String): TargetLanguage = TargetLanguage.valueOf(value)
}
