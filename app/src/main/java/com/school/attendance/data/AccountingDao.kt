package com.school.attendance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    // ---- groups ----
    @Query("SELECT * FROM account_groups ORDER BY nature ASC, name COLLATE NOCASE ASC") fun observeGroups(): Flow<List<AccountGroup>>
    @Query("SELECT * FROM account_groups") suspend fun allGroups(): List<AccountGroup>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroup(g: AccountGroup): Long
    @Update suspend fun updateGroup(g: AccountGroup)
    @Delete suspend fun deleteGroup(g: AccountGroup)

    // ---- heads ----
    @Query("SELECT * FROM account_heads ORDER BY name COLLATE NOCASE ASC") fun observeHeads(): Flow<List<AccountHead>>
    @Query("SELECT * FROM account_heads") suspend fun allHeads(): List<AccountHead>
    @Query("SELECT * FROM account_heads WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun headByName(name: String): AccountHead?
    @Query("SELECT * FROM account_heads WHERE id = :id LIMIT 1") suspend fun headById(id: Long): AccountHead?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHead(h: AccountHead): Long
    @Update suspend fun updateHead(h: AccountHead)
    @Delete suspend fun deleteHead(h: AccountHead)

    // ---- cost centers ----
    @Query("SELECT * FROM cost_centers ORDER BY name COLLATE NOCASE") fun observeCostCenters(): Flow<List<CostCenter>>
    @Query("SELECT * FROM cost_centers") suspend fun allCostCenters(): List<CostCenter>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCostCenter(c: CostCenter): Long
    @Delete suspend fun deleteCostCenter(c: CostCenter)

    // ---- journal ----
    @Query("SELECT * FROM journal_entries ORDER BY dateMillis DESC") fun observeJournalEntries(): Flow<List<JournalEntry>>
    @Query("SELECT * FROM journal_entries") suspend fun allJournalEntries(): List<JournalEntry>
    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId") suspend fun linesFor(entryId: Long): List<JournalLine>
    @Query("SELECT * FROM journal_lines") suspend fun allJournalLines(): List<JournalLine>
    @Query("SELECT * FROM journal_lines") fun observeJournalLines(): Flow<List<JournalLine>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertJournalEntry(e: JournalEntry): Long
    @Insert suspend fun insertJournalLines(lines: List<JournalLine>)
    @Query("DELETE FROM journal_lines WHERE entryId = :entryId") suspend fun deleteJournalLines(entryId: Long)
    @Delete suspend fun deleteJournalEntry(e: JournalEntry)

    @Transaction
    suspend fun saveJournal(entry: JournalEntry, lines: List<JournalLine>): Long {
        val id = insertJournalEntry(entry.copy(id = 0))
        insertJournalLines(lines.map { it.copy(id = 0, entryId = id) })
        return id
    }

    @Transaction
    suspend fun updateJournal(entry: JournalEntry, lines: List<JournalLine>) {
        insertJournalEntry(entry)
        deleteJournalLines(entry.id)
        insertJournalLines(lines.map { it.copy(id = 0, entryId = entry.id) })
    }

    @Transaction
    suspend fun deleteJournal(entry: JournalEntry) {
        deleteJournalLines(entry.id)
        deleteJournalEntry(entry)
    }

    // ---- receipts ----
    @Query("SELECT * FROM acc_receipts ORDER BY dateMillis DESC") fun observeReceipts(): Flow<List<Receipt>>
    @Query("SELECT * FROM acc_receipts") suspend fun allReceipts(): List<Receipt>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReceipt(r: Receipt): Long
    @Delete suspend fun deleteReceipt(r: Receipt)

    // ---- expenses ----
    @Query("SELECT * FROM acc_expenses ORDER BY dateMillis DESC") fun observeExpenses(): Flow<List<Expense>>
    @Query("SELECT * FROM acc_expenses") suspend fun allExpenses(): List<Expense>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExpense(e: Expense): Long
    @Delete suspend fun deleteExpense(e: Expense)

    // ---- customers ----
    @Query("SELECT * FROM acc_customers ORDER BY name COLLATE NOCASE") fun observeCustomers(): Flow<List<Customer>>
    @Query("SELECT * FROM acc_customers") suspend fun allCustomers(): List<Customer>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomer(c: Customer): Long
    @Delete suspend fun deleteCustomer(c: Customer)

    // ---- suppliers ----
    @Query("SELECT * FROM acc_suppliers ORDER BY name COLLATE NOCASE") fun observeSuppliers(): Flow<List<Supplier>>
    @Query("SELECT * FROM acc_suppliers") suspend fun allSuppliers(): List<Supplier>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplier(s: Supplier): Long
    @Delete suspend fun deleteSupplier(s: Supplier)

    // ---- purchases ----
    @Query("SELECT * FROM acc_purchases ORDER BY dateMillis DESC") fun observePurchases(): Flow<List<Purchase>>
    @Query("SELECT * FROM acc_purchases") suspend fun allPurchases(): List<Purchase>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPurchase(p: Purchase): Long
    @Delete suspend fun deletePurchase(p: Purchase)

    // ---- wipe (Complete restore) ----
    @Query("DELETE FROM account_groups") suspend fun wipeGroups()
    @Query("DELETE FROM account_heads") suspend fun wipeHeads()
    @Query("DELETE FROM cost_centers") suspend fun wipeCostCenters()
    @Query("DELETE FROM journal_entries") suspend fun wipeJournalEntries()
    @Query("DELETE FROM journal_lines") suspend fun wipeJournalLines()
    @Query("DELETE FROM acc_receipts") suspend fun wipeReceipts()
    @Query("DELETE FROM acc_expenses") suspend fun wipeExpenses()
    @Query("DELETE FROM acc_customers") suspend fun wipeCustomers()
    @Query("DELETE FROM acc_suppliers") suspend fun wipeSuppliers()
    @Query("DELETE FROM acc_purchases") suspend fun wipePurchases()

    @Transaction
    suspend fun wipeAllAccounting() {
        wipeJournalLines(); wipeJournalEntries(); wipeReceipts(); wipeExpenses(); wipePurchases()
        wipeCustomers(); wipeSuppliers(); wipeCostCenters(); wipeHeads(); wipeGroups()
    }
}
