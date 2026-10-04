package app.luogo.app.ui.map

import app.luogo.app.data.map.MapAndRoutingProvider
import app.luogo.app.domain.model.MapStyleOption

/**
 * Builds MapLibre style documents in code.
 *
 * Why generated rather than shipped as a JSON asset:
 *  - the tile User-Agent must be injected, and OSM refuses tiles without one
 *  - a self-hoster can point satellite at their own imagery service
 *  - attribution is always kept in step with whichever source is actually in use
 *
 * Every basemap here is OpenStreetMap-derived or Esri. Google tile services are never used,
 * so the app carries no Google Maps dependency and no Google requests.
 */
object MapStyleFactory {

    const val SOURCE_BASEMAP = "basemap"
    const val SOURCE_PEOPLE = "people"
    const val SOURCE_ITEMS = "items"
    const val SOURCE_PLACES = "places"
    const val SOURCE_ROUTE = "route"

    const val LAYER_GEOFENCE_FILL = "geofence-fill"
    const val LAYER_GEOFENCE_LINE = "geofence-line"
    const val LAYER_ROUTE_CASING = "route-casing"
    const val LAYER_ROUTE = "route"
    const val LAYER_ACCURACY = "accuracy"
    const val LAYER_ITEM_HALO = "item-halo"
    const val LAYER_ITEM = "item"
    const val LAYER_ITEM_LABEL = "item-label"
    const val LAYER_PEOPLE_ACCURACY = "people-accuracy"
    const val LAYER_PERSON = "person"
    const val LAYER_PERSON_LABEL = "person-label"
    const val LAYER_ME_ACCURACY = "me-accuracy"
    const val LAYER_ME = "me"

