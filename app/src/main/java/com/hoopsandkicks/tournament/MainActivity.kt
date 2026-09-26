package com.hoopsandkicks.tournament

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.remote.ViewerStore
import com.hoopsandkicks.tournament.ui.AlgorithmScreen
import com.hoopsandkicks.tournament.ui.CreateTournamentScreen
import com.hoopsandkicks.tournament.ui.FixturesScreen
import com.hoopsandkicks.tournament.ui.GameFormatScreen
import com.hoopsandkicks.tournament.ui.HomeScreen
import com.hoopsandkicks.tournament.ui.HoopsTheme
import com.hoopsandkicks.tournament.ui.JoinTournamentScreen
import com.hoopsandkicks.tournament.ui.MatchReadyScreen
import com.hoopsandkicks.tournament.ui.MatchRoute
import com.hoopsandkicks.tournament.ui.PlayersScreen
import com.hoopsandkicks.tournament.ui.ReorderFixturesScreen
import com.hoopsandkicks.tournament.ui.SplitScreen
import com.hoopsandkicks.tournament.ui.TeamsScreen
import com.hoopsandkicks.tournament.ui.TournamentScreen
import com.hoopsandkicks.tournament.ui.ViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoopsTheme {
                AppNav()
            }
        }
    }
}

private fun stepRoute(t: Tournament): String = when {
    t.status != TStatus.DRAFT -> "t/${t.id}"
    t.draftStep <= 1 -> "setup/players/${t.id}"
    t.draftStep == 2 -> "setup/teams/${t.id}"
    t.draftStep == 3 -> "setup/split/${t.id}"
    t.draftStep == 4 -> "setup/schedule/${t.id}"
    else -> "setup/format/${t.id}"
}

@Composable
fun AppNav() {
    val nav: NavHostController = rememberNavController()
    // Shared by the "join" screen and Home's "Resume watching" shortcut — join (or rejoin) a room's
    // live feed and open the read-only viewer for it.
    val joinRoom: (String) -> Unit = { code ->
        ViewerStore.join(code)
        nav.navigate("view/$code") { popUpTo("home") }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNew = { nav.navigate("create") },
                onOpen = { t -> nav.navigate(stepRoute(t)) },
                onJoin = { nav.navigate("join") },
                onResumeWatching = joinRoom
            )
        }
        // Enter a room code to watch a live tournament read-only (JoinTournament design).
        composable("join") {
            JoinTournamentScreen(
                onBack = { nav.popBackStack() },
                onJoin = joinRoom
            )
        }
        // Read-only viewer of a live room (hub + leaderboards + fixtures; no match controls).
        composable("view/{code}") { e ->
            val code = e.arguments?.getString("code") ?: return@composable
            ViewerScreen(
                code,
                onBack = {
                    ViewerStore.leave()
                    nav.popBackStack("home", false)
                },
                onFixtures = { id -> nav.navigate("vfixtures/$id") }
            )
        }
        composable("vfixtures/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            FixturesScreen(id, onBack = { nav.popBackStack() }, onOpenMatch = { _, _ -> }, readOnly = true)
        }
        composable("create") {
            CreateTournamentScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate("setup/players/$id") { popUpTo("create") { inclusive = true } }
                }
            )
        }
        composable("setup/players/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            PlayersScreen(id, onBack = { nav.popBackStack() }, onNext = { nav.navigate("setup/teams/$id") })
        }
        composable("setup/teams/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            TeamsScreen(id, onBack = { nav.popBackStack() }, onNext = { nav.navigate("setup/split/$id") })
        }
        composable("setup/split/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            SplitScreen(id, onBack = { nav.popBackStack() }, onNext = { nav.navigate("setup/schedule/$id") })
        }
        composable("setup/schedule/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            AlgorithmScreen(id, onBack = { nav.popBackStack() }, onNext = { nav.navigate("setup/format/$id") })
        }
        composable("setup/format/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            GameFormatScreen(
                id, wizard = true,
                onBack = { nav.popBackStack() },
                onDone = { nav.navigate("t/$id") { popUpTo("home") } }
            )
        }
        composable("format/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            GameFormatScreen(id, wizard = false, onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
        }
        composable("t/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            TournamentScreen(
                id,
                onBack = { nav.popBackStack("home", false) },
                onFixtures = { nav.navigate("fixtures/$id") },
                onFormat = { nav.navigate("format/$id") },
                onOpenMatch = { mid, scheduled ->
                    nav.navigate(if (scheduled) "ready/$id/$mid" else "match/$id/$mid")
                },
                onDeleted = { nav.popBackStack("home", false) }
            )
        }
        composable("fixtures/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            FixturesScreen(
                id,
                onBack = { nav.popBackStack() },
                onOpenMatch = { mid, scheduled ->
                    nav.navigate(if (scheduled) "ready/$id/$mid" else "match/$id/$mid")
                },
                onReorder = { nav.navigate("reorder/$id") }
            )
        }
        composable("reorder/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            ReorderFixturesScreen(id, onBack = { nav.popBackStack() })
        }
        composable("ready/{id}/{mid}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            val mid = e.arguments?.getString("mid") ?: return@composable
            MatchReadyScreen(
                id, mid,
                onBack = { nav.popBackStack() },
                onChangeFormat = { nav.navigate("format/$id") },
                onStarted = { nav.navigate("match/$id/$mid") { popUpTo("ready/{id}/{mid}") { inclusive = true } } }
            )
        }
        composable("match/{id}/{mid}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            val mid = e.arguments?.getString("mid") ?: return@composable
            MatchRoute(id, mid, onExit = { nav.popBackStack() })
        }
    }
}
