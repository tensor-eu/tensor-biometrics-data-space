package it.eng.idsa.dataapp.service.impl;

import java.net.URISyntaxException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

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
import org.springframework.beans.factory.annotation.Value;

import it.eng.idsa.dataapp.domain.DisplayEvidenceRequest;
import it.eng.idsa.dataapp.service.DisplayEvidenceService;
import it.eng.idsa.dataapp.util.TensorBE;
import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

@Service
public class DisplayEvidenceServiceImpl implements DisplayEvidenceService {
  private static final Logger logger = LoggerFactory.getLogger(DisplayEvidenceService.class);

  private String encryptorAPI;
  private String dataSharingPlatformAPI;
  private final TensorBE tensorBE;
  private final TENSORConnectorRegistry registry;

  public DisplayEvidenceServiceImpl(@Value("${application.encryptorAPI}") String encryptorAPI, @Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, TensorBE tensorBE, TENSORConnectorRegistry registry) {
    this.encryptorAPI = encryptorAPI;
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.tensorBE = tensorBE;
    this.registry = registry;
  }

  @Override
  public DisplayEvidenceRequest parseIncomingDisplayEvidenceRequest(String suspectProfileID, String requestorID,
      String requestID) {
    try {
      return new DisplayEvidenceRequest(suspectProfileID, requestorID, requestID);
    } catch (Exception e) {
      logger.error("Error parsing incoming body for Display Evidence request", e);

    }
    return new DisplayEvidenceRequest();
  }

