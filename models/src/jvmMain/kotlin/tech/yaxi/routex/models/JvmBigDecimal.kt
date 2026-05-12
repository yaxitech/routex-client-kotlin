@file:JvmName("JvmBigDecimals")

package tech.yaxi.routex.models

import com.ionspin.kotlin.bignum.decimal.BigDecimal

/**
 * Convert this multiplatform [BigDecimal] to a [java.math.BigDecimal], preserving
 * the original number of fractional digits.
 *
 * Java callers receive this as `JvmBigDecimals.toJavaBigDecimal(amount)`.
 */
public fun BigDecimal.toJavaBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(toPlainString())

/**
 * Convert a [java.math.BigDecimal] to the multiplatform [BigDecimal] used in [Amount],
 * [Balance], and other monetary fields.
 *
 * Java callers receive this as `JvmBigDecimals.toBigNumBigDecimal(value)`.
 */
public fun java.math.BigDecimal.toBigNumBigDecimal(): BigDecimal = BigDecimal.parseString(toPlainString())
