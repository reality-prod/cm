// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.codeberg.mobile.PagedList
import org.codeberg.mobile.RepoCard
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Screen
import org.codeberg.mobile.data.forks

/** Browse public forks and open any fork in the normal repository browser. */
@Composable
internal fun ForksScreen(state: AppState, full: String) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Forks", full, state::pop)
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Forks of $full", style = MaterialTheme.typography.titleMedium)
            Text(
                "Browse public copies of this repository. Select one to explore its files, issues, and pull requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PagedList(
            key = "forks:$full",
            fetch = { state.api.forks(full, it) },
            empty = "No public forks yet.",
        ) { repo ->
            RepoCard(repo) { state.push(Screen.RepoHome(repo.fullName)) }
        }
    }
}
