package com.brewska.database

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

internal fun PreparedStatement.bind(vararg values: Any?) {
    values.forEachIndexed { index, value -> setObject(index + 1, value) }
}
internal fun <T> Connection.query(sql: String, values: Array<out Any?> = emptyArray(), read: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(*values)
        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(read(rows)) } }
    }