  @Override
  public ResponseEntity<JSONObject> createDisplayEvidence(DisplayEvidenceRequest displayEvidenceRequest,
      HttpHeaders httpHeaders, String solidToken) throws URISyntaxException, IOException {
    try {
      JSONObject response = getSuspectEvidence(displayEvidenceRequest.getSuspectProfileID(),
          displayEvidenceRequest.getRequestorID(), solidToken);
      return new ResponseEntity<>(response, HttpStatus.OK);

    } catch (Exception e) {
      logger.error("Following error occurred: {}", e.getMessage(), e);
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("message", "Message could not be processed");
      return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private String getLatestEvidence(JSONObject suspectEvidence) {
    String latestZip = null;
    try {
      JSONArray graphArray = (JSONArray) suspectEvidence.get("@graph");
      String latestDateStr = null;
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

      for (Object obj : graphArray) {
        JSONObject item = (JSONObject) obj;
        String id = (String) item.get("@id");

        if (id != null && id.endsWith(".zip.enc")) {
          JSONObject modifiedObj = (JSONObject) item.get("dc:modified");
          String modifiedDate = (modifiedObj != null) ? (String) modifiedObj.get("@value") : null;

          if (modifiedDate != null) {
            Date currentDate = dateFormat.parse(modifiedDate);
            Date latestDate = (latestDateStr != null) ? dateFormat.parse(latestDateStr) : null;

            if (latestDate == null || currentDate.after(latestDate)) {
              latestZip = id;
              latestDateStr = modifiedDate;
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    //System.out.println("latestZip " + latestZip);
    return latestZip;
  }

  private byte[] downloadFile(String requestorID, String suspectProfileID, String fileName, String solidToken) {
    byte[] downloadedFile = null;
    String pod = registry.getPodById(requestorID);

    try {
      String suspectProfileUrl = dataSharingPlatformAPI + "/api/resources/" + pod + "%2Fdsp_requests%2F"
          + suspectProfileID + "%2F" + fileName + "?toJSONld=true";
      System.out.println("downloadFile " + suspectProfileUrl);
      URL url = new URL(suspectProfileUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Cookie", solidToken);

      int responseCode = connection.getResponseCode();
      System.out.println("downloadFile code " + responseCode);
      // Handle HTTP errors explicitly
      if (responseCode != 200) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            System.err.println(line); // Print server error details
          }
        }
        return null;
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[8192];
      int bytesRead;
      while ((bytesRead = connection.getInputStream().read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, bytesRead);
      }
      downloadedFile = buffer.toByteArray();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return downloadedFile;
  }

  private JSONObject extractZipContents(byte[] zipFile) {
    System.out.println("IN extractZipContents");
    if (!isZipFile(zipFile)) {
      System.err.println("Provided byte array is not a valid ZIP file.");
      return new JSONObject();  // Return an empty JSON object
    }
    String encodedResource = null;
    JSONParser parser = new JSONParser();
    JSONObject suspectInfo = new JSONObject();

    try {
      ByteArrayInputStream ByteArrayInputStream = new ByteArrayInputStream(zipFile);
      ZipInputStream zipInputStream = new ZipInputStream(ByteArrayInputStream);
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        String fileName = entry.getName();
        System.out.println("extractZipContents fileName "+ fileName);
        // Handle 'info.json' file (outside 'face/' folder)
        if (fileName.equals("info/caseInfo.json")) {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int len;
          while ((len = zipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
          }
          String jsonContent = outputStream.toString("UTF-8");
          JSONObject caseDescription = (JSONObject) parser.parse(jsonContent);
          suspectInfo.put("descriptiveText", caseDescription.get("caseDescriptiveText"));
        } else {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int len;
          while ((len = zipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
          }

          if (outputStream.size() == 0) {
            System.out.println("Stream is empty!");
            continue;
          }
          String mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(outputStream.toByteArray()));

          if (fileName.startsWith("face/")) {
            if (mimeType == null) {
              mimeType = "image/png";
            }
            encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            suspectInfo.put("face", encodedResource);
          } else if (fileName.startsWith("voice/")) {
            if (mimeType == null) {
              mimeType = "audio/flac";
            }
            encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            suspectInfo.put("voice", encodedResource);
          } else if (fileName.startsWith("finger/")) {
            if (mimeType == null) {
              mimeType = "image/jpeg";
            }
            encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            suspectInfo.put("fingerprint", encodedResource);
          }
        }

        zipInputStream.closeEntry();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return suspectInfo;
  }

  private boolean isZipFile(byte[] fileBytes) {
    if (fileBytes == null || fileBytes.length < 4) {
      System.err.println("Decrypted byte array is null or too small to be a ZIP file.");
      return false;
    }

    // Print first four bytes as hex values
    //System.out.printf("Decrypted File Magic Number: %02X %02X %02X %02X%n", fileBytes[0], fileBytes[1], fileBytes[2], fileBytes[3]);

    return (fileBytes[0] == 0x50 && fileBytes[1] == 0x4B && fileBytes[2] == 0x03 && fileBytes[3] == 0x04);
  }

  private JSONObject getEvidenceFiles(String suspectProfileID, String requestorID, String solidToken) {
    JSONObject evidenceFilePaths = null;
    String requestorPod = registry.getPodById(requestorID);

    try {
      String suspectEvidenceUrl = dataSharingPlatformAPI + "/api/resources/" + requestorPod + "%2Fdsp_requests%2F"
          + suspectProfileID + "%2F?toJSONld=true";

      System.out.println("get evidence files " + suspectEvidenceUrl);
      StringBuilder response = new StringBuilder();
      URL url = new URL(suspectEvidenceUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Cookie", solidToken);

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }
      }

      connection.disconnect();

      JSONParser parser = new JSONParser();
      evidenceFilePaths = (JSONObject) parser.parse(response.toString());

      //System.out.println("File paths " + response.toString());

    } catch (Exception e) {
      e.printStackTrace();
    }
    return evidenceFilePaths;
  }

  private JSONObject getSuspectEvidence(String suspectProfileID, String requestorID, String solidToken) {
    JSONParser parser = new JSONParser();
    JSONObject suspectInfo = new JSONObject();

    JSONObject suspectEvidence = getEvidenceFiles(suspectProfileID, requestorID, solidToken);
    String latestEvidence = getLatestEvidence(suspectEvidence);
    if (latestEvidence != null) {
      byte[] encryptedFile = downloadFile(requestorID, suspectProfileID, latestEvidence, solidToken);
      byte[] decryptedFile = tensorBE.queryEncryptor("decrypt", "face", encryptedFile, latestEvidence, requestorID);
      suspectInfo = extractZipContents(decryptedFile);
    }
    return suspectInfo;
  }
}
