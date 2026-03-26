package devs.org.calculator.utils

import kotlin.math.abs

fun formatResult(result: Double): String {
    if (result.isInfinite() || result.isNaN()) return "Error"
    val absResult = abs(result)
    return when {
        absResult >= 1e15 || (absResult < 1e-6 && absResult > 0) ->
            String.format("%.3E", result)
                .replace("E+0", "E")
                .replace("E+", "E")
                .replace("E-0", "E-")
        result.toLong().toDouble() == result ->
            result.toLong().toString()
        else ->
            String.format("%.2f", result)
    }
}