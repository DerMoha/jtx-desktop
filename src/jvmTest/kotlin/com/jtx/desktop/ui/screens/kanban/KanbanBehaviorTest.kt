package com.jtx.desktop.ui.screens.kanban

import com.jtx.desktop.domain.model.KanbanColumnConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class KanbanBehaviorTest {
    @Test
    fun normalizedColumnsUseDefaultsAndClampInvalidRanges() {
        val defaults = emptyList<KanbanColumnConfig>().normalizedColumns()
        assertEquals(listOf("todo", "inprogress", "done"), defaults.map { it.id })

        val normalized = listOf(
            KanbanColumnConfig("bad", "Bad", 0xFF000000, 120, -10),
            KanbanColumnConfig("wide", "Wide", 0xFF000000, 20, 80)
        ).normalizedColumns()

        assertEquals(0, normalized[0].progressMin)
        assertEquals(100, normalized[0].progressMax)
        assertEquals(20, normalized[1].progressMin)
        assertEquals(80, normalized[1].progressMax)
    }

    @Test
    fun progressForColumnChoosesStableDropTargets() {
        assertEquals(0, progressForColumn(KanbanColumnConfig("todo", "To Do", 0xFF000000, 0, 0)))
        assertEquals(50, progressForColumn(KanbanColumnConfig("doing", "Doing", 0xFF000000, 1, 99)))
        assertEquals(100, progressForColumn(KanbanColumnConfig("done", "Done", 0xFF000000, 100, 100)))
    }
}
