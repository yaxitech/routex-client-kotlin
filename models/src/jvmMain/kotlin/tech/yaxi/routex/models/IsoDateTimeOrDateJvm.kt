@file:JvmName("IsoDateTimeOrDates")

package tech.yaxi.routex.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset

/** Build an [IsoDateTimeOrDate.Date] from a [java.time.LocalDate]. */
public fun of(date: java.time.LocalDate): IsoDateTimeOrDate.Date =
    IsoDateTimeOrDate.Date(LocalDate(date.year, date.monthValue, date.dayOfMonth))

/** Build an [IsoDateTimeOrDate.NaiveDateTime] from a [java.time.LocalDateTime]. */
public fun of(dateTime: java.time.LocalDateTime): IsoDateTimeOrDate.NaiveDateTime =
    IsoDateTimeOrDate.NaiveDateTime(dateTime.toKotlinLocalDateTime())

/** Build an [IsoDateTimeOrDate.OffsetDateTime] from a [java.time.OffsetDateTime]. */
public fun of(dateTime: java.time.OffsetDateTime): IsoDateTimeOrDate.OffsetDateTime =
    IsoDateTimeOrDate.OffsetDateTime(
        dateTime.toLocalDateTime().toKotlinLocalDateTime(),
        UtcOffset(seconds = dateTime.offset.totalSeconds),
    )

private fun java.time.LocalDateTime.toKotlinLocalDateTime(): LocalDateTime =
    LocalDateTime(year, monthValue, dayOfMonth, hour, minute, second, nano)
