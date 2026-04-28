package com.jtx.desktop.data.local

import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.flow.Flow

interface LocalDataSource {
    fun getAllJournals(includeArchived: Boolean = false): Flow<List<JournalEntry>>
    fun getAllNotes(includeArchived: Boolean = false): Flow<List<NoteEntry>>
    fun getAllTasks(includeArchived: Boolean = false): Flow<List<TaskEntry>>

    suspend fun getJournalById(id: String): JournalEntry?
    suspend fun getNoteById(id: String): NoteEntry?
    suspend fun getTaskById(id: String): TaskEntry?

    suspend fun insertJournal(entry: JournalEntry)
    suspend fun insertNote(entry: NoteEntry)
    suspend fun insertTask(entry: TaskEntry)

    suspend fun updateJournal(entry: JournalEntry)
    suspend fun updateNote(entry: NoteEntry)
    suspend fun updateTask(entry: TaskEntry)

    suspend fun deleteJournal(id: String)
    suspend fun deleteNote(id: String)
    suspend fun deleteTask(id: String)

    suspend fun restoreFromArchive(type: EntryType, id: String)
    suspend fun permanentlyDeleteJournal(id: String)
    suspend fun permanentlyDeleteNote(id: String)
    suspend fun permanentlyDeleteTask(id: String)

    suspend fun getSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)
}
