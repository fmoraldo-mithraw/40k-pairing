package com.pairing40k.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pairing40k.app.data.AppData
import com.pairing40k.app.data.HistoryEntry
import com.pairing40k.app.data.MatrixImport
import com.pairing40k.app.data.Matchup
import com.pairing40k.app.data.PlayedGame
import com.pairing40k.app.data.Player
import com.pairing40k.app.data.Store
import com.pairing40k.app.data.Xlsx
import com.pairing40k.app.engine.Assignment
import com.pairing40k.app.engine.PairingSolver
import com.pairing40k.app.engine.PairingState
import com.pairing40k.app.engine.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Résultat d'analyse d'une matrice : le solveur reste chaud pour le pairing en direct. */
class Analysis(
    val matchupId: String,
    val scores: List<List<Double>>,
    val solver: PairingSolver,
    val value: Double,
    val secure: Double,
    val best: Assignment.Result,
    val worst: Assignment.Result,
    val probabilities: Array<DoubleArray>
)

/** Import en attente de confirmation (aperçu, choix de l'échelle). */
class PendingImport(
    val source: String,
    val rows: List<List<String>>,
    val result: MatrixImport.Result?,
    val error: String?,
    val scale: MatrixImport.Scale
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "data.json")

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _analysis = MutableStateFlow<Analysis?>(null)
    val analysis: StateFlow<Analysis?> = _analysis.asStateFlow()

    /** Pile des états du pairing en cours (le dernier est l'état courant). */
    private val _pairing = MutableStateFlow(listOf(PairingState.start(_data.value.active.size)))
    val pairing: StateFlow<List<PairingState>> = _pairing.asStateFlow()
    private var pairingFor: String = _data.value.active.id

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var analysisJob: Job? = null

    init {
        analyze()
    }

    // ------------------------------------------------------------ données

    private fun load(): AppData = try {
        if (file.exists()) Store.fromJson(file.readText()) else AppData()
    } catch (e: Exception) {
        AppData()
    }

    private fun update(f: (AppData) -> AppData) {
        val before = _data.value
        val after = f(before)
        _data.value = after
        val text = Store.toJson(after)
        viewModelScope.launch(Dispatchers.IO) {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
        val a = after.active
        if (a.id != pairingFor || a.size != _pairing.value.first().remaining) {
            pairingFor = a.id
            _pairing.value = listOf(PairingState.start(a.size))
        }
        if (before.active.id != a.id || before.active.scores != a.scores) analyze()
    }

    fun updateMatchup(f: (Matchup) -> Matchup) = update { d ->
        val cur = d.active
        d.copy(matchups = d.matchups.map { if (it.id == cur.id) f(it) else it }, activeId = cur.id)
    }

    fun select(id: String) = update { it.copy(activeId = id) }

    fun newMatchup() = update { d ->
        val cur = d.active
        val m = Matchup.blank(cur.size, cur.ourTeam, cur.ourPlayers)
        d.copy(matchups = d.matchups + m, activeId = m.id)
    }

    fun duplicate() = update { d ->
        val m = d.active.copy(id = java.util.UUID.randomUUID().toString(), theirTeam = d.active.theirTeam + " (copie)")
        d.copy(matchups = d.matchups + m, activeId = m.id)
    }

    fun deleteActive() = update { d ->
        val rest = d.matchups.filter { it.id != d.active.id }.ifEmpty { listOf(Matchup.blank(8)) }
        d.copy(matchups = rest, activeId = rest.last().id)
    }

    fun consumeMessage() { _message.value = null }
    fun toast(msg: String) { _message.value = msg }

    // ------------------------------------------------------------ analyse

    private fun analyze() {
        val m = _data.value.active
        analysisJob?.cancel()
        if (m.size !in setOf(4, 6, 8)) { _analysis.value = null; return }
        if (_analysis.value?.let { it.matchupId == m.id && it.scores == m.scores } == true) return
        _analysis.value = null
        analysisJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val solver = PairingSolver(m.scores, m.maxScore)
                val full = solver.fullMask
                Analysis(
                    m.id, m.scores, solver,
                    solver.stateValue(full, full),
                    solver.stateValue(full, full, PairingSolver.Mode.SECURE),
                    Assignment.best(m.scores),
                    Assignment.worst(m.scores),
                    solver.matchProbabilities()
                )
            }
            _analysis.value = result
        }
    }

    // ------------------------------------------------------------ pairing

    fun pushPairing(state: PairingState) { _pairing.value = _pairing.value + state }

    fun undoPairing() {
        if (_pairing.value.size > 1) _pairing.value = _pairing.value.dropLast(1)
    }

    fun resetPairing() { _pairing.value = listOf(PairingState.start(_data.value.active.size)) }

    fun savePairingToHistory() {
        val st = _pairing.value.last()
        if (st.phase != Phase.DONE) return
        val m = _data.value.active
        val a = _analysis.value
        val entry = HistoryEntry(
            ourTeam = m.ourTeam,
            theirTeam = m.theirTeam,
            maxScore = m.maxScore,
            expectedBefore = a?.value ?: 0.0,
            games = st.games.map { g ->
                PlayedGame(m.ourPlayers[g.our].label, m.theirPlayers[g.their].label, g.kind.label, m.scores[g.our][g.their])
            }
        )
        update { it.copy(history = listOf(entry) + it.history) }
        toast("Pairing enregistré dans l'historique")
    }

    fun setActual(entryId: String, gameIndex: Int, value: Double?) = update { d ->
        d.copy(history = d.history.map { h ->
            if (h.id != entryId) h else h.copy(games = h.games.mapIndexed { i, g -> if (i == gameIndex) g.copy(actual = value) else g })
        })
    }

    fun deleteHistory(id: String) = update { d -> d.copy(history = d.history.filter { it.id != id }) }

    // ------------------------------------------------------------- import

    fun importBytes(source: String, bytes: ByteArray) {
        try {
            val rows = if (Xlsx.isXlsx(bytes)) {
                Xlsx.read(ByteArrayInputStream(bytes)).sheet(0)
            } else {
                MatrixImport.parseCsv(String(bytes, Charsets.UTF_8))
            }
            preview(source, rows, MatrixImport.Scale.AUTO)
        } catch (e: Exception) {
            _pendingImport.value = PendingImport(source, emptyList(), null, e.message ?: e.toString(), MatrixImport.Scale.AUTO)
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) toast("Impossible de lire le fichier") else importBytes(uri.lastPathSegment ?: "Fichier", bytes)
        }
    }

    fun importText(source: String, text: String) = importBytes(source, text.toByteArray())

    fun importUrl(url: String) {
        val csvUrl = MatrixImport.googleSheetsCsvUrl(url) ?: url.trim()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(csvUrl).openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15_000
                    c.readTimeout = 15_000
                    val code = c.responseCode
                    if (code != 200) error("HTTP $code — le fichier est-il partagé « tous les utilisateurs disposant du lien » ?")
                    val type = c.contentType.orEmpty()
                    val bytes = c.inputStream.use { it.readBytes() }
                    if (type.contains("html")) error("Google a renvoyé une page web : vérifie le partage du fichier (lecture pour tous avec le lien)")
                    bytes
                }
            }
            result.fold(
                onSuccess = { importBytes("Google Sheets", it) },
                onFailure = { _pendingImport.value = PendingImport("Google Sheets", emptyList(), null, it.message ?: it.toString(), MatrixImport.Scale.AUTO) }
            )
        }
    }

    fun preview(source: String, rows: List<List<String>>, scale: MatrixImport.Scale) {
        val m = _data.value.active
        _pendingImport.value = try {
            PendingImport(source, rows, MatrixImport.parseTable(rows, m.maxScore, scale), null, scale)
        } catch (e: MatrixImport.ImportException) {
            PendingImport(source, rows, null, e.message, scale)
        }
    }

    fun cancelImport() { _pendingImport.value = null }

    /** [asNew] : crée une nouvelle confrontation au lieu d'écraser l'active. */
    fun confirmImport(asNew: Boolean) {
        val r = _pendingImport.value?.result ?: return
        _pendingImport.value = null
        val n = r.size
        val fit = if (n in setOf(4, 6, 8)) n else listOf(8, 6, 4).first { it <= n }
        fun <T> cut(l: List<T>) = l.take(fit)
        val apply = { m: Matchup ->
            m.copy(
                ourPlayers = cut(r.ourPlayers),
                theirPlayers = cut(r.theirPlayers),
                scores = cut(r.scores).map { cut(it) }
            )
        }
        if (asNew) {
            update { d ->
                val m = apply(Matchup.blank(fit, d.active.ourTeam))
                d.copy(matchups = d.matchups + m, activeId = m.id)
            }
        } else updateMatchup(apply)
        toast("Matrice ${fit}×$fit importée")
    }

    // ---------------------------------------------------- sauvegarde JSON

    fun backupJson(): String = Store.toJson(_data.value)

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
            }
            val restored = text?.let { runCatching { Store.fromJson(it) }.getOrNull() }
            if (restored == null) { toast("Sauvegarde illisible"); return@launch }
            // Fusion : on ajoute les confrontations et l'historique absents
            update { d ->
                val ids = d.matchups.map { it.id }.toSet()
                val hIds = d.history.map { it.id }.toSet()
                d.copy(
                    matchups = d.matchups + restored.matchups.filter { it.id !in ids },
                    history = (d.history + restored.history.filter { it.id !in hIds }).sortedByDescending { it.date }
                )
            }
            toast("Sauvegarde fusionnée")
        }
    }

    fun writeTo(uri: Uri, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            }.isSuccess
            _message.value = if (ok) "Fichier enregistré" else "Échec de l'écriture"
        }
    }

    fun renamePlayer(ours: Boolean, index: Int, p: Player) = updateMatchup { m ->
        if (ours) m.copy(ourPlayers = m.ourPlayers.mapIndexed { i, x -> if (i == index) p else x })
        else m.copy(theirPlayers = m.theirPlayers.mapIndexed { i, x -> if (i == index) p else x })
    }
}
