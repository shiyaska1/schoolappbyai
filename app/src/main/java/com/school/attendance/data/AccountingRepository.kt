package com.school.attendance.data

import android.content.Context

class AccountingRepository(context: Context) {
    private val dao = AppDatabase.get(context).accountingDao()

    val groups get() = dao.observeGroups()
    val heads get() = dao.observeHeads()
    val costCenters get() = dao.observeCostCenters()
    val journalEntries get() = dao.observeJournalEntries()
    val journalLines get() = dao.observeJournalLines()
    val receipts get() = dao.observeReceipts()
    val expenses get() = dao.observeExpenses()
    val customers get() = dao.observeCustomers()
    val suppliers get() = dao.observeSuppliers()
    val purchases get() = dao.observePurchases()

    suspend fun allGroups() = dao.allGroups()
    suspend fun allHeads() = dao.allHeads()
    suspend fun allCostCenters() = dao.allCostCenters()
    suspend fun allJournalEntries() = dao.allJournalEntries()
    suspend fun allJournalLines() = dao.allJournalLines()
    suspend fun allReceipts() = dao.allReceipts()
    suspend fun allExpenses() = dao.allExpenses()
    suspend fun allCustomers() = dao.allCustomers()
    suspend fun allSuppliers() = dao.allSuppliers()
    suspend fun allPurchases() = dao.allPurchases()
    suspend fun linesFor(entryId: Long) = dao.linesFor(entryId)

    suspend fun saveGroup(g: AccountGroup) = dao.insertGroup(g.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteGroup(g: AccountGroup) = dao.deleteGroup(g)
    suspend fun saveHead(h: AccountHead) = dao.insertHead(h.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteHead(h: AccountHead) = dao.deleteHead(h)
    suspend fun saveCostCenter(c: CostCenter) = dao.insertCostCenter(c.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteCostCenter(c: CostCenter) = dao.deleteCostCenter(c)

    suspend fun saveJournal(entry: JournalEntry, lines: List<JournalLine>) =
        dao.saveJournal(entry.copy(updatedAtMillis = System.currentTimeMillis()), lines)
    suspend fun updateJournal(entry: JournalEntry, lines: List<JournalLine>) =
        dao.updateJournal(entry.copy(updatedAtMillis = System.currentTimeMillis()), lines)
    suspend fun deleteJournal(entry: JournalEntry) = dao.deleteJournal(entry)

    suspend fun saveReceipt(r: Receipt) = dao.insertReceipt(r.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteReceipt(r: Receipt) = dao.deleteReceipt(r)
    suspend fun saveExpense(e: Expense) = dao.insertExpense(e.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteExpense(e: Expense) = dao.deleteExpense(e)
    suspend fun saveCustomer(c: Customer) = dao.insertCustomer(c.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteCustomer(c: Customer) = dao.deleteCustomer(c)
    suspend fun saveSupplier(s: Supplier) = dao.insertSupplier(s.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteSupplier(s: Supplier) = dao.deleteSupplier(s)
    suspend fun savePurchase(p: Purchase) = dao.insertPurchase(p.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deletePurchase(p: Purchase) = dao.deletePurchase(p)

    suspend fun nextVoucherNo(prefix: String, count: Int): String = "$prefix${(count + 1).toString().padStart(4, '0')}"

    suspend fun wipeAll() = dao.wipeAllAccounting()

    /** Seeds a minimal, sensible chart of accounts on first use (Cash, Bank, Sundry Debtors,
     * Sundry Creditors, Fee Income, Indirect Expenses, Purchase Account) — same idea as the POS
     * app's system groups/heads, just created lazily instead of at DB-creation time. */
    suspend fun ensureDefaultChartOfAccounts() {
        if (dao.allGroups().isNotEmpty()) return
        val cashInHand = dao.insertGroup(AccountGroup(name = "Cash-in-hand", nature = AccountNature.ASSET, isSystem = true))
        val bank = dao.insertGroup(AccountGroup(name = "Bank Accounts", nature = AccountNature.ASSET, isSystem = true))
        dao.insertGroup(AccountGroup(name = "Sundry Debtors", nature = AccountNature.ASSET, isSystem = true))
        dao.insertGroup(AccountGroup(name = "Sundry Creditors", nature = AccountNature.LIABILITY, isSystem = true))
        val income = dao.insertGroup(AccountGroup(name = "Fee Income", nature = AccountNature.INCOME, isSystem = true))
        val indirectExp = dao.insertGroup(AccountGroup(name = "Indirect Expenses", nature = AccountNature.EXPENSE, isSystem = true))
        dao.insertGroup(AccountGroup(name = "Purchase Account", nature = AccountNature.EXPENSE, isSystem = true))
        dao.insertHead(AccountHead(name = "Cash", groupId = cashInHand, isSystem = true))
        dao.insertHead(AccountHead(name = "Bank", groupId = bank, isSystem = true))
        dao.insertHead(AccountHead(name = "Fee Income", groupId = income, isSystem = true))
        dao.insertHead(AccountHead(name = "Indirect Expenses", groupId = indirectExp, isSystem = true))
    }
}
