package com.school.attendance.report

import com.school.attendance.data.AccountGroup
import com.school.attendance.data.AccountHead
import com.school.attendance.data.AccountNature
import com.school.attendance.data.CostCenter
import com.school.attendance.data.Expense
import com.school.attendance.data.JournalEntry
import com.school.attendance.data.JournalLine
import com.school.attendance.data.JournalVoucherType
import com.school.attendance.data.Purchase
import com.school.attendance.data.Receipt

/** One balanced posting to an account — the unified "account transaction" every report reads.
 * Ported from the POS billing app's AccountingEngine: no running-balance table anywhere — every
 * balance/ledger/trial-balance/P&L/balance-sheet/day-book is recomputed from this list each time
 * a report is opened, so there's no ledger/document drift to worry about. */
data class Posting(
    val head: String,
    val group: String,
    val nature: AccountNature,
    val debit: Double,
    val credit: Double,
    val date: Long,
    val particulars: String = "",
    val vch: String = "",
    val costCenter: String = ""
)

object AccountingEngine {
    private const val OPENING_DATE = Long.MIN_VALUE

    fun build(
        heads: List<AccountHead>,
        groups: List<AccountGroup>,
        receipts: List<Receipt>,
        expenses: List<Expense>,
        purchases: List<Purchase>,
        jEntries: List<JournalEntry>,
        jLines: List<JournalLine>,
        costCenters: List<CostCenter> = emptyList()
    ): List<Posting> {
        val groupById = groups.associateBy { it.id }
        val headById = heads.associateBy { it.id }
        val costCenterById = costCenters.associateBy { it.id }
        val out = ArrayList<Posting>()

        fun natureOfGroup(name: String): AccountNature = groups.firstOrNull { it.name == name }?.nature ?: AccountNature.ASSET

        fun partyGroup(name: String, defaultGroup: String): Pair<String, AccountNature> {
            val h = heads.firstOrNull { it.name.equals(name, true) }
            val g = h?.let { groupById[it.groupId] }
            val defNat = if (defaultGroup == "Sundry Debtors") AccountNature.ASSET else AccountNature.LIABILITY
            return (g?.name ?: defaultGroup) to (g?.nature ?: defNat)
        }

        fun cashBank(accountId: Long, mode: String): Triple<String, String, AccountNature> {
            headById[accountId]?.let { h ->
                val g = groupById[h.groupId]
                return Triple(h.name, g?.name ?: "Cash-in-hand", g?.nature ?: AccountNature.ASSET)
            }
            val (nm, grp) = when (mode) {
                "UPI" -> "UPI" to "Cash-in-hand"
                "Card" -> "Card" to "Cash-in-hand"
                "Cheque" -> "Cheque" to "Cash-in-hand"
                "Bank" -> "Bank" to "Bank Accounts"
                else -> "Cash" to "Cash-in-hand"
            }
            return Triple(nm, grp, AccountNature.ASSET)
        }

        heads.forEach { h ->
            if (h.openingBalance != 0.0) {
                val g = groupById[h.groupId]
                out.add(Posting(h.name, g?.name ?: "", g?.nature ?: AccountNature.ASSET,
                    if (h.openingIsDebit) h.openingBalance else 0.0,
                    if (h.openingIsDebit) 0.0 else h.openingBalance, OPENING_DATE, "Opening Balance", ""))
            }
        }

        // receipts (money in) — fee payments and any other money received
        receipts.forEach { rc ->
            val (nm, grp, nat) = cashBank(rc.toAccountId, rc.paymentMode)
            val party = rc.studentName.ifBlank { rc.payFrom }.ifBlank { "Sundry Debtors" }
            val (pg, pn) = partyGroup(party, "Sundry Debtors")
            out.add(Posting(nm, grp, nat, rc.amount, 0.0, rc.dateMillis, "Receipt - $party", rc.receiptNo))
            out.add(Posting(party, pg, pn, 0.0, rc.amount, rc.dateMillis, "Receipt", rc.receiptNo))
        }

        // expenses (money out)
        expenses.forEach { e ->
            val (nm, grp, nat) = cashBank(e.fromAccountId, e.paymentMode)
            if (e.payTo.isNotBlank()) {
                val (pg, pn) = partyGroup(e.payTo, "Sundry Creditors")
                out.add(Posting(e.payTo, pg, pn, e.amount, 0.0, e.dateMillis, "Payment", e.voucherNo))
            } else {
                out.add(Posting(e.description.ifBlank { "Indirect Expenses" }, "Indirect Expenses", natureOfGroup("Indirect Expenses"), e.amount, 0.0, e.dateMillis, "Payment", e.voucherNo))
            }
            out.add(Posting(nm, grp, nat, 0.0, e.amount, e.dateMillis, "Payment - ${e.payTo.ifBlank { e.description }}", e.voucherNo))
        }

        // purchases (from suppliers) — debit a "Purchases" expense head, credit supplier or cash/bank
        purchases.forEach { p ->
            out.add(Posting(p.description.ifBlank { "Purchases" }, "Purchase Account", natureOfGroup("Purchase Account"), p.amount, 0.0, p.dateMillis, "Purchase - ${p.supplierName}", p.purchaseNo))
            if (p.paymentMethod == "Credit") {
                out.add(Posting(p.supplierName, "Sundry Creditors", AccountNature.LIABILITY, 0.0, p.amount, p.dateMillis, "Purchase", p.purchaseNo))
            } else {
                val (nm, grp, nat) = cashBank(p.fromAccountId, p.paymentMethod)
                out.add(Posting(nm, grp, nat, 0.0, p.amount, p.dateMillis, "Purchase - ${p.supplierName}", p.purchaseNo))
            }
        }

        // manual journal lines
        val entryById = jEntries.associateBy { it.id }
        jLines.forEach { l ->
            val e = entryById[l.entryId] ?: return@forEach
            val h = headById[l.headId]
            val g = h?.let { groupById[it.groupId] }
            val ccName = l.costCenterId?.let { costCenterById[it]?.name } ?: ""
            val defaultParticulars = if (e.voucherType == JournalVoucherType.CONTRA) "Contra" else "Journal"
            out.add(Posting(l.headName, g?.name ?: "", g?.nature ?: AccountNature.ASSET,
                if (l.isDebit) l.amount else 0.0, if (!l.isDebit) l.amount else 0.0, e.dateMillis,
                e.narration.ifBlank { defaultParticulars }, e.voucherNo, ccName))
        }

        return out
    }

