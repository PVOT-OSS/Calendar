/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingLinksTest {

    @Test
    fun findsCommonServices() {
        val cases =
            mapOf(
                "https://meet.google.com/abc-defg-hij" to "Google Meet",
                "https://us02web.zoom.us/j/1234567890?pwd=x" to "Zoom",
                "https://teams.microsoft.com/l/meetup-join/19%3ameeting_x/0" to "Microsoft Teams",
                "https://meet.jit.si/MyRoom" to "Jitsi Meet",
                "https://acme.webex.com/meet/jane" to "Webex",
                "https://whereby.com/team-room" to "Whereby",
                "https://cloud.example.org/call/ab12cd34" to "Nextcloud Talk",
            )
        for ((url, service) in cases) {
            assertEquals(url, MeetingLinks.Link(url, service), MeetingLinks.find("Join: $url."))
        }
    }

    @Test
    fun checksFieldsInOrderAndIgnoresOtherLinks() {
        val link =
            MeetingLinks.find(
                "Room 4",
                "Agenda at https://example.com/doc, call https://meet.jit.si/Second",
                "https://meet.jit.si/Third",
            )
        assertEquals("https://meet.jit.si/Second", link?.url)
        assertNull(MeetingLinks.find(null, "", "see https://zoom.us/pricing"))
    }
}
