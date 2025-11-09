package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 *
 * @param nodeId The OpenStreetMap node ID.
 * @param lat The latitude coordinate of the node.
 * @param lon The longitude coordinate of the node.
 * @param tags A map of OSM tags (key-value pairs) containing metadata like name, address, etc.
 */
@Builder
public record OsmNode(
        @NonNull Long nodeId,
        @Nullable Double lat,
        @Nullable Double lon,
        @NonNull Map<String, String> tags
) {
}
