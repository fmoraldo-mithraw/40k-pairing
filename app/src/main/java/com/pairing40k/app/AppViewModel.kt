package com.pairing40k.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pairing40k.app.data.AppData
import com.pairing40k.app.data.HistoryEntry
import com.pairing40k.app.data.MatrixImport
import com.pairing40k.app.data.Matchup
import com.pairing40k.app.data.PlayedGame
import com.pairing40k.app.data.Settings
import com.pairing40k.app.data.WorkbookImport
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

/**
 * Compte rendu du dernier import : les matrices sont déjà créées ; l'écran
 * permet d'ajuster le début de matrice ou l'échelle puis de ré-appliquer.
 */
class ImportReport(
    val source: String,
    val sheets: List<WorkbookImport.Sheet>,
    val results: List<WorkbookImport.SheetResult>,
    val scale: MatrixImport.Scale,
    val created: Int,
    val updated: Int,
    val error: String? = null
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

    private val _importReport = MutableStateFlow<ImportReport?>(null)
    val importReport: StateFlow<ImportReport?> = _importReport.asStateFlow()

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
        val m = Matchup.blank(cur.size, d.settings.defaultTeam, cur.ourPlayers)
        d.copy(matchups = d.matchups + m, activeId = m.id)
    }

    fun duplicate() = update { d ->
        val m = d.active.copy(id = java.util.UUID.randomUUID().toString(), theirTeam = d.active.theirTeam + " (copie)")
        d.copy(matchups = d.matchups + m, activeId = m.id)
    }

    fun deleteActive() = update { d ->
        val rest = d.matchups.filter { it.id != d.active.id }.ifEmpty { listOf(Matchup.blank(8, d.settings.defaultTeam)) }
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

    /** Classeur Excel (tous les onglets) ou CSV : crée / met à jour les matrices. */
    fun importBytes(source: String, bytes: ByteArray) {
        val sheets = try {
            if (Xlsx.isXlsx(bytes)) {
                val book = Xlsx.read(ByteArrayInputStream(bytes))
                book.sheetNames.mapIndexed { i, name -> WorkbookImport.Sheet(name, book.sheet(i)) }
            } else {
                listOf(WorkbookImport.Sheet(null, MatrixImport.parseCsv(String(bytes, Charsets.UTF_8))))
            }
        } catch (e: Exception) {
            _importReport.value = ImportReport(source, emptyList(), emptyList(), MatrixImport.Scale.AUTO, 0, 0, e.message ?: e.toString())
            return
        }
        applyImport(source, sheets, MatrixImport.Scale.AUTO)
    }

    /** (Ré)applique l'import avec les réglages courants de début de matrice. */
    fun applyImport(source: String, sheets: List<WorkbookImport.Sheet>, scale: MatrixImport.Scale) {
        val d = _data.value
        val fallback = source.substringBeforeLast('.').ifBlank { "Adversaire" }
        val results = WorkbookImport.analyze(sheets, d.settings, scale, d.active.maxScore, fallback)
        val merge = WorkbookImport.merge(d, results)
        if (merge.created + merge.updated > 0) update { merge.data }
        _importReport.value = ImportReport(
            source, sheets, results, scale, merge.created, merge.updated,
            if (results.none { it.ok }) "Aucune matrice reconnue" else null
        )
    }

    fun reapplyImport(scale: MatrixImport.Scale) {
        val r = _importReport.value ?: return
        applyImport(r.source, r.sheets, scale)
    }

    fun closeImport() { _importReport.value = null }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val (name, bytes) = withContext(Dispatchers.IO) {
                val n = runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull()
                n to runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) toast("Impossible de lire le fichier") else importBytes(name ?: uri.lastPathSegment ?: "Fichier", bytes)
        }
    }

    fun importText(source: String, text: String) = importBytes(source, text.toByteArray())

    /**
     * Lien Google Sheets : on télécharge le classeur en .xlsx pour avoir tous
     * les onglets avec leurs noms. Autre lien : contenu brut (CSV ou xlsx).
     */
    fun importUrl(url: String) {
        val target = MatrixImport.googleSheetsXlsxUrl(url) ?: url.trim()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(target).openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15_000
                    c.readTimeout = 30_000
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
                onFailure = {
                    _importReport.value = ImportReport("Google Sheets", emptyList(), emptyList(), MatrixImport.Scale.AUTO, 0, 0, it.message ?: it.toString())
                }
            )
        }
    }

    // ----------------------------------------------------------- réglages

    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    /** Donne le nom par défaut à notre équipe dans toutes les matrices. */
    fun applyDefaultTeamEverywhere() = update { d ->
        d.copy(matchups = d.matchups.map { it.copy(ourTeam = d.settings.defaultTeam) })
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
