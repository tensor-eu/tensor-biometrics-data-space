package it.eng.idsa.dataapp.service.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import it.eng.idsa.dataapp.domain.DspResponseRequest;
import it.eng.idsa.dataapp.service.DspResponseService;
import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

@Service
public class DspResponseServiceImpl implements DspResponseService {
  private static final Logger logger = LoggerFactory.getLogger(DspResponseService.class);
  
  private static String dataSharingPlatformAPI;

  private static final String SOLID_POD = "solidPod";
  private static final String PROVIDER_ID = "providerId";
  private static final String CONSUMER_ID = "consumerId";
  private static final String REQUEST_ID = "requestId";
  private static final String RECIPIENT_ADDRESS = "recipientAddress";
  private static final String SUSPECT_PROFILE_ID = "suspectProfileId";
  private static final String DURATION = "duration";
  private static final String ACCESS_TYPE = "accessType";
  private static final String RESPONSE_TYPE = "responseType";
  private static final String ENCRYPTION_KEY = "encryptionKey";
  private static final String TOS = "tos";

  private final TENSORConnectorRegistry registry;

  public DspResponseServiceImpl( @Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, TENSORConnectorRegistry registry) {
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.registry = registry;
  }

  @Override
  public DspResponseRequest parseIncomingDspResponseRequest(String body) {
    JSONParser parser = new JSONParser();
    JSONObject jsonObject;
    try {
      jsonObject = (JSONObject) parser.parse(body);

      String providerId = (String) jsonObject.get(PROVIDER_ID);
      String consumerId = (String) jsonObject.get(CONSUMER_ID);
      String solidPod = (String) jsonObject.get(SOLID_POD);
      long requestId = (long) jsonObject.get(REQUEST_ID);
      String recipientAddress = (String) jsonObject.get(RECIPIENT_ADDRESS);
      String suspectProfileId = (String) jsonObject.get(SUSPECT_PROFILE_ID);
      String duration = (String) jsonObject.get(DURATION);
      String accessType = (String) jsonObject.get(ACCESS_TYPE);
      String responseType = (String) jsonObject.get(RESPONSE_TYPE);
      String encryptionKey = (String) jsonObject.get(ENCRYPTION_KEY);
      String tos = (String) jsonObject.get(TOS);

      return new DspResponseRequest(providerId, consumerId, solidPod, requestId, suspectProfileId, recipientAddress, suspectProfileId,
          duration, accessType,
          responseType,
          encryptionKey, tos);
    } catch (ParseException e) {
      logger.error("Error parsing incoming body for DSP response", e);
    }
    return new DspResponseRequest();
  }

  @Override
  public ResponseEntity<JSONObject> createDSPAccessResponse(DspResponseRequest dspResponseRequest,
      HttpHeaders httpHeaders,
      String solidToken)
      throws URISyntaxException, IOException {
    try {
      JSONObject response = createAccessResponse(solidToken, dspResponseRequest.getProviderId(), dspResponseRequest.getConsumerId(), dspResponseRequest.getSolidPod(),
          dspResponseRequest.getRequestId(),
          dspResponseRequest.getSuspectProfileId(),
          dspResponseRequest.getRecipientAddress(),
          dspResponseRequest.getDuration(), dspResponseRequest.getAccessType(),
          dspResponseRequest.getResponseType(), dspResponseRequest.getEncryptionKey(), dspResponseRequest.getTos());

      return new ResponseEntity<>(response, HttpStatus.OK);
    }

    catch (Exception e) {
      logger.error("Following error occurred: {}", e.getMessage(), e);
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("message", "Message could not be processed");
      return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private static String retrieveIndexUrl(String solidPod, String solidToken, String suspectProfileId) {
    String indexUrl = "";

    try {
      // Log the info message
      logger.info("Retrieving index url");

      // Set provider URL
      String resourceIndexUrl = dataSharingPlatformAPI + "/api/resources/" + solidPod + "/" + suspectProfileId
          + "/url";
      System.out.println("resourceIndexUrl " + resourceIndexUrl);
      URL url1 = new URL(resourceIndexUrl);

      // Open connection
      HttpURLConnection connection = (HttpURLConnection) url1.openConnection();

      // Set request method to GET
      connection.setRequestMethod("GET");

      // Set request property (headers)
      connection.setRequestProperty("Cookie", solidToken);
      // Get the response code
      int responseCode = connection.getResponseCode();

      if (responseCode == HttpURLConnection.HTTP_OK) { // If response is successful
        // Read the response into a string
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
          response.append(inputLine);
        }
        in.close();

        // Print or log the response as a string
        String responseString = response.toString();

        // If you need to do further processing with the response
        indexUrl = responseString;
      } else {
        logger.error("GET request failed. Response Code: " + responseCode);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return indexUrl; // Return the Ethereum address (or empty string if not found)
  }

  private static void updateCMScase(String cmsAPI, JSONObject cmsObject) {
    try {
      String cmsUrl = cmsAPI + "/cases/receive-dsp-response";
      System.out.println("Update CMS Case for Response: "+ cmsUrl);
      URL url = new URL(cmsUrl);

      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "application/json"); // Set Content-Type header to application/json
      connection.setRequestProperty("Authorization", "InternalWS internal-d35aef36-be86-4c92-a088-996e36f6f12e");

      // Send the request body
      try (OutputStream os = connection.getOutputStream()) {
        byte[] input = cmsObject.toString().getBytes("utf-8");
        os.write(input, 0, input.length); // Write JSON data as bytes
      }

      int responseCode = connection.getResponseCode();
      System.out.println("RESPONSE IN UPDATE CMS " + responseCode);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private JSONObject getDSPRequest(long requestId, String solidToken) {
    JSONObject requestObj = new JSONObject();
    try {
      String requestUrl = dataSharingPlatformAPI + "/api/requests/" + requestId;
      URL url = new URL(requestUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Cookie", solidToken);

      try (InputStream inputStream = connection.getInputStream();
          InputStreamReader isr = new InputStreamReader(inputStream);
          BufferedReader bufferedReader = new BufferedReader(isr)) {

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          response.append(line);
        }

        JSONParser parser = new JSONParser();
        requestObj = (JSONObject) parser.parse(response.toString());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return requestObj;
  }

  private void allowAccessToFile(String solidToken, String suspectProfileId, String consumerPod, String providerPod, String consumerSolidAPI) {
    HttpURLConnection connection = null;
    try {      
      String accessUrl = dataSharingPlatformAPI + "/api/access/read/" + providerPod + "%2Fsuspects%2F" + suspectProfileId+  ".zip.enc/" + URLEncoder.encode(consumerSolidAPI, "UTF-8") + "%2F" + consumerPod+ "%2Fprofile%2Fcard%23me";
      System.out.println("alloAccessToFile accessUrl" + accessUrl);
      URL url = new URL(accessUrl);
      connection = (HttpURLConnection) url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
      connection.setRequestProperty("Cookie", solidToken);

      // Send the request body
      try (OutputStream os = connection.getOutputStream();
          OutputStreamWriter osw = new OutputStreamWriter(os);
          BufferedWriter wr = new BufferedWriter(osw)) {
        String body = "{\"read\":true}";
        wr.write(body);
        wr.flush(); // Ensure all data is sent
        System.out.println(body);
      }

      // Get and print response code
      int responseCode = connection.getResponseCode();
      System.out.println("alloAccessToFile Response Code: " + responseCode);

      connection.disconnect();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private JSONObject createAccessResponse(String solidToken, String providerId, String consumerId, String solidPod, long requestId,
      String suspectProfileId,
      String recipientAddress,
      String duration, String accessType, String responseType, String encryptionKey, String tos) {
    System.out.println("responseType " + responseType);
    System.out.println("tos " + tos);
    URL url;
    HttpURLConnection connection = null;
    JSONObject jsonObject = new JSONObject();
    JSONObject responseObj = new JSONObject();
    System.out.println("REQUEST ID " + requestId);
    try {
      String indexUrl = retrieveIndexUrl(solidPod, solidToken, suspectProfileId);

      String providerEthAddr = registry.getEthAddressById(providerId);
      String cmsAPI = registry.getCmsAPIById(consumerId);
      String consumerSolidAPI = registry.getSolidAPIById(consumerId);
      String consumerPod = registry.getPodById(consumerId);
      String providerPod = registry.getPodById(providerId);

      System.out.println("providerEthAddr " + providerEthAddr);
      System.out.println("cmsAPI "+ cmsAPI);
      System.out.println("consumerSolidAPI " + consumerSolidAPI);
      System.out.println("consumerPod: " + consumerPod);
      System.out.println("providerPod: " + providerPod);

      // Allow access to Consumer
      allowAccessToFile(solidToken, suspectProfileId, consumerPod, providerPod, consumerSolidAPI);

      // Send creation of Access response request
      logger.info("Creating access response in Data Sharing Platform for resource {}", suspectProfileId);

      url = new URL(dataSharingPlatformAPI + "/api/responses");

      connection = (HttpURLConnection) url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
      connection.setRequestProperty("Cookie", solidToken);

      try (OutputStream os = connection.getOutputStream();
          OutputStreamWriter osw = new OutputStreamWriter(os);
          BufferedWriter wr = new BufferedWriter(osw)) {
        String body = "{\"requestId\":" + requestId
            + ",\"recipientAddress\": \"" + providerEthAddr
            + "\", \"resUrl\": " + indexUrl
            + ", \"duration\": " + 600
            + ",\"accessType\": \"" + "read"
            + "\", \"responseType\":\"" + responseType
            + "\", \"tos\": \"" + tos
            + "\"}";
        System.out.println("body" + body);
        wr.write(body);
      }
      try (InputStream inputStream = connection.getInputStream();
          InputStreamReader isr = new InputStreamReader(inputStream);
          BufferedReader bufferedReader = new BufferedReader(isr)) {

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          response.append(line);
        }

        JSONParser parser = new JSONParser();
        responseObj = (JSONObject) parser.parse(response.toString());

        JSONObject requestObj = getDSPRequest(requestId, solidToken);

        jsonObject.put("request", requestObj);
        jsonObject.put("response", responseObj);

        System.out.println("JSON Object in response " + jsonObject.toJSONString());

        responseObj.put("requestId", requestId);
        responseObj.put("duration", 600);
        responseObj.put("responseType", responseType);
        responseObj.put("tos", tos);
        System.out.println("JSON Object in response " + responseObj.toJSONString());
        // Update Case Management System case to proceed
        updateCMScase(cmsAPI, jsonObject);
      }

      // Response
      int responseCode = connection.getResponseCode();
      System.out.println("Response Code: " + responseCode);

      connection.disconnect();

      return responseObj;

    } catch (ParseException | IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

  }

}
