/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

data class IcsProperty(val name: String, val params: Map<String, String>, val value: String) {
    fun param(name: String): String? = params[name.uppercase()]

    val text: String
        get() = IcsParser.unescapeText(value)
}

class IcsComponent(val name: String) {
    val properties = mutableListOf<IcsProperty>()
    val children = mutableListOf<IcsComponent>()

    fun first(name: String): IcsProperty? = properties.firstOrNull { it.name == name }

    fun all(name: String): List<IcsProperty> = properties.filter { it.name == name }

    fun text(name: String): String? = first(name)?.text

    fun components(name: String): List<IcsComponent> = children.filter { it.name == name }
}

object IcsParser {

    fun parse(input: String): List<IcsComponent> {
        val roots = mutableListOf<IcsComponent>()
        val stack = ArrayDeque<IcsComponent>()
        for (line in unfold(input)) {
            val property = parseLine(line) ?: continue
            when (property.name) {
                "BEGIN" -> stack.addLast(IcsComponent(property.value.trim().uppercase()))
                "END" -> {
                    val name = property.value.trim().uppercase()
                    while (stack.isNotEmpty()) {
                        val done = stack.removeLast()
                        val parent = stack.lastOrNull()
                        if (parent != null) parent.children += done else roots += done
                        if (done.name == name) break
                    }
                }
                else -> stack.lastOrNull()?.properties?.add(property)
            }
        }
        while (stack.isNotEmpty()) {
            val done = stack.removeLast()
            stack.lastOrNull()?.children?.add(done) ?: roots.add(done)
        }
        return roots
    }

    fun unfold(input: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var started = false
        for (raw in input.removePrefix("\uFEFF").split("\r\n", "\n", "\r")) {
            if (raw.isNotEmpty() && (raw[0] == ' ' || raw[0] == '\t') && started) {
                current.append(raw, 1, raw.length)
            } else {
                if (started && current.isNotBlank()) lines += current.toString()
                current.setLength(0)
                current.append(raw)
                started = true
            }
        }
        if (started && current.isNotBlank()) lines += current.toString()
        return lines
    }

    fun parseLine(line: String): IcsProperty? {
        var i = 0
        var inQuotes = false
        var nameEnd = -1
        var colon = -1
        while (i < line.length) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (!inQuotes && c == ';' && nameEnd < 0) nameEnd = i
            else if (!inQuotes && c == ':') {
                colon = i
                break
            }
            i++
        }
        if (colon < 0) return null
        if (nameEnd < 0) nameEnd = colon
        val name = line.substring(0, nameEnd).trim().uppercase()
        if (name.isEmpty()) return null
        val params = LinkedHashMap<String, String>()
        if (nameEnd < colon) {
            for (part in splitOutsideQuotes(line.substring(nameEnd + 1, colon), ';')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                params[part.substring(0, eq).trim().uppercase()] =
                    part.substring(eq + 1).trim().removeSurrounding("\"")
            }
        }
        return IcsProperty(name, params, line.substring(colon + 1))
    }

    fun splitOutsideQuotes(input: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in input) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                c == separator && !inQuotes -> {
                    parts += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts
    }

    fun unescapeText(value: String): String {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n',
                    'N' -> out.append('\n')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    fun escapeText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
}
