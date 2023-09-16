package mobi.sevenwinds.app.budget

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * @author osadchiy.ia
 */
object AuthorTable : IntIdTable("author") {
    val authorName = text("author_name")
    val created = datetime("created")
}

class AuthorEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AuthorEntity>(AuthorTable)

    var authorName by AuthorTable.authorName
    var created by AuthorTable.created

    fun toResponse(): AuthorRecord {
        return AuthorRecord(authorName)
    }
}