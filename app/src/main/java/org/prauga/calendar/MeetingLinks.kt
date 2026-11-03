/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

object MeetingLinks {

    data class Link(val url: String, val service: String)

    private val URL = Regex("""https?://[^\s<>"'()\[\]]+""", RegexOption.IGNORE_CASE)

    private val SERVICES = listOf(
        Regex("""^meet\.google\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}""") to "Google Meet",
        Regex("""^([a-z0-9-]+\.)?zoom\.(us|com)/(j|my|w|s)/""") to "Zoom",
        Regex("""^([a-z0-9-]+\.)?zoomgov\.com/(j|my|w|s)/""") to "Zoom",
        Regex("""^teams\.(microsoft|live)\.com/(l/meetup-join|meet)/""") to "Microsoft Teams",
        Regex("""^meet\.jit\.si/[^/?#]+""") to "Jitsi Meet",
        Regex("""^([a-z0-9-]+\.)?webex\.com/(meet|join|[a-z0-9-]+/j\.php|[a-z0-9-]+/join)""") to "Webex",
        Regex("""^whereby\.com/[^/?#]+""") to "Whereby",
        Regex("""^(app\.)?(gotomeeting\.com|meet\.goto\.com)/""") to "GoTo Meeting",
        Regex("""^([a-z0-9-]+\.)?bluejeans\.com/[0-9]""") to "BlueJeans",
        Regex("""^chime\.aws/[0-9]""") to "Amazon Chime",
        Regex("""^join\.skype\.com/""") to "Skype",
        Regex("""^[a-z0-9.-]+/call/[a-z0-9]+$""") to "Nextcloud Talk",
        Regex("""^([a-z0-9-]+\.)?jitsi\.[a-z.]+/[^/?#]+""") to "Jitsi Meet",
        Regex("""^([a-z0-9-]+\.)?bigbluebutton\.[a-z.]+/""") to "BigBlueButton",
    )

    fun find(vararg texts: String?): Link? {
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            for (match in URL.findAll(text)) {
                val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', '>')
                val rest = url.substringAfter("://").lowercase()
                for ((pattern, service) in SERVICES) {
                    if (pattern.containsMatchIn(rest)) return Link(url, service)
                }
            }
        }
        return null
    }
}
