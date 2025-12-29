package it.eng.idsa.dataapp.service;

import it.eng.idsa.dataapp.domain.TENSORConnector;

import java.util.HashMap;
import java.util.Map;

public class TENSORConnectorRegistry {

    private final Map<String, TENSORConnector> connectors = new HashMap<>();

    // Add a connector to the registry
    public void addConnector(TENSORConnector tensorConnector) {
        if (tensorConnector != null && tensorConnector.getId() != null) {
            connectors.put(tensorConnector.getId(), tensorConnector);
        }
    }

    // Get TENSORConnector object by id
    public TENSORConnector getConnectorById(String id) {
        return connectors.get(id);
    }

    public String getIdByPod(String pod) {
        return connectors.values().stream()
            .filter(c -> c.getPod().equals(pod))
            .map(TENSORConnector::getId)
            .findFirst()
            .orElse(null);
    } 

    // Get IP by connector id
    public String getIPbyId(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getIp() : null;
    }

    // Get pod by connector id
    public String getPodById(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getPod() : null;
    }

    // Get ethereum address by connector id
    public String getEthAddressById(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getEthAddress() : null;
    }

     // Get fuzzy extractor file by connector id
    public String getFuzzyExtractorFileById(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getFuzzyExtractorFile() : null;
    }

     // Get CMS API by connector id
    public String getCmsAPIById(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getCmsAPI() : null;
    }

    // Get SOLID API by connector id
    public String getSolidAPIById(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getSolidAPI() : null;
    }

    // Get DSP API by connector id
    public String getDspAPIById(String id) {
        TENSORConnector connector = connectors.get(id);
        return (connector != null) ? connector.getDspAPI() : null;
    }

    // Optionally, get all connectors
    public Map<String, TENSORConnector> getAllConnectors() {
        return connectors;
    }
}