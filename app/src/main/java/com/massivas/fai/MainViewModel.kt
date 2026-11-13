package com.massivas.fai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val release: Updater.Release, val progress: Float) : UpdateState
    data class Ready(val release: Updater.Release, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class ImportSummary(val added: Int, val updated: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "vehicles.json")
    private val saveLock = Mutex()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    private val prefs = app.getSharedPreferences("prefs", 0)
    var lastModel: CarModel?
        get() = enumOrNull<CarModel>(prefs.getString("lastModel", null))
        set(v) { prefs.edit().putString("lastModel", v?.name).apply() }

    var shareTitle: String
        get() = prefs.getString("shareTitle", null) ?: "MASSIVA FAI"
        set(v) { prefs.edit().putString("shareTitle", v).apply() }

    private var checkedOnStart = false

    init {
        viewModelScope.launch {
            _vehicles.value = withContext(Dispatchers.IO) { load() }
        }
    }

    private fun load(): List<Vehicle> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { Vehicle.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist(list: List<Vehicle>) {
        _vehicles.value = list
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock {
                // Sempre grava o estado mais recente.
                val arr = JSONArray().apply { _vehicles.value.forEach { put(it.toJson()) } }
                val tmp = File(file.parentFile, "vehicles.json.tmp")
                tmp.writeText(arr.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(arr.toString()); tmp.delete()
                }
            }
        }
    }

    fun findByVin(vin: String): Vehicle? = _vehicles.value.firstOrNull { it.vin == cleanVin(vin) }

    /** Sugere o modelo pelo VIN completo (posições 1–8 = fabricante + modelo). */
    fun suggestModel(vin: String): CarModel? {
        val v = cleanVin(vin)
        findByVin(v)?.let { return it.model }
        if (v.length < 11) return null
        val prefix = v.take(8)
        val models = _vehicles.value.filter { it.vin.length >= 11 && it.vin.startsWith(prefix) }.map { it.model }.toSet()
        return models.singleOrNull()
    }

    fun save(vehicle: Vehicle) {
        val v = vehicle.copy(vin = cleanVin(vehicle.vin), updatedAt = System.currentTimeMillis())
        val list = _vehicles.value.toMutableList()
        val sameVin = list.indexOfFirst { it.vin == v.vin && it.id != v.id }
        if (sameVin >= 0) list.removeAt(sameVin)
        val idx = list.indexOfFirst { it.id == v.id }
        if (idx >= 0) list[idx] = v else list += v
        lastModel = v.model
        persist(list)
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun delete(id: String) = persist(_vehicles.value.filterNot { it.id == id })

    fun setStatus(id: String, status: Status) = persist(_vehicles.value.map {
        if (it.id == id) it.copy(status = status, updatedAt = System.currentTimeMillis()) else it
    })

    fun toggleRepair(id: String, station: Station) = persist(_vehicles.value.map { v ->
        if (v.id != id) v else v.copy(
            repairs = v.repairs.map { if (it.station == station) it.copy(done = !it.done) else it },
            updatedAt = System.currentTimeMillis(),
        )
    })

    var shareIncludeReleased: Boolean
        get() = prefs.getBoolean("shareReleased", true)
        set(v) { prefs.edit().putBoolean("shareReleased", v).apply() }
    var shareIncludeDate: Boolean
        get() = prefs.getBoolean("shareDate", true)
        set(v) { prefs.edit().putBoolean("shareDate", v).apply() }

    fun clearReleased() = persist(_vehicles.value.filterNot { it.status == Status.LIBERADO })

    fun clearAll() = persist(emptyList())

    fun importParsed(parsed: List<ParsedVehicle>, fallbackModel: CarModel?): ImportSummary {
        val list = _vehicles.value.toMutableList()
        var added = 0
        var updated = 0
        for (p in parsed) {
            val idx = list.indexOfFirst { it.vin == p.vin }
            if (idx >= 0) {
                val old = list[idx]
                list[idx] = old.copy(
                    model = p.model ?: old.model,
                    status = p.status ?: old.status,
                    repairs = if (p.repairs.isNotEmpty()) p.repairs else if (p.status == Status.LIBERADO || p.status == Status.TUNEL) old.repairs.map { it.copy(done = true) } else old.repairs,
                    notes = p.notes.ifBlank { old.notes },
                    forecast = p.forecast ?: old.forecast,
                    difficulty = p.difficulty ?: old.difficulty,
                    updatedAt = System.currentTimeMillis(),
                )
                updated++
            } else {
                val model = p.model ?: fallbackModel ?: continue
                list += Vehicle(
                    id = newId(), vin = p.vin, model = model,
                    status = p.status ?: Status.PROCESSO, repairs = p.repairs, notes = p.notes,
                    forecast = p.forecast, difficulty = p.difficulty,
                )
                added++
            }
        }
        persist(list)
        return ImportSummary(added, updated)
    }

    // ---------- Atualizações ----------

    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        checkForUpdate(silent = true)
    }

    fun checkForUpdate(silent: Boolean = false) {
        if (_update.value is UpdateState.Downloading) return
        viewModelScope.launch {
            if (!silent) _update.value = UpdateState.Checking
            try {
                val rel = Updater.fetchLatest()
                _update.value = when {
                    rel != null && rel.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(rel)
                    silent -> UpdateState.Idle
                    else -> UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _update.value = if (silent) UpdateState.Idle
                else UpdateState.Error("Não foi possível verificar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun download(release: Updater.Release) {
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(release, 0f)
            try {
                val f = Updater.download(getApplication(), release) { p ->
                    _update.value = UpdateState.Downloading(release, p)
                }
                _update.value = UpdateState.Ready(release, f)
            } catch (e: Exception) {
                _update.value = UpdateState.Error("Falha no download: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun dismissUpdate() {
        if (_update.value !is UpdateState.Downloading) _update.value = UpdateState.Idle
    }
}
