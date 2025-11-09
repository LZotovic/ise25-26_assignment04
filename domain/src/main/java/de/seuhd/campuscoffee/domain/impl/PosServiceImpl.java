package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException, OsmNodeMissingFieldsException, DuplicatePosNameException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Extracts relevant tags (name, address, opening_hours, website) and coordinates,
     * and maps them to the POS model.
     *
     * @param osmNode the OSM node to convert
     * @return the converted POS object
     * @throws OsmNodeMissingFieldsException if required fields are missing
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) throws OsmNodeMissingFieldsException {
        var tags = osmNode.tags();

        // Extract name (required)
        String name = tags.get("name");
        if (name == null || name.isBlank()) {
            log.error("OSM node {} is missing required 'name' tag", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Extract address fields (required)
        String street = tags.get("addr:street");
        String houseNumber = tags.get("addr:housenumber");
        String postalCodeStr = tags.get("addr:postcode");
        String city = tags.get("addr:city");

        if (street == null || street.isBlank()) {
            log.error("OSM node {} is missing required 'addr:street' tag", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (houseNumber == null || houseNumber.isBlank()) {
            log.error("OSM node {} is missing required 'addr:housenumber' tag", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (postalCodeStr == null || postalCodeStr.isBlank()) {
            log.error("OSM node {} is missing required 'addr:postcode' tag", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (city == null || city.isBlank()) {
            log.error("OSM node {} is missing required 'addr:city' tag", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Parse postal code
        Integer postalCode;
        try {
            postalCode = Integer.parseInt(postalCodeStr.trim());
        } catch (NumberFormatException e) {
            log.error("OSM node {} has invalid postal code: {}", osmNode.nodeId(), postalCodeStr);
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Extract description (use website, opening_hours, or default)
        String description = buildDescription(tags);

        // Determine POS type from OSM tags
        PosType posType = determinePosType(tags);

        // Determine campus type (default to ALTSTADT if coordinates are not available or cannot be determined)
        CampusType campus = determineCampusType(osmNode);

        log.debug("Converting OSM node {} to POS: name={}, type={}, campus={}", 
                osmNode.nodeId(), name, posType, campus);

        return Pos.builder()
                .name(name)
                .description(description)
                .type(posType)
                .campus(campus)
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(postalCode)
                .city(city)
                .build();
    }

    /**
     * Builds a description from OSM tags.
     * Uses website, opening_hours, or a default description.
     *
     * @param tags the OSM tags
     * @return the description string
     */
    private @NonNull String buildDescription(@NonNull Map<String, String> tags) {
        String website = tags.get("website");
        String openingHours = tags.get("opening_hours");

        if (website != null && !website.isBlank()) {
            return "Website: " + website + (openingHours != null && !openingHours.isBlank() 
                    ? " | Opening hours: " + openingHours : "");
        } else if (openingHours != null && !openingHours.isBlank()) {
            return "Opening hours: " + openingHours;
        } else {
            return "Imported from OpenStreetMap";
        }
    }

    /**
     * Determines the POS type from OSM tags.
     * Checks amenity and shop tags to map to PosType enum.
     *
     * @param tags the OSM tags
     * @return the determined PosType (defaults to CAFE)
     */
    private @NonNull PosType determinePosType(@NonNull Map<String, String> tags) {
        String amenity = tags.get("amenity");
        String shop = tags.get("shop");

        // Check amenity tag
        if (amenity != null) {
            String amenityLower = amenity.toLowerCase();
            if (amenityLower.equals("cafe") || amenityLower.equals("coffee_shop")) {
                return PosType.CAFE;
            } else if (amenityLower.equals("canteen") || amenityLower.equals("food_court")) {
                return PosType.CAFETERIA;
            }
        }

        // Check shop tag
        if (shop != null) {
            String shopLower = shop.toLowerCase();
            if (shopLower.equals("bakery")) {
                return PosType.BAKERY;
            } else if (shopLower.equals("coffee") || shopLower.equals("cafe")) {
                return PosType.CAFE;
            }
        }

        // Default to CAFE if no specific type can be determined
        log.debug("Could not determine POS type from tags, defaulting to CAFE");
        return PosType.CAFE;
    }

    /**
     * Determines the campus type based on coordinates or defaults to ALTSTADT.
     * This is a simplified implementation - in a real scenario, you might want to
     * use coordinate ranges or geocoding to determine the campus more accurately.
     *
     * @param osmNode the OSM node with coordinates
     * @return the determined CampusType (defaults to ALTSTADT)
     */
    private @NonNull CampusType determineCampusType(@NonNull OsmNode osmNode) {
        // For now, default to ALTSTADT
        // In a real implementation, you could use coordinates to determine the campus
        // For example, check if lat/lon fall within specific ranges for each campus
        if (osmNode.lat() != null && osmNode.lon() != null) {
            // Could implement coordinate-based logic here
            log.debug("Using coordinates to determine campus: lat={}, lon={}", 
                    osmNode.lat(), osmNode.lon());
        }
        return CampusType.ALTSTADT;
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