    fun rollUp(groups: List<AccountGroup>, postings: List<Posting>): Map<Long, Double> {
        val byGroupName = postings.groupBy { it.group }
        val childrenOf = groups.groupBy { it.parentGroupId }
        val result = HashMap<Long, Double>()
        fun computeFor(g: AccountGroup): Double {
            result[g.id]?.let { return it }
            val own = (byGroupName[g.name] ?: emptyList()).sumOf { it.debit - it.credit }
            val childTotal = (childrenOf[g.id] ?: emptyList()).sumOf { computeFor(it) }
            val total = own + childTotal
            result[g.id] = total
            return total
        }
        groups.forEach { computeFor(it) }
        return result
    }

    data class HeadBalance(val head: String, val group: String, val nature: AccountNature, val debit: Double, val credit: Double)

    /** Net balance per head, up to and including [to]. */
    fun trialBalanceOf(postings: List<Posting>, to: Long): List<HeadBalance> =
        postings.filter { it.date <= to }
            .groupBy { it.head to it.group }
            .map { (k, list) ->
                val net = list.sumOf { it.debit - it.credit }
                val nature = list.first().nature
                HeadBalance(k.first, k.second, nature, if (net >= 0) net else 0.0, if (net < 0) -net else 0.0)
            }
            .filter { it.debit != 0.0 || it.credit != 0.0 }
            .sortedBy { it.head.lowercase() }

