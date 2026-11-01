package com.hmg.safelog.app

import com.hmg.safelog.core.EventRecord
import com.hmg.safelog.core.EventStore
import java.io.File

/** One JSON-lines file per event under [dir]. */
class FileEventStore(private val dir: File) : EventStore {
    override fun save(record: EventRecord) {
        dir.mkdirs()
        File(dir, "event-${record.type}-${record.triggerMs}.jsonl")
            .writeText(record.toJsonLines().joinToString("\n", postfix = "\n"))
    }
}
