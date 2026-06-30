package com.act.geomapper.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class DistanceUnit { METERS, KILOMETERS }
enum class AreaUnit     { SQUARE_METERS, SQUARE_KILOMETERS, HECTARES }
enum class AppLanguage  { SPANISH, ENGLISH }
enum class CoordFormat  { DECIMAL, DMS }   // Decimal o Grados°Minutos'Segundos"

data class AppSettings(
    val distanceUnit     : DistanceUnit = DistanceUnit.METERS,
    val areaUnit         : AreaUnit     = AreaUnit.HECTARES,
    val language         : AppLanguage  = AppLanguage.SPANISH,
    val darkMode         : Boolean      = false,
    val coordFormat      : CoordFormat  = CoordFormat.DECIMAL,
    val rellenoPoligonos : Boolean      = true   // toggle desde ImportSheet
)

fun formatCoord(deg: Double, format: CoordFormat, isLat: Boolean): String {
    if (format == CoordFormat.DECIMAL) {
        return "%.6f°".format(deg)
    }
    // DMS
    val abs = Math.abs(deg)
    val d   = abs.toInt()
    val mf  = (abs - d) * 60
    val m   = mf.toInt()
    val s   = (mf - m) * 60
    val hem = when {
        isLat  -> if (deg >= 0) "N" else "S"
        else   -> if (deg >= 0) "E" else "O"
    }
    return "%d°%02d'%05.2f\"%s".format(d, m, s, hem)
}

fun Double.toDisplayArea(unit: AreaUnit): String = when (unit) {
    AreaUnit.SQUARE_METERS      -> "%.1f m²".format(this * 10_000)
    AreaUnit.SQUARE_KILOMETERS  -> "%.6f km²".format(this / 100)
    AreaUnit.HECTARES           -> "%.4f ha".format(this)
}

fun Double.toDisplayDistance(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METERS      -> "%.1f m".format(this)
    DistanceUnit.KILOMETERS  -> "%.4f km".format(this / 1000)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DISTANCE_UNIT      = stringPreferencesKey("distance_unit")
        val AREA_UNIT          = stringPreferencesKey("area_unit")
        val LANGUAGE           = stringPreferencesKey("language")
        val DARK_MODE          = booleanPreferencesKey("dark_mode")
        val COORD_FORMAT       = stringPreferencesKey("coord_format")
        val RELLENO_POLIGONOS  = booleanPreferencesKey("relleno_poligonos")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                distanceUnit = prefs[Keys.DISTANCE_UNIT]
                    ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrDefault(DistanceUnit.METERS) }
                    ?: DistanceUnit.METERS,
                areaUnit = prefs[Keys.AREA_UNIT]
                    ?.let { runCatching { AreaUnit.valueOf(it) }.getOrDefault(AreaUnit.HECTARES) }
                    ?: AreaUnit.HECTARES,
                language = prefs[Keys.LANGUAGE]
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrDefault(AppLanguage.SPANISH) }
                    ?: AppLanguage.SPANISH,
                darkMode         = prefs[Keys.DARK_MODE] ?: false,
                coordFormat      = prefs[Keys.COORD_FORMAT]
                    ?.let { runCatching { CoordFormat.valueOf(it) }.getOrDefault(CoordFormat.DECIMAL) }
                    ?: CoordFormat.DECIMAL,
                rellenoPoligonos = prefs[Keys.RELLENO_POLIGONOS] ?: true
            )
        }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { it[Keys.DISTANCE_UNIT] = unit.name }
    }

    suspend fun setAreaUnit(unit: AreaUnit) {
        context.dataStore.edit { it[Keys.AREA_UNIT] = unit.name }
    }

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang.name }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = dark }
    }

    suspend fun setCoordFormat(fmt: CoordFormat) {
        context.dataStore.edit { it[Keys.COORD_FORMAT] = fmt.name }
    }

    suspend fun setRellenoPoligonos(valor: Boolean) {
        context.dataStore.edit { it[Keys.RELLENO_POLIGONOS] = valor }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