    data class PnlLine(val head: String, val amount: Double)
    data class ProfitLoss(val income: List<PnlLine>, val expense: List<PnlLine>, val totalIncome: Double, val totalExpense: Double) {
        val netProfit: Double get() = totalIncome - totalExpense
    }

    fun profitLossOf(postings: List<Posting>, from: Long, to: Long): ProfitLoss {
        val inRange = postings.filter { it.date in from..to }
        val income = inRange.filter { it.nature == AccountNature.INCOME }.groupBy { it.head }
            .map { (h, l) -> PnlLine(h, l.sumOf { it.credit - it.debit }) }.filter { it.amount != 0.0 }
        val expense = inRange.filter { it.nature == AccountNature.EXPENSE }.groupBy { it.head }
            .map { (h, l) -> PnlLine(h, l.sumOf { it.debit - it.credit }) }.filter { it.amount != 0.0 }
        return ProfitLoss(income, expense, income.sumOf { it.amount }, expense.sumOf { it.amount })
    }

    data class BalanceSheet(val assets: List<PnlLine>, val liabilities: List<PnlLine>, val totalAssets: Double, val totalLiabilities: Double, val netProfit: Double)

    fun balanceSheetOf(postings: List<Posting>, to: Long): BalanceSheet {
        val upTo = postings.filter { it.date <= to }
        val assets = upTo.filter { it.nature == AccountNature.ASSET }.groupBy { it.head }
            .map { (h, l) -> PnlLine(h, l.sumOf { it.debit - it.credit }) }.filter { it.amount != 0.0 }
        val liabilities = upTo.filter { it.nature == AccountNature.LIABILITY }.groupBy { it.head }
            .map { (h, l) -> PnlLine(h, l.sumOf { it.credit - it.debit }) }.filter { it.amount != 0.0 }
        val pnl = profitLossOf(postings, Long.MIN_VALUE, to)
        return BalanceSheet(assets, liabilities, assets.sumOf { it.amount }, liabilities.sumOf { it.amount } + pnl.netProfit, pnl.netProfit)
    }

    fun dayBookOf(postings: List<Posting>, from: Long, to: Long): List<Posting> =
        postings.filter { it.date in from..to }.sortedWith(compareBy({ it.date }, { it.vch }))

    /** Every distinct account name postings have been made to — includes real [AccountHead]s and
     * ad-hoc party names (a student, a supplier) that only exist as posting heads, so a ledger
     * lookup works for either without needing the party pre-registered as a head. */
    fun accountNames(postings: List<Posting>, groupName: String? = null): List<String> =
        postings.filter { (groupName == null || it.group == groupName) && it.head.isNotBlank() }
            .map { it.head }.distinct().sortedBy { it.lowercase() }

    data class LedgerRow(val date: Long, val particulars: String, val vch: String, val debit: Double, val credit: Double, val balance: Double)
    data class LedgerResult(val opening: Double, val rows: List<LedgerRow>, val closing: Double)

    /** One account's running-balance statement: everything before [from] collapses into an opening
     * balance, then a signed running total walks the rows in [from]..[to] (oldest first). */
    fun ledgerOf(postings: List<Posting>, head: String, from: Long, to: Long): LedgerResult {
        val mine = postings.filter { it.head.equals(head, ignoreCase = true) }
        val opening = mine.filter { it.date < from }.sumOf { it.debit - it.credit }
        var running = opening
        val rows = mine.filter { it.date in from..to }.sortedBy { it.date }.map { p ->
            running += p.debit - p.credit
            LedgerRow(p.date, p.particulars, p.vch, p.debit, p.credit, running)
        }
        return LedgerResult(opening, rows, running)
    }

    /** Every party in [groupName] (typically "Sundry Creditors" or "Sundry Debtors") with a
     * non-zero balance as of [to] — the outstanding-payables/receivables list. */
    fun outstandingOf(postings: List<Posting>, groupName: String, to: Long): List<HeadBalance> =
        trialBalanceOf(postings, to).filter { it.group == groupName }.sortedByDescending { it.debit + it.credit }
}
