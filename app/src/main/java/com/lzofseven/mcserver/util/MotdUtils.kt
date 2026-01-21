package com.lzofseven.mcserver.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

object MotdUtils {
    private val colorMap = mapOf(
        '0' to Color(0xFF000000),
        '1' to Color(0xFF0000AA),
        '2' to Color(0xFF00AA00),
        '3' to Color(0xFF00AAAA),
        '4' to Color(0xFFAA0000),
        '5' to Color(0xFFAA00AA),
        '6' to Color(0xFFFFAA00),
        '7' to Color(0xFFAAAAAA),
        '8' to Color(0xFF555555),
        '9' to Color(0xFF5555FF),
        'a' to Color(0xFF55FF55),
        'b' to Color(0xFF55FFFF),
        'c' to Color(0xFFFF5555),
        'd' to Color(0xFFFF55FF),
        'e' to Color(0xFFFFFF55),
        'f' to Color(0xFFFFFFFF)
    )

    fun parseMinecraftColors(text: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            var currentColor = Color.White
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var isStrikethrough = false

            fun applyStyles() {
                // This is a simplified approach; buildAnnotatedString.withStyle handles nesting
            }

            while (i < text.length) {
                if ((text[i] == '&' || text[i] == '§') && i + 1 < text.length) {
                    val code = text[i + 1].lowercaseChar()
                    when {
                        colorMap.containsKey(code) -> {
                            currentColor = colorMap[code]!!
                            isBold = false
                            isItalic = false
                            isUnderline = false
                            isStrikethrough = false
                        }
                        code == 'l' -> isBold = true
                        code == 'o' -> isItalic = true
                        code == 'n' -> isUnderline = true
                        code == 'm' -> isStrikethrough = true
                        code == 'r' -> {
                            currentColor = Color.White
                            isBold = false
                            isItalic = false
                            isUnderline = false
                            isStrikethrough = false
                        }
                    }
                    i += 2
                } else {
                    withStyle(
                        SpanStyle(
                            color = currentColor,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = when {
                                isUnderline && isStrikethrough -> TextDecoration.combine(
                                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                                )
                                isUnderline -> TextDecoration.Underline
                                isStrikethrough -> TextDecoration.LineThrough
                                else -> TextDecoration.None
                            }
                        )
                    ) {
                        append(text[i])
                    }
                    i++
                }
            }
        }
    }
}
