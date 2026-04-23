package com.jtx.desktop.data.local

import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryLocalDataSource {
    private val _journals = MutableStateFlow<List<JournalEntry>>(emptyList())
    private val _notes = MutableStateFlow<List<NoteEntry>>(emptyList())
    private val _tasks = MutableStateFlow<List<TaskEntry>>(emptyList())
    private var settings = AppSettings()

    fun getAllJournals(): Flow<List<JournalEntry>> = _journals.asStateFlow()
    fun getAllNotes(): Flow<List<NoteEntry>> = _notes.asStateFlow()
    fun getAllTasks(): Flow<List<TaskEntry>> = _tasks.asStateFlow()

    suspend fun getJournalById(id: String): JournalEntry? = _journals.value.find { it.id == id }
    suspend fun getNoteById(id: String): NoteEntry? = _notes.value.find { it.id == id }
    suspend fun getTaskById(id: String): TaskEntry? = _tasks.value.find { it.id == id }

    suspend fun insertJournal(entry: JournalEntry) {
        _journals.value = _journals.value + entry
    }

    suspend fun insertNote(entry: NoteEntry) {
        _notes.value = _notes.value + entry
    }

    suspend fun insertTask(entry: TaskEntry) {
        _tasks.value = _tasks.value + entry
    }

    suspend fun updateJournal(entry: JournalEntry) {
        _journals.value = _journals.value.filter { it.id != entry.id } + entry
    }

    suspend fun updateNote(entry: NoteEntry) {
        _notes.value = _notes.value.filter { it.id != entry.id } + entry
    }

    suspend fun updateTask(entry: TaskEntry) {
        _tasks.value = _tasks.value.filter { it.id != entry.id } + entry
    }

    suspend fun deleteJournal(id: String) {
        _journals.value = _journals.value.filter { it.id != id }
    }

    suspend fun deleteNote(id: String) {
        _notes.value = _notes.value.filter { it.id != id }
    }

    suspend fun deleteTask(id: String) {
        _tasks.value = _tasks.value.filter { it.id != id }
    }

    suspend fun getSettings(): AppSettings = settings

    suspend fun saveSettings(newSettings: AppSettings) {
        settings = newSettings
    }
}

fun createSampleData(): InMemoryLocalDataSource {
    val dataSource = InMemoryLocalDataSource()
    return dataSource
}