package com.school.attendance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ported from the POS billing app's accounting module — same shapes, same posting model, so the
 * same mental model (and eventually the same AccountingEngine-style reports) applies here. */
enum class AccountNature(val label: String) {
    ASSET("Asset"), LIABILITY("Liability"), INCOME("Income"), EXPENSE("Expense")
}

@Entity(tableName = "account_groups")
data class AccountGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nature: AccountNature,
    val isSystem: Boolean = false,
    /** Optional parent group, so groups can nest (e.g. "Bank Accounts" -> "HDFC"). Null = top-level. */
    val parentGroupId: Long? = null,
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "account_heads")
data class AccountHead(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val groupId: Long,
    val openingBalance: Double = 0.0,
    val openingIsDebit: Boolean = true,
    val isSystem: Boolean = false,
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "cost_centers")
data class CostCenter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSystem: Boolean = false,
    val updatedAtMillis: Long = 0
)

object JournalVoucherType {
    const val JOURNAL = "JOURNAL"
    const val CONTRA = "CONTRA"
}

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voucherNo: String,
    val dateMillis: Long,
    val narration: String = "",
    val cashMode: String = "",
    val cashIsIn: Boolean = true,
    val cashAmount: Double = 0.0,
    val deviceId: String = "",
    val updatedAtMillis: Long = 0,
    val voucherType: String = JournalVoucherType.JOURNAL
)

@Entity(tableName = "journal_lines")
data class JournalLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val headId: Long,
    val headName: String,
    val amount: Double,
    val isDebit: Boolean,
    val costCenterId: Long? = null
)

/** A fee (or any other) receipt — money coming in. [studentId] is set for a fee payment; blank/0
 * with [payFrom] set covers any other receipt (donation, rent, etc.), matching how the POS app's
 * Receipt covers both a customer's bill payment and a free-standing receipt. */
@Entity(tableName = "acc_receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val dateMillis: Long,
    val studentId: Long = 0,
    val studentName: String = "",
    val payFrom: String = "",
    val amount: Double,
    /** Cash / UPI / Card / Cheque / Bank. */
    val paymentMode: String = "Cash",
    /** Which Cash/Bank account head the money landed in (0 = inferred from [paymentMode]). */
    val toAccountId: Long = 0,
    val narration: String = "",
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "acc_expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voucherNo: String,
    val dateMillis: Long,
    val description: String,
    val amount: Double,
    val paymentMode: String = "Cash",
    val payTo: String = "",
    /** Which Cash/Bank account head the money came from (0 = inferred from [paymentMode]). */
    val fromAccountId: Long = 0,
    val purchaseId: Long = 0,
    val purchaseNo: String = "",
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)

/** A generic external party the school owes or is owed money by, outside the student fee ledger
 * (a donor, a hall-rental tenant, etc.) — mirrors the POS app's Customer. */
@Entity(tableName = "acc_customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "acc_suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val updatedAtMillis: Long = 0
)

/** A purchase voucher against a supplier — stationery, furniture, bus fuel, etc. No items/stock
 * (the school app has no inventory yet); [description] is the debit side, like a categorized expense. */
@Entity(tableName = "acc_purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseNo: String,
    val dateMillis: Long,
    val supplierId: Long,
    val supplierName: String,
    val description: String,
    val amount: Double,
    /** Credit / Cash / UPI / Card / Cheque / Bank. */
    val paymentMethod: String = "Credit",
    val fromAccountId: Long = 0,
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)
