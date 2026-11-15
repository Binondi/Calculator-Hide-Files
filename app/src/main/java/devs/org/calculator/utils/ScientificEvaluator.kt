package devs.org.calculator.utils

import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function
import net.objecthunter.exp4j.operator.Operator
import java.util.regex.Pattern

object ScientificEvaluator {

    // DEG trigonometric functions
    private val dSin = object : Function("d_sin", 1) {
        override fun apply(vararg args: Double): Double {
            val deg = args[0]
            val mod = deg % 360.0
            val norm = if (mod < 0) mod + 360.0 else mod
            if (norm == 0.0 || norm == 180.0 || norm == 360.0) return 0.0
            if (norm == 90.0) return 1.0
            if (norm == 270.0) return -1.0
            return Math.sin(Math.toRadians(deg))
        }
    }

    private val dCos = object : Function("d_cos", 1) {
        override fun apply(vararg args: Double): Double {
            val deg = args[0]
            val mod = deg % 360.0
            val norm = if (mod < 0) mod + 360.0 else mod
            if (norm == 90.0 || norm == 270.0) return 0.0
            if (norm == 0.0 || norm == 360.0) return 1.0
            if (norm == 180.0) return -1.0
            return Math.cos(Math.toRadians(deg))
        }
    }

    private val dTan = object : Function("d_tan", 1) {
        override fun apply(vararg args: Double): Double {
            val deg = args[0]
            val mod = deg % 180.0
            val norm = if (mod < 0) mod + 180.0 else mod
            if (norm == 90.0) throw ArithmeticException("Undefined")
            if (norm == 0.0 || norm == 180.0) return 0.0
            if (norm == 45.0) return 1.0
            if (norm == 135.0) return -1.0
            return Math.tan(Math.toRadians(deg))
        }
    }

    private val dAsin = object : Function("d_asin", 1) {
        override fun apply(vararg args: Double): Double {
            val v = args[0]
            if (v < -1.0 || v > 1.0) throw ArithmeticException("Domain error")
            return Math.toDegrees(Math.asin(v))
        }
    }

    private val dAcos = object : Function("d_acos", 1) {
        override fun apply(vararg args: Double): Double {
            val v = args[0]
            if (v < -1.0 || v > 1.0) throw ArithmeticException("Domain error")
            return Math.toDegrees(Math.acos(v))
        }
    }

    private val dAtan = object : Function("d_atan", 1) {
        override fun apply(vararg args: Double): Double {
            return Math.toDegrees(Math.atan(args[0]))
        }
    }

    // Factorial operator: 5! = 120
    private val factorial = object : Operator("!", 1, true, Operator.PRECEDENCE_POWER + 1) {
        override fun apply(vararg args: Double): Double {
            val n = args[0]
            if (n < 0 || n != Math.floor(n)) {
                throw ArithmeticException("Factorial is only defined for non-negative integers")
            }
            if (n > 170) return Double.POSITIVE_INFINITY
            var res = 1.0
            for (i in 2..n.toLong()) {
                res *= i
            }
            return res
        }
    }

    fun evaluate(expression: String, isDeg: Boolean): Double {
        val processed = prepareExpressionForEvaluation(expression, isDeg, forPreview = false)
        if (processed.isEmpty()) throw IllegalArgumentException("Empty expression")

        val exp = ExpressionBuilder(processed)
            .operator(factorial)
            .functions(dSin, dCos, dTan, dAsin, dAcos, dAtan)
            .variables("pi", "e")
            .build()

        exp.setVariable("pi", Math.PI)
        exp.setVariable("e", Math.E)

        val result = exp.evaluate()
        if (result.isNaN() || result.isInfinite()) {
            throw ArithmeticException("Invalid mathematical operation")
        }
        return result
    }

    fun evaluatePreview(expression: String, isDeg: Boolean): Double? {
        return try {
            val processed = prepareExpressionForEvaluation(expression, isDeg, forPreview = true)
            if (processed.isEmpty()) return null

            val exp = ExpressionBuilder(processed)
                .operator(factorial)
                .functions(dSin, dCos, dTan, dAsin, dAcos, dAtan)
                .variables("pi", "e")
                .build()

            exp.setVariable("pi", Math.PI)
            exp.setVariable("e", Math.E)

            val result = exp.evaluate()
            if (result.isNaN() || result.isInfinite()) null else result
        } catch (_: Exception) {
            null
        }
    }

    fun prepareExpressionForEvaluation(expr: String, isDeg: Boolean, forPreview: Boolean = false): String {
        var s = expr.trim()
            .replace(",", "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("—", "-")
            .replace("−", "-")

        if (s.isEmpty()) return ""

        if (forPreview) {
            while (s.isNotEmpty() && (s.last() in charArrayOf('+', '-', '*', '/', '^', '('))) {
                s = s.dropLast(1).trim()
            }
        }

        if (s.isEmpty()) return ""

        // Replace constant pi and square root symbol
        s = s.replace("π", "pi")
        s = s.replace("√", "sqrt")

        // Single pass for trig functions to avoid replacing substrings
        val trigRegex = Regex("sin⁻¹|cos⁻¹|tan⁻¹|asin|acos|atan|sin|cos|tan")
        s = trigRegex.replace(s) { match ->
            when (match.value) {
                "sin⁻¹", "asin" -> if (isDeg) "d_asin" else "asin"
                "cos⁻¹", "acos" -> if (isDeg) "d_acos" else "acos"
                "tan⁻¹", "atan" -> if (isDeg) "d_atan" else "atan"
                "sin" -> if (isDeg) "d_sin" else "sin"
                "cos" -> if (isDeg) "d_cos" else "cos"
                "tan" -> if (isDeg) "d_tan" else "tan"
                else -> match.value
            }
        }

        // Single pass for log and ln
        val logRegex = Regex("\\b(log|ln)\\b")
        s = logRegex.replace(s) { match ->
            when (match.value) {
                "log" -> "log10"
                "ln" -> "log"
                else -> match.value
            }
        }

        // Handle factorial followed by operand
        s = s.replace(Regex("!\\s*(\\d|\\(|pi|e|[a-zA-Z])"), "!*$1")

        // Preprocess percentages
        if (s.contains("%")) {
            s = preprocessPercentage(s)
        }

        // Balance unclosed parentheses
        val openCount = s.count { it == '(' }
        val closeCount = s.count { it == ')' }
        if (openCount > closeCount) {
            s += ")".repeat(openCount - closeCount)
        }

        return s
    }

    private fun preprocessPercentage(expression: String): String {
        var sb = StringBuilder(expression)
        val percentPattern = Pattern.compile("(\\d*\\.?\\d+)%")
        var matcher = percentPattern.matcher(sb)
        var offset = 0

        while (matcher.find(offset)) {
            val percentNumberStr = matcher.group(1) ?: break
            val start = matcher.start()
            val end = matcher.end()

            val prevIdx = start - 1
            if (prevIdx >= 0 && (sb[prevIdx] == '+' || sb[prevIdx] == '-')) {
                val baseExpression = sb.substring(0, prevIdx)
                if (baseExpression.isNotEmpty()) {
                    try {
                        val baseValue = ExpressionBuilder(baseExpression).build().evaluate()
                        val percentNumber = percentNumberStr.toDouble()
                        val replacement = "(($baseValue) * $percentNumber / 100.0)"
                        sb.replace(start, end, replacement)
                        offset = start + replacement.length
                        matcher = percentPattern.matcher(sb)
                        continue
                    } catch (_: Exception) {}
                }
            }

            val percentNumber = percentNumberStr.toDouble()
            val replacement = "($percentNumber / 100.0)"
            sb.replace(start, end, replacement)
            offset = start + replacement.length
            matcher = percentPattern.matcher(sb)
        }

        return sb.toString()
    }
}
