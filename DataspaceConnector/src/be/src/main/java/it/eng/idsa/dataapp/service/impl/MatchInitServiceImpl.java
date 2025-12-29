package it.eng.idsa.dataapp.service.impl;

import java.io.IOException;
import java.util.Map;
import java.net.URISyntaxException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import it.eng.idsa.dataapp.domain.MatchInitRequest;
import it.eng.idsa.dataapp.domain.TENSORConnector;
import it.eng.idsa.dataapp.service.MatchInitService;
import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

@Service
public class MatchInitServiceImpl implements MatchInitService {
  private static final Logger logger = LoggerFactory.getLogger(MatchInitService.class);

  private final TENSORConnectorRegistry registry;

  // Constructor injection of the shared bean
  public MatchInitServiceImpl(TENSORConnectorRegistry registry) {
    this.registry = registry;
  }


  @Override
  public MatchInitRequest parseIncomingMatchInitRequest(String faceHash, String fingerprintHash, String voiceHash) {
    try {
      return new MatchInitRequest(faceHash, fingerprintHash, voiceHash);
    } catch (Exception e) {
      logger.error("Error parsing incoming body for Match Init request", e);
    }
    return new MatchInitRequest();
  }

  @Override
  public ResponseEntity<JSONArray> createMatchInit(MatchInitRequest matchInitRequest,
      HttpHeaders httpHeaders)
      throws URISyntaxException, IOException {
    try {
      // Request all connectors that participate in the Data Space by querying the
      // Metadata Broker
      JSONArray response = getBrokerParticipants();
      return new ResponseEntity<>(response, HttpStatus.OK);

    } catch (Exception e) {
      logger.error("Following error occurred: {}", e.getMessage(), e);
      JSONArray jsonArray = new JSONArray();
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("message", "Message could not be processed");
      jsonArray.add(errorResponse);
      return new ResponseEntity<>(jsonArray, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  // TODO: Update this code to collect participants registered in the Metadata
  // Broker component
  private JSONArray getBrokerParticipants() {
    System.out.println("Initializing TENSORConnectorRegistry...");
    
    JSONArray jsonArray = new JSONArray();

    System.out.println("Fetching all connectors...");
    Map<String, TENSORConnector> connectors = registry.getAllConnectors();
    System.out.println("Total connectors found: " + connectors.size());

    for (Map.Entry<String, TENSORConnector> entry : connectors.entrySet()) {
      String key = entry.getKey();
      TENSORConnector connector = entry.getValue();
      System.out.println("Processing connector with key: " + key);

      JSONObject obj = new JSONObject();
      obj.put("ConnectorId", connector.getId());
      obj.put("ConnectorIP", connector.getIp());
      obj.put("Pod", connector.getPod());

      System.out.println("Created JSON object: " + obj.toJSONString());

      jsonArray.add(obj);
    }

    System.out.println("Final JSONArray result: " + jsonArray.toJSONString());
    return jsonArray;
  }


}
