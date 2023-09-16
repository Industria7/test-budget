package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = BudgetTable
                .select { BudgetTable.year eq param.year }

            val total = query.count()

            val data = BudgetEntity.wrapRows(query).map { it.toResponse() }.sortedWith(compareBy<BudgetRecord> { it.month }.thenByDescending { it.amount } )

            val sumByType = data.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            val rangeFrom = if(data.size > param.offset) param.offset else data.size-1
            val rangeTo = if(data.size > param.offset+param.limit-1) param.offset+param.limit-1 else data.size - 1
            val limitedList = data.slice(rangeFrom .. rangeTo)

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = limitedList
            )
        }
    }
}