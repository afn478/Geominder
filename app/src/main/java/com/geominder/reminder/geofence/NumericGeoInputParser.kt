package com.geominder.reminder.geofence

data class NumericGeoInput(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
)

enum class GeoInputField { LATITUDE, LONGITUDE, RADIUS }

enum class GeoInputError { REQUIRED, NOT_A_NUMBER, OUT_OF_RANGE, NOT_POSITIVE }

sealed interface NumericGeoInputResult {
    data class Valid(val value: NumericGeoInput) : NumericGeoInputResult
    data class Invalid(val errors: Map<GeoInputField, GeoInputError>) : NumericGeoInputResult
}

/** Strict decimal-number parsing for the map-free coordinate form. */
class NumericGeoInputParser(private val defaultRadiusMeters: Double) {
    init {
        require(defaultRadiusMeters.isFinite() && defaultRadiusMeters > 0.0)
    }

    fun parse(
        latitudeText: String,
        longitudeText: String,
        radiusText: String,
    ): NumericGeoInputResult {
        val errors = linkedMapOf<GeoInputField, GeoInputError>()
        val latitude = parseCoordinate(
            text = latitudeText,
            field = GeoInputField.LATITUDE,
            range = -90.0..90.0,
            errors = errors,
        )
        val longitude = parseCoordinate(
            text = longitudeText,
            field = GeoInputField.LONGITUDE,
            range = -180.0..180.0,
            errors = errors,
        )
        val radius = if (radiusText.isBlank()) {
            defaultRadiusMeters
        } else {
            radiusText.trim().toDoubleOrNull().also { value ->
                when {
                    value == null || !value.isFinite() -> {
                        errors[GeoInputField.RADIUS] = GeoInputError.NOT_A_NUMBER
                    }
                    value <= 0.0 -> errors[GeoInputField.RADIUS] = GeoInputError.NOT_POSITIVE
                }
            }
        }

        return if (errors.isEmpty()) {
            NumericGeoInputResult.Valid(
                NumericGeoInput(
                    latitude = requireNotNull(latitude),
                    longitude = requireNotNull(longitude),
                    radiusMeters = requireNotNull(radius),
                ),
            )
        } else {
            NumericGeoInputResult.Invalid(errors.toMap())
        }
    }

    private fun parseCoordinate(
        text: String,
        field: GeoInputField,
        range: ClosedFloatingPointRange<Double>,
        errors: MutableMap<GeoInputField, GeoInputError>,
    ): Double? {
        if (text.isBlank()) {
            errors[field] = GeoInputError.REQUIRED
            return null
        }
        val value = text.trim().toDoubleOrNull()
        when {
            value == null || !value.isFinite() -> errors[field] = GeoInputError.NOT_A_NUMBER
            value !in range -> errors[field] = GeoInputError.OUT_OF_RANGE
        }
        return value
    }
}
