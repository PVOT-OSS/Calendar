/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.prauga.calendar.event.EditEventActivity
import org.prauga.calendar.event.ExtendedProperty
import org.prauga.calendar.ics.IcsCalendar
import org.prauga.calendar.ics.IcsEvent
import org.prauga.calendar.ics.IcsImporter
import org.prauga.calendar.ics.ImportAction
import org.prauga.calendar.ics.ImportTarget
import org.prauga.calendar.theme.applyTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.calendar.R
import java.io.File
import java.util.Formatter
import java.util.Locale

class ImportActivity : AppCompatActivity() {

    private lateinit var importer: IcsImporter
    private lateinit var adapter: EventAdapter
    private lateinit var importButton: TextView
    private lateinit var summary: TextView
    private var calendar: IcsCalendar? = null
    private var targets: List<ImportTarget> = emptyList()
    private var target: ImportTarget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null || (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE)) {
            Toast.makeText(this, R.string.cal_nothing_to_import, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContentView(R.layout.import_activity)
        setSupportActionBar(findViewById(R.id.include))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.import_title)

        importer = IcsImporter(contentResolver)
        summary = findViewById(R.id.summary)
        importButton = findViewById(R.id.import_button)
        adapter = EventAdapter { updateButton() }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@ImportActivity)
            adapter = this@ImportActivity.adapter
        }
        findViewById<View>(R.id.cancel).setOnClickListener { finish() }
        importButton.setOnClickListener { runImport() }
        importButton.isEnabled = false

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val text = readText(uri) ?: return@withContext null
                IcsCalendar.parse(text, Utils.getTimeZone(this@ImportActivity, null)) to importer.writableCalendars()
            }
            if (loaded == null || loaded.first.events.isEmpty()) {
                Toast.makeText(this@ImportActivity, R.string.cal_nothing_to_import, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            show(loaded.first, loaded.second)
        }
    }

    private fun readText(uri: Uri): String? = try {
        val stream = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            File(uri.path ?: return null).inputStream()
        } else {
            contentResolver.openInputStream(uri)
        }
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    } catch (e: Exception) {
        null
    }

    private fun show(parsed: IcsCalendar, writable: List<ImportTarget>) {
        calendar = parsed
        targets = writable
        if (parsed.warnings.isNotEmpty()) {
            findViewById<TextView>(R.id.warning).apply {
                visibility = View.VISIBLE
                text = getString(R.string.import_unknown_zones, parsed.warnings.joinToString(", "))
            }
        }
        adapter.submit(parsed.events)
        summary.text = resources.getQuantityString(R.plurals.import_summary, adapter.rowCount(), adapter.rowCount())
        invalidateOptionsMenu()
        if (writable.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.import_no_calendar)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }
        val spinner = findViewById<android.widget.Spinner>(R.id.calendar_spinner)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            writable.map { if (it.displayName == it.accountName) it.displayName else "${it.displayName} (${it.accountName})" }
        )
        val last = getSharedPreferences(Utils.SHARED_PREFS_NAME, MODE_PRIVATE).getLong(PREF_LAST_TARGET, -1)
        spinner.setSelection(writable.indexOfFirst { it.id == last }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectTarget(writable[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        selectTarget(writable[spinner.selectedItemPosition.coerceAtLeast(0)])
    }

    private fun selectTarget(newTarget: ImportTarget) {
        target = newTarget
        val parsed = calendar ?: return
        lifecycleScope.launch {
            val actions = withContext(Dispatchers.IO) { importer.plan(parsed.events, parsed.method, newTarget.id) }
            if (target == newTarget) {
                adapter.setActions(actions)
                updateButton()
            }
        }
    }

    private fun updateButton() {
        val count = adapter.selectedRowCount()
        importButton.isEnabled = count > 0 && target != null
        importButton.text = if (count > 0) getString(R.string.import_button_count, count) else getString(R.string.import_button)
    }

    private fun runImport() {
        val parsed = calendar ?: return
        val chosen = target ?: return
        val events = adapter.selectedEvents()
        importButton.isEnabled = false
        getSharedPreferences(Utils.SHARED_PREFS_NAME, MODE_PRIVATE).edit().putLong(PREF_LAST_TARGET, chosen.id).apply()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { importer.import(events, parsed.method, chosen) }
            val message = if (result.failed == 0) {
                getString(R.string.import_result, result.added, result.updated, result.deleted)
            } else {
                getString(R.string.import_result_failed, result.added, result.updated, result.deleted, result.failed)
            }
            Toast.makeText(this@ImportActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.import_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val events = calendar?.events.orEmpty()
        menu.findItem(R.id.select_all)?.apply {
            isVisible = adapter.rowCount() > 1
            setTitle(if (adapter.allSelected()) R.string.import_select_none else R.string.import_select_all)
        }
        menu.findItem(R.id.edit_first)?.isVisible = events.size == 1 && !events[0].cancelled && !events[0].isException
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.select_all -> {
                adapter.selectAll(!adapter.allSelected())
                invalidateOptionsMenu()
            }
            R.id.edit_first -> calendar?.events?.firstOrNull()?.let { openInEditor(it) }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun openInEditor(event: IcsEvent) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = "vnd.android.cursor.item/event"
            setClass(this@ImportActivity, EditEventActivity::class.java)
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(ExtendedProperty.URL, event.url)
            putExtra(CalendarContract.Events.ORGANIZER, event.organizer)
            putExtra(CalendarContract.Events.RRULE, event.rrule)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, event.start.timeZone)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.millis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.millis)
            if (event.attendees.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, event.attendees.joinToString(",") { it.email })
            }
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cal_import_error_msg, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun describeWhen(event: IcsEvent): String {
        var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_YEAR
        val tz: String
        var end = event.end.millis
        if (event.allDay) {
            tz = "UTC"
            if (end > event.start.millis) end -= 1
        } else {
            flags = flags or DateUtils.FORMAT_SHOW_TIME
            if (android.text.format.DateFormat.is24HourFormat(this)) flags = flags or DateUtils.FORMAT_24HOUR
            tz = Utils.getTimeZone(this, null)
        }
        val text = DateUtils.formatDateRange(this, Formatter(StringBuilder(50), Locale.getDefault()), event.start.millis, end, flags, tz).toString()
        return if (event.isRecurring) "$text · ${getString(R.string.import_repeats)}" else text
    }

    private inner class EventAdapter(private val onChange: () -> Unit) : RecyclerView.Adapter<EventHolder>() {
        private var all: List<IcsEvent> = emptyList()
        private var allActions: List<ImportAction> = emptyList()
        private var rows: List<Int> = emptyList()
        private var events: List<IcsEvent> = emptyList()
        private var actions: List<ImportAction> = emptyList()
        private val selected = HashSet<Int>()

        fun submit(list: List<IcsEvent>) {
            all = list
            val seriesUids = list.filter { !it.isException && it.uid != null }.mapNotNull { it.uid }.toSet()
            rows = list.indices.filter { !list[it].isException || list[it].uid !in seriesUids }
            events = rows.map { list[it] }
            allActions = emptyList()
            actions = emptyList()
            selected.clear()
            notifyDataSetChanged()
        }

        fun setActions(list: List<ImportAction>) {
            allActions = list
            actions = rows.map { list[it] }
            selected.clear()
            actions.forEachIndexed { i, action -> if (action != ImportAction.SKIP) selected += i }
            notifyDataSetChanged()
        }

        fun selectedEvents(): List<IcsEvent> {
            val chosen = rows.filterIndexed { i, _ -> i in selected && actionAt(i) != ImportAction.SKIP }.toSet()
            val chosenUids = chosen.mapNotNull { all[it].uid }.toSet()
            return all.filterIndexed { i, e ->
                i in chosen || (e.isException && i !in rows && e.uid in chosenUids && allActions.getOrNull(i) != ImportAction.SKIP)
            }
        }

        fun rowCount(): Int = events.size

        fun selectedRowCount(): Int = selected.count { actionAt(it) != ImportAction.SKIP }

        fun allSelected(): Boolean = events.indices.all { it in selected || actionAt(it) == ImportAction.SKIP }

        fun selectAll(select: Boolean) {
            selected.clear()
            if (select) events.indices.filter { actionAt(it) != ImportAction.SKIP }.forEach { selected += it }
            notifyDataSetChanged()
            onChange()
        }

        private fun actionAt(i: Int): ImportAction? = actions.getOrNull(i)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            EventHolder(LayoutInflater.from(parent.context).inflate(R.layout.import_item, parent, false))

        override fun getItemCount() = events.size

        override fun onBindViewHolder(holder: EventHolder, position: Int) {
            val event = events[position]
            val action = actionAt(position)
            holder.title.text = event.title?.takeIf { it.isNotBlank() } ?: getString(R.string.import_untitled)
            holder.whenText.text = describeWhen(event)
            holder.where.text = event.location.orEmpty()
            holder.where.visibility = if (event.location.isNullOrBlank()) View.GONE else View.VISIBLE
            holder.action.text = when (action) {
                ImportAction.ADD -> getString(R.string.import_action_add)
                ImportAction.UPDATE -> getString(R.string.import_action_update)
                ImportAction.DELETE -> getString(R.string.import_action_delete)
                ImportAction.SKIP -> getString(R.string.import_action_skip)
                null -> ""
            }
            val enabled = action != null && action != ImportAction.SKIP
            holder.check.isEnabled = enabled
            holder.check.isChecked = enabled && position in selected
            holder.itemView.isEnabled = enabled
            holder.itemView.alpha = if (enabled) 1f else 0.6f
            holder.itemView.setOnClickListener {
                val row = holder.bindingAdapterPosition
                if (!enabled || row == RecyclerView.NO_POSITION) return@setOnClickListener
                if (!selected.remove(row)) selected += row
                notifyItemChanged(row)
                onChange()
                invalidateOptionsMenu()
            }
        }
    }

    private class EventHolder(view: View) : RecyclerView.ViewHolder(view) {
        val check: CheckBox = view.findViewById(R.id.check)
        val title: TextView = view.findViewById(R.id.title)
        val whenText: TextView = view.findViewById(R.id.`when`)
        val where: TextView = view.findViewById(R.id.where)
        val action: TextView = view.findViewById(R.id.action)
    }

    companion object {
        private const val PREF_LAST_TARGET = "import_last_calendar"
    }
}
