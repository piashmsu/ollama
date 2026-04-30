package com.piashmsu.aichat.agent.tools

import com.piashmsu.aichat.agent.AgentTool

class CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description = "Evaluate a basic arithmetic expression."
    override val parametersJsonSchema = """{"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"]}"""

    override suspend fun execute(arguments: Map<String, String>): String {
        val expr = arguments["expression"] ?: return "error: missing 'expression'"
        return try {
            val v = ShuntingYard.eval(expr)
            v.toString()
        } catch (t: Throwable) {
            "error: ${t.message}"
        }
    }
}

// Minimal shunting-yard arithmetic evaluator. + - * / ( ) and unary -.
private object ShuntingYard {
    fun eval(input: String): Double {
        val tokens = tokenize(input)
        val output = ArrayDeque<String>()
        val ops = ArrayDeque<String>()
        val prec = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "u-" to 3)
        for (t in tokens) {
            when {
                t.toDoubleOrNull() != null -> output.addLast(t)
                t == "(" -> ops.addLast(t)
                t == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != "(") output.addLast(ops.removeLast())
                    require(ops.isNotEmpty() && ops.removeLast() == "(") { "mismatched parens" }
                }
                else -> {
                    while (ops.isNotEmpty() && ops.last() != "(" &&
                        (prec[ops.last()] ?: 0) >= (prec[t] ?: 0)) {
                        output.addLast(ops.removeLast())
                    }
                    ops.addLast(t)
                }
            }
        }
        while (ops.isNotEmpty()) {
            val o = ops.removeLast()
            require(o != "(") { "mismatched parens" }
            output.addLast(o)
        }
        val stack = ArrayDeque<Double>()
        for (t in output) {
            val n = t.toDoubleOrNull()
            if (n != null) { stack.addLast(n); continue }
            when (t) {
                "u-" -> stack.addLast(-stack.removeLast())
                else -> {
                    val b = stack.removeLast(); val a = stack.removeLast()
                    stack.addLast(when (t) {
                        "+" -> a + b
                        "-" -> a - b
                        "*" -> a * b
                        "/" -> a / b
                        else -> error("op $t")
                    })
                }
            }
        }
        return stack.last()
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        var lastWasOp = true
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) { sb.append(s[i]); i++ }
                    out += sb.toString(); lastWasOp = false
                }
                c == '(' -> { out += "("; i++; lastWasOp = true }
                c == ')' -> { out += ")"; i++; lastWasOp = false }
                c in "+-*/" -> {
                    out += if (c == '-' && lastWasOp) "u-" else c.toString()
                    i++; lastWasOp = true
                }
                else -> error("unexpected '$c'")
            }
        }
        return out
    }
}
