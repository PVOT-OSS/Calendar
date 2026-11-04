/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.search

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.prauga.calendar.CalendarRecentSuggestionsProvider
import org.prauga.calendar.R
import org.prauga.calendar.Utils
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.AgendaRepository
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Actions
import org.prauga.calendar.ui.Formats
import org.prauga.calendar.ui.components.EventCard

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AgendaRepository(application)
    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Agenda>> = query
        .debounce(250)
        .mapLatest { q ->
            val now = System.currentTimeMillis()
            repository.search(q, now - YEAR, now + 2 * YEAR)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        const val YEAR = 366L * 24 * 3_600_000
    }
}

class SearchActivity : PvotBaseActivity() {

    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setPvotContent {
            PvotAppTheme {
                SearchScreen(viewModel, onBack = ::finish, onSubmit = ::saveQuery)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action == Intent.ACTION_SEARCH) {
            intent.getStringExtra(SearchManager.QUERY)?.let {
                viewModel.query.value = it
                saveQuery(it)
            }
        }
    }

    private fun saveQuery(query: String) {
        if (query.isBlank()) return
        SearchRecentSuggestions(this, Utils.getSearchAuthority(this), CalendarRecentSuggestionsProvider.MODE)
            .saveRecentQuery(query.trim(), null)
    }
}

@Composable
private fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    val context = LocalContext.current
    val formats = remember { Formats(context) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PvotIconButton(painterResource(R.drawable.ic_ui_back), stringResource(R.string.ui_back), onBack)
            PvotTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = stringResource(R.string.search),
                singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_menu_search), null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
            )
        }
        val grouped = remember(results) { results.groupBy { it.startDay } }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (query.isNotBlank() && results.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.ui_no_results),
                        Modifier.fillMaxWidth().padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            grouped.forEach { (day, items) ->
                item(key = "day-$day") {
                    Text(
                        formats.dayLabel(JulianDay.toDate(day)),
                        Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items, key = { "${it.eventId}-${it.begin}" }) { agenda ->
                    EventCard(
                        title = agenda.title,
                        time = formats.timeRange(agenda),
                        color = Color(agenda.color),
                        onClick = {
                            onSubmit(query)
                            Actions.open(context, agenda)
                        },
                    )
                }
            }
        }
    }
}
