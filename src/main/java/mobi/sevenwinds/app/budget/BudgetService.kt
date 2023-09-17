package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            // Проверка на существование Автора с данным ID
            val author = body.authorId?.let {
                val authorQuery = AuthorTable.select { AuthorTable.id eq it }
                if (authorQuery.count() > 0) it else null
            }

            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.authorId = author
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = BudgetTable.select { BudgetTable.year eq param.year }

            val authorName = param.authorName
            //обрабатываем данные с фильтрацией и подтягиванием автора(если существует)
            val data = getFilteredData(query, authorName)

            //количество возможных записей без пагинации
            val total = data.count()
            //подсчитаем сумму(общая или с выборкой по совпадению Авторов)
            val sumByType = data.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            //Делаем выборку по лимиту со сдвигом с сортировкой
            val limitedQuery = query.limit(param.limit, param.offset)
                .orderBy(BudgetTable.month to SortOrder.ASC)
                .orderBy(BudgetTable.amount to SortOrder.DESC)

            //обрабатываем данные с фильтрацией и подтягиванием автора(если существует) для вывода
            val limitedData = getFilteredData(limitedQuery, authorName)

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = limitedData
            )
        }
    }

    /**
     * Загрузка данных Автора(если существует), фильтрация по AuthorName.
     * @param query данные из БД.
     * @param authorName String или Null.
     * @return List< BudgetRecordWithAuthorName>
     */
    private fun getFilteredData(query: Query, authorName: String?): List<BudgetRecordWithAuthorName> {

        val data = BudgetEntity.wrapRows(query)
            .map { it.toResponse() }
            .map { budgetRecord ->
                val authorId = budgetRecord.authorId

                if (authorId == null) {
                    return@map BudgetRecordWithAuthorName(budgetRecord)
                } else {
                    val authorQuery = AuthorTable.select { AuthorTable.id eq authorId }
                    val authorEntity = AuthorEntity.wrapRow(authorQuery.first())

                    val author = authorEntity.authorName
                    val authorCreated = authorEntity.created

                    //форматирование Даты и Времени
                    val pattern: DateTimeFormatter = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
                    val authorCreatedAtString = pattern.print(authorCreated)

                    return@map BudgetRecordWithAuthorName(budgetRecord, author, authorCreatedAtString)
                    }
                }

        return if (authorName == null) {
            data
        } else {
            //Фильтруем данные по AuthorName
            data.filter { budgetRecordWithAuthorName ->
                return@filter budgetRecordWithAuthorName.authorName?.contains(authorName, ignoreCase = true) == true
                }
            }
    }
}