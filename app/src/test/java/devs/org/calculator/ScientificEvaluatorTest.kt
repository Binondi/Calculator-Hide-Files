package devs.org.calculator

import devs.org.calculator.utils.ScientificEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScientificEvaluatorTest {

    @Test
    fun testBasicArithmetic() {
        assertEquals(7.0, ScientificEvaluator.evaluate("3 + 4", isDeg = true), 1e-6)
        assertEquals(6.0, ScientificEvaluator.evaluate("2 × 3", isDeg = true), 1e-6)
        assertEquals(2.5, ScientificEvaluator.evaluate("5 ÷ 2", isDeg = true), 1e-6)
    }

    @Test
    fun testTrigonometryDeg() {
        assertEquals(0.5, ScientificEvaluator.evaluate("sin(30)", isDeg = true), 1e-6)
        assertEquals(0.5, ScientificEvaluator.evaluate("cos(60)", isDeg = true), 1e-6)
        assertEquals(0.0, ScientificEvaluator.evaluate("cos(90)", isDeg = true), 1e-6)
        assertEquals(1.0, ScientificEvaluator.evaluate("tan(45)", isDeg = true), 1e-6)
        assertEquals(30.0, ScientificEvaluator.evaluate("sin⁻¹(0.5)", isDeg = true), 1e-6)
        assertEquals(45.0, ScientificEvaluator.evaluate("tan⁻¹(1)", isDeg = true), 1e-6)
    }

    @Test
    fun testTrigonometryRad() {
        assertEquals(1.0, ScientificEvaluator.evaluate("sin(pi / 2)", isDeg = false), 1e-6)
        assertEquals(0.0, ScientificEvaluator.evaluate("cos(pi / 2)", isDeg = false), 1e-6)
    }

    @Test
    fun testScientificFunctions() {
        assertEquals(120.0, ScientificEvaluator.evaluate("5!", isDeg = true), 1e-6)
        assertEquals(1.0, ScientificEvaluator.evaluate("0!", isDeg = true), 1e-6)
        assertEquals(4.0, ScientificEvaluator.evaluate("√(16)", isDeg = true), 1e-6)
        assertEquals(8.0, ScientificEvaluator.evaluate("2^3", isDeg = true), 1e-6)
        assertEquals(2.0, ScientificEvaluator.evaluate("log(100)", isDeg = true), 1e-6)
        assertEquals(1.0, ScientificEvaluator.evaluate("ln(e)", isDeg = true), 1e-6)
        assertEquals(0.25, ScientificEvaluator.evaluate("1/(4)", isDeg = true), 1e-6)
    }

    @Test
    fun testImplicitMultiplication() {
        assertEquals(14.0, ScientificEvaluator.evaluate("2(3+4)", isDeg = true), 1e-6)
        assertEquals(1.0, ScientificEvaluator.evaluate("2sin(30)", isDeg = true), 1e-6)
        assertEquals(6.2831853, ScientificEvaluator.evaluate("2π", isDeg = true), 1e-5)
    }

    @Test
    fun testAutoParenthesesAndPreview() {
        // Unclosed parenthesis in preview: sin(30
        val preview = ScientificEvaluator.evaluatePreview("sin(30", isDeg = true)
        assertNotNull(preview)
        assertEquals(0.5, preview!!, 1e-6)

        // Trailing operator: 5 +
        val previewOp = ScientificEvaluator.evaluatePreview("5 + ", isDeg = true)
        assertNotNull(previewOp)
        assertEquals(5.0, previewOp!!, 1e-6)
    }
}