    /**
     * @param customSatelliteUrl user-configured imagery template, or null to use the default
     */
    fun build(
        style: MapStyleOption,
        customSatelliteUrl: String? = null
    ): String {
        val tiles = tileUrlsFor(style, customSatelliteUrl)
        val attribution = attributionFor(style, customSatelliteUrl)
        val dark = style == MapStyleOption.PROTOMAPS_DARK

        return """
        {
          "version": 8,
          "name": "Luogo-FOSS",
          "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
          "sources": {
            "$SOURCE_BASEMAP": {
              "type": "raster",
              "tiles": [${tiles}],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19,
              "attribution": "${escapeJson(attribution)}",
              "headers": { "User-Agent": "${escapeJson(MapAndRoutingProvider.USER_AGENT)}" }
            },
            "$SOURCE_PEOPLE": { "type": "geojson", "data": ${emptyFeatureCollection()} },
            "$SOURCE_ITEMS":  { "type": "geojson", "data": ${emptyFeatureCollection()} },
            "$SOURCE_PLACES": { "type": "geojson", "data": ${emptyFeatureCollection()} },
            "$SOURCE_ROUTE":  { "type": "geojson", "data": ${emptyFeatureCollection()} }
          },
          "layers": [
            {
              "id": "basemap-layer",
              "type": "raster",
              "source": "$SOURCE_BASEMAP",
              "minzoom": 0,
              "maxzoom": 22
            },
            {
              "id": "$LAYER_GEOFENCE_FILL",
              "type": "fill",
              "source": "$SOURCE_PLACES",
              "filter": ["==", ["geometry-type"], "Polygon"],
              "paint": {
                "fill-color": "${if (dark) "#7C8CF8" else "#3F51B5"}",
                "fill-opacity": 0.10
              }
            },
            {
              "id": "$LAYER_GEOFENCE_LINE",
              "type": "line",
              "source": "$SOURCE_PLACES",
              "filter": ["==", ["geometry-type"], "Polygon"],
              "paint": {
                "line-color": "${if (dark) "#A5B0FF" else "#3F51B5"}",
                "line-width": 2,
                "line-opacity": 0.75,
                "line-dasharray": [3, 2]
              }
            },
            {
              "id": "$LAYER_ROUTE_CASING",
              "type": "line",
              "source": "$SOURCE_ROUTE",
              "layout": { "line-cap": "round", "line-join": "round" },
              "paint": { "line-color": "#FFFFFF", "line-width": 9, "line-opacity": 0.9 }
            },
            {
              "id": "$LAYER_ROUTE",
              "type": "line",
              "source": "$SOURCE_ROUTE",
              "layout": { "line-cap": "round", "line-join": "round" },
              "paint": { "line-color": "#1E88E5", "line-width": 6 }
            },
            {
              "id": "$LAYER_ACCURACY",
              "type": "circle",
              "source": "$SOURCE_PEOPLE",
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 3, 4, 16, 14],
                "circle-color": ["get", "color"],
                "circle-opacity": 0.14,
                "circle-stroke-width": 1.5,
                "circle-stroke-color": ["get", "color"],
                "circle-stroke-opacity": 0.45
              }
            },
            {
              "id": "$LAYER_ITEM_HALO",
              "type": "circle",
              "source": "$SOURCE_ITEMS",
              "minzoom": 8,
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 8, 7, 16, 13],
                "circle-color": "${if (dark) "#000000" else "#FFFFFF"}",
                "circle-opacity": 0.9
              }
            },
            {
              "id": "$LAYER_ITEM",
              "type": "circle",
              "source": "$SOURCE_ITEMS",
              "minzoom": 6,
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 6, 4, 16, 9],
                "circle-color": ["get", "color"],
                "circle-opacity": ["case", ["==", ["get", "stale"], true], 0.45, 1.0]
              }
            },
            {
              "id": "$LAYER_ITEM_LABEL",
              "type": "symbol",
              "source": "$SOURCE_ITEMS",
              "minzoom": 11,
              "layout": {
                "text-field": ["get", "label"],
                "text-size": 11,
                "text-offset": [0, 1.9],
                "text-anchor": "top",
                "text-allow-overlap": false
              },
              "paint": {
                "text-color": "${if (dark) "#F1F5F9" else "#0F172A"}",
                "text-halo-color": "${if (dark) "#0B1117" else "#FFFFFF"}",
                "text-halo-width": 1.6
              }
            },
            {
              "id": "$LAYER_PEOPLE_ACCURACY",
              "type": "circle",
              "source": "$SOURCE_PEOPLE",
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 3, 5, 16, 16],
                "circle-color": ["get", "color"],
                "circle-opacity": 0.15,
                "circle-stroke-width": 2,
                "circle-stroke-color": ["get", "color"],
                "circle-stroke-opacity": 0.5
              }
            },
            {
              "id": "$LAYER_PERSON",
              "type": "circle",
              "source": "$SOURCE_PEOPLE",
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 3, 5, 16, 11],
                "circle-color": ["get", "color"],
                "circle-stroke-width": 2,
                "circle-stroke-color": "#FFFFFF",
                "circle-opacity": ["case", ["==", ["get", "stale"], true], 0.55, 1.0]
              }
            },
            {
              "id": "$LAYER_PERSON_LABEL",
              "type": "symbol",
              "source": "$SOURCE_PEOPLE",
              "minzoom": 10,
              "layout": {
                "text-field": ["get", "label"],
                "text-size": 12,
                "text-offset": [0, 2.1],
                "text-anchor": "top",
                "text-allow-overlap": false
              },
              "paint": {
                "text-color": "${if (dark) "#F8FAFC" else "#0F172A"}",
                "text-halo-color": "${if (dark) "#0B1117" else "#FFFFFF"}",
                "text-halo-width": 1.8
              }
            },
            {
              "id": "$LAYER_ME_ACCURACY",
              "type": "circle",
              "source": "$SOURCE_PEOPLE",
              "filter": ["==", ["get", "id"], "__me__"],
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 3, 6, 17, 20],
                "circle-color": "#1E88E5",
                "circle-opacity": 0.16,
                "circle-stroke-width": 2,
                "circle-stroke-color": "#1E88E5",
                "circle-stroke-opacity": 0.55
              }
            },
            {
              "id": "$LAYER_ME",
              "type": "circle",
              "source": "$SOURCE_PEOPLE",
              "filter": ["==", ["get", "id"], "__me__"],
              "paint": {
                "circle-radius": ["interpolate", ["linear"], ["zoom"], 3, 6, 17, 10],
                "circle-color": "#1E88E5",
                "circle-stroke-width": 3,
                "circle-stroke-color": "#FFFFFF"
              }
            }
          ]
        }
        """.trimIndent()
    }

    /** Tile templates for a style. The custom satellite URL wins when it is valid. */
    fun tileUrlsFor(style: MapStyleOption, customSatelliteUrl: String?): String {
        val template = if (style == MapStyleOption.SATELLITE_IMAGERY &&
            !customSatelliteUrl.isNullOrBlank()
        ) {
            customSatelliteUrl
        } else {
            style.tileUrlTemplate
        }
        return "\"${escapeJson(template)}\""
    }

    fun attributionFor(style: MapStyleOption, customSatelliteUrl: String?): String {
        val usingCustom = style == MapStyleOption.SATELLITE_IMAGERY &&
            !customSatelliteUrl.isNullOrBlank()
        return if (usingCustom) "Imagery: user-configured source" else style.attribution
    }

    fun emptyFeatureCollection(): String = """{"type":"FeatureCollection","features":[]}"""

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
}