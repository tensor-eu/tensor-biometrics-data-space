package it.eng.idsa.dataapp.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import it.eng.idsa.dataapp.domain.MatchLocalRequest;
import it.eng.idsa.dataapp.service.MatchLocalService;

@Service
public class MatchLocalServiceImpl implements MatchLocalService {
  private static final Logger logger = LoggerFactory.getLogger(MatchLocalService.class);
  
  private String indexerAPI;

  public MatchLocalServiceImpl(@Value("${application.indexerAPI}") String indexerAPI) {
    this.indexerAPI = indexerAPI;
  }

  @Override
  public MatchLocalRequest parseIncomingMatchLocalRequest(String providerId, String consumerId, String sampleFaceUrl,
      String sampleFingerprintUrl,
      String sampleVoiceUrl) {
    try {
      return new MatchLocalRequest(providerId, consumerId, sampleFaceUrl, sampleFingerprintUrl, sampleVoiceUrl);
    } catch (Exception e) {
      logger.error("Error parsing incoming body for Match Local request", e);
    }
    return new MatchLocalRequest();
  }

  private JSONObject getHashData(String providerId, String consumerId, String sampleFaceUrl,
      String sampleFingerprintUrl, String sampleVoiceUrl) {
    System.out.println("getHashData sampleFaceUrl " + sampleFaceUrl);
    JSONObject hashData = new JSONObject();
    try {
      String hashUrl = indexerAPI + "/calculateHashForSearching";
      System.out.println("hashUrl "+ hashUrl);
      JSONObject typeObject = new JSONObject();
      typeObject.put("image", !sampleFaceUrl.isEmpty());
      typeObject.put("fingerprint", !sampleFingerprintUrl.isEmpty());
      typeObject.put("voice", !sampleVoiceUrl.isEmpty());

      JSONObject jsonBody = new JSONObject();
      jsonBody.put("type", typeObject);
      jsonBody.put("full_facial_image_url", sampleFaceUrl);
      jsonBody.put("full_fingerprint_url", sampleFingerprintUrl);
      jsonBody.put("full_voice_url", sampleVoiceUrl);

      URL url = new URL(hashUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();

      // Set request method to POST
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);

      // Set headers
      connection.setRequestProperty("Content-Type", "application/json");

      // Write JSON data to the request body
      try (OutputStream os = connection.getOutputStream()) {
        byte[] input = jsonBody.toJSONString().getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }

      int responseCode = connection.getResponseCode();
      System.out.println("Hash Response Code: " + responseCode);

      // Read and parse the response
      BufferedReader in = new BufferedReader(
          new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
      StringBuilder response = new StringBuilder();
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      // Parse the response as JSON
      JSONParser parser = new JSONParser();
      hashData = (JSONObject) parser.parse(response.toString());
      hashData.put("from", consumerId.toUpperCase());
      hashData.put("to", providerId.toUpperCase());

    } catch (Exception e) {
      e.printStackTrace();
    }
    return hashData;
  }

  private JSONArray getMatchData(JSONObject hashData) {
    JSONArray matchData = new JSONArray();
    try {
      String comparatorUrl = indexerAPI + "/searchForMatches";
      URL url = new URL(comparatorUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();

      // Set request method to POST
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);

      // Set headers
      connection.setRequestProperty("Content-Type", "application/json");

      // Write JSON data to the request body
      try (OutputStream os = connection.getOutputStream()) {
        byte[] input = hashData.toJSONString().getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }

      int responseCode = connection.getResponseCode();
      System.out.println(" Search Response Code: " + responseCode);

      // Read and parse the response
      BufferedReader in = new BufferedReader(
          new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
      StringBuilder response = new StringBuilder();
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      // Parse the response as JSON
      JSONParser parser = new JSONParser();
      matchData = (JSONArray) parser.parse(response.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
    return matchData;
  }

  @Override
  public ResponseEntity<JSONArray> createMatchLocal(MatchLocalRequest matchLocalRequest,
      HttpHeaders httpHeaders)
      throws URISyntaxException, IOException {
    try {
      // Get connector's catalog of indexes
      // Call endpoint of Local Comparator to get similarity score between each
      // catalog's entry and input sample

      JSONObject hashData = getHashData(matchLocalRequest.getProviderId(), matchLocalRequest.getConsumerId(),
          matchLocalRequest.getSampleFaceUrl(), matchLocalRequest.getSampleFingerprintUrl(),
          matchLocalRequest.getSampleVoiceUrl());
      JSONArray matchData = getMatchData(hashData);

      return new ResponseEntity<>(matchData, HttpStatus.OK);
    }

    catch (Exception e) {
      logger.error("Following error occurred: {}", e.getMessage(), e);
      JSONArray jsonArray = new JSONArray();
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("message", "Message could not be processed");
      jsonArray.add(errorResponse);
      return new ResponseEntity<>(jsonArray, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

}
