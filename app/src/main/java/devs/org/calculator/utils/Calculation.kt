package devs.org.calculator.utils

import kotlin.math.abs

fun formatResult(value: Double, precision: Int = 10): String {
    if (value.isInfinite() || value.isNaN()) return "Error"

    val absResult = abs(value)

    return when {
        absResult >= 1e15 || (absResult < 1e-6 && absResult > 0) ->
            String.format("%.3E", value)
                .replace("E+0", "E")
                .replace("E+", "E")
                .replace("E-0", "E-")

        value.toLong().toDouble() == value ->
            value.toLong().toString()

        else ->
            "%.${precision}f".format(value).trimEnd('0').trimEnd('.')
    }
}

fun formatWithCommas(expression: String): String {
    val regex = Regex("(\\d+\\.?\\d*)")
    return expression.replace(regex) { match ->
        val numStr = match.value
        val parts = numStr.split(".")
        val intPart = parts[0].reversed().chunked(3).joinToString(",").reversed()
        if (parts.size > 1) "$intPart.${parts[1]}" else intPart
    }
}