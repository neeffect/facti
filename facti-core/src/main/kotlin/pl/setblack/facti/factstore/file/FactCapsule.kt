package pl.setblack.facti.factstore.file

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

internal data class FactCapsule(
    val eventId: Long,
    val time: Instant,
    val eventContent: JsonNode,
    val eventClass: String)

