package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {

    private static final String OSM_API_BASE_URL =
            "https://api.openstreetmap.org/api/0.6/node/";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId)
            throws OsmNodeNotFoundException {

        String url = OSM_API_BASE_URL + nodeId;

        log.info("Fetching OSM node {} from {}", nodeId, url);

        try {
            // Add user agent 
            HttpHeaders headers = new HttpHeaders();
            headers.add("User-Agent", "CampusCoffee/1.0 (luka@student)");
            headers.add("Accept", "application/xml, text/xml, */*");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // Check status code first
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("OSM API returned non-2xx status for node {}: {}", nodeId, response.getStatusCode());
                throw new OsmNodeNotFoundException(nodeId);
            }

            String body = response.getBody();

            // ensure it's XML, not HTML
            if (body == null) {
                log.error("OSM API returned null body for node {}", nodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }

            String trimmedBody = body.trim();
            if (!trimmedBody.startsWith("<?xml") && !trimmedBody.startsWith("<osm")) {
                log.error("Invalid OSM response: Not XML. Status: {}, Content-Type: {}, Body (first 500 chars): {}",
                        response.getStatusCode(),
                        response.getHeaders().getFirst("Content-Type"),
                        body.length() > 500 ? body.substring(0, 500) : body);
                throw new OsmNodeNotFoundException(nodeId);
            }

            return parseOsmXml(body, nodeId);

        } catch (HttpClientErrorException e) {
            log.error("HTTP error fetching OSM node {}: Status {}, Body: {}",
                    nodeId, e.getStatusCode(),
                    e.getResponseBodyAsString() != null && e.getResponseBodyAsString().length() > 500
                            ? e.getResponseBodyAsString().substring(0, 500)
                            : e.getResponseBodyAsString());
            throw new OsmNodeNotFoundException(nodeId);
        } catch (RestClientException e) {
            log.error("Rest client error fetching OSM node {}: {}", nodeId, e.getMessage());
            throw new OsmNodeNotFoundException(nodeId);
        } catch (Exception e) {
            log.error("Unexpected error fetching OSM node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    private @NonNull OsmNode parseOsmXml(String xml, Long nodeId)
            throws OsmNodeNotFoundException {

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream inputStream =
                    new ByteArrayInputStream(xml.getBytes("UTF-8"));

            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            NodeList nodeList = document.getElementsByTagName("node");

            if (nodeList.getLength() == 0)
                throw new OsmNodeNotFoundException(nodeId);

            Element nodeElement = (Element) nodeList.item(0);

            Double lat = Double.parseDouble(nodeElement.getAttribute("lat"));
            Double lon = Double.parseDouble(nodeElement.getAttribute("lon"));

            // extract tags
            Map<String, String> tags = new HashMap<>();
            NodeList tagList = nodeElement.getElementsByTagName("tag");

            for (int i = 0; i < tagList.getLength(); i++) {
                Element tag = (Element) tagList.item(i);
                tags.put(tag.getAttribute("k"), tag.getAttribute("v"));
            }

            return OsmNode.builder()
                    .nodeId(nodeId)
                    .lat(lat)
                    .lon(lon)
                    .tags(tags)
                    .build();

        } catch (Exception e) {
            throw new OsmNodeNotFoundException(nodeId);
        }
    }
}
