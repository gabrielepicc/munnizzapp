package com.gabrielepiccione.onda.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/**
 * Piccola calcolatrice per la barra di ricerca: + - * / ^ %, parentesi,
 * virgola o punto come separatore decimale, "x" come moltiplicazione.
 */
object Calculator {

    /** Restituisce il risultato formattato, oppure null se il testo non è un'espressione. */
    fun evaluate(input: String): String? {
        val expr = input.replace(" ", "").replace(',', '.').replace('x', '*').replace('×', '*').replace('÷', '/').replace(':', '/')
        if (expr.isEmpty() || expr.none { it in "+-*/^%" }) return null
        if (expr.any { !(it.isDigit() || it in ".+-*/^%()") }) return null
        val value = runCatching { Parser(expr).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    private fun format(v: Double): String {
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        val plain = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
        return plain.replace('.', ',')
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expression()
            require(pos == s.length) { "token inatteso" }
            return v
        }

        private fun peek(): Char? = s.getOrNull(pos)

        private fun expression(): Double {
            var v = term()
            while (true) {
                v = when (peek()) {
                    '+' -> { pos++; v + term() }
                    '-' -> { pos++; v - term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                v = when (peek()) {
                    '*' -> { pos++; v * power() }
                    '/' -> { pos++; v / power() }
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            return if (peek() == '^') { pos++; base.pow(power()) } else base
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> postfix()
        }

        private fun postfix(): Double {
            var v = primary()
            while (peek() == '%') { pos++; v /= 100.0 }
            return v
        }

        private fun primary(): Double {
            if (peek() == '(') {
                pos++
                val v = expression()
                require(peek() == ')') { "manca )" }
                pos++
                return v
            }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            require(pos > start) { "numero atteso" }
            return s.substring(start, pos).toDouble()
        }
    }
}
