package it.eng.idsa.dataapp.service.impl;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipInputStream;

import javax.net.ssl.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import it.eng.idsa.dataapp.domain.CriminalProfileRequest;
import it.eng.idsa.dataapp.service.CriminalProfileService;
import it.eng.idsa.dataapp.util.TensorBE;
import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

@Service
public class CriminalProfileServiceImpl implements CriminalProfileService {
  private static final Logger logger = LoggerFactory.getLogger(CriminalProfileService.class);
 
  private String encryptorAPI;
  private String dataSharingPlatformAPI;
  private final TensorBE tensorBE;
  private final TENSORConnectorRegistry registry;

  public CriminalProfileServiceImpl(@Value("${application.encryptorAPI}") String encryptorAPI,  @Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, TensorBE tensorBE, TENSORConnectorRegistry registry) {
    this.encryptorAPI = encryptorAPI;
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.tensorBE = tensorBE;
    this.registry = registry;
  }

  @Override
  public CriminalProfileRequest parseIncomingCriminalProfileRequest(String suspectProfileID, String providerID) {
    try {
      return new CriminalProfileRequest(suspectProfileID, providerID);
    } catch (Exception e) {
      logger.error("Error parsing incoming body for Criminal Profile request", e);
    }
    return new CriminalProfileRequest();
  }

  @Override
  public ResponseEntity<JSONObject> createCriminalProfile(CriminalProfileRequest criminalProfileRequest,
      HttpHeaders httpHeaders, String solidToken) throws URISyntaxException, IOException {
    try {
      JSONObject response = getSuspectProfile(criminalProfileRequest.getSuspectProfileID(),
          criminalProfileRequest.getProviderID(), solidToken);
      return new ResponseEntity<>(response, HttpStatus.OK);

    } catch (Exception e) {
      logger.error("Following error occurred: {}", e.getMessage(), e);
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("message", "Message could not be processed");
      return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private byte[] downloadFile(String providerPod, String suspectProfileID, String solidToken) {
    byte[] downloadedFile = null;
    try {
        String suspectProfileUrl = dataSharingPlatformAPI + "/api/resources/" + providerPod + "%2Fsuspects%2F"
                + suspectProfileID + ".zip.enc?toJSONld=true";
        System.out.println("suspectProfileUrl: " + suspectProfileUrl);

        URL url = new URL(suspectProfileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cookie", solidToken);

        int responseCode = connection.getResponseCode();
        System.out.println("responseCode: " + responseCode);

        if (responseCode != 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println(line);
                }
            }
            return null;
        }

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            downloadedFile = buffer.toByteArray();
        }

        logger.info("Downloaded encrypted file size: {}", downloadedFile != null ? downloadedFile.length : 0);

    } catch (Exception e) {
        e.printStackTrace();
    }
    return downloadedFile;
  }

  private JSONObject extractZipContents(byte[] zipFile) {
    String encodedResource;
    JSONParser parser = new JSONParser();
    JSONObject suspectInfo = new JSONObject();
    JSONArray faceImagesArray = new JSONArray();
    JSONArray voiceFilesArray = new JSONArray();
    JSONArray fingerprintFilesArray = new JSONArray();

    try {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(zipFile);
        ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream);
        ZipEntry entry;
        int fileCount = 0;

        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                logger.info("Skipping directory: {}", entry.getName());
                continue;
            }

            String fileName = entry.getName().replace("\\", "/").toLowerCase();
            logger.info("Processing file: {}", fileName);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = zipInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            int size = outputStream.size();
            if (size == 0) {
                logger.warn("File '{}' has zero bytes. Skipping.", fileName);
                continue;
            }

            logger.info("File '{}' size: {} bytes", fileName, size);

            byte[] fileBytes = outputStream.toByteArray();
            String mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(fileBytes));

            if (mimeType == null) {
                if (fileName.endsWith(".png")) mimeType = "image/png";
                else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) mimeType = "image/jpeg";
                else if (fileName.endsWith(".flac")) mimeType = "audio/flac";
                else if (fileName.endsWith(".json")) mimeType = "application/json";
                else mimeType = "application/octet-stream";
                logger.info("Guessed fallback MIME type for '{}': {}", fileName, mimeType);
            } else {
                logger.info("Detected MIME type for '{}': {}", fileName, mimeType);
            }

            if (fileName.equals("info.json")) {
                String jsonContent = outputStream.toString("UTF-8");
                suspectInfo.put("info", (JSONObject) parser.parse(jsonContent));
                logger.info("Parsed info.json successfully.");
            } else {
                encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);

                if (fileName.startsWith("face/")) {
                    faceImagesArray.add(encodedResource);
                    logger.info("Added file '{}' to faceImagesArray", fileName);
                } else if (fileName.startsWith("voice/")) {
                    voiceFilesArray.add(encodedResource);
                    logger.info("Added file '{}' to voiceFilesArray", fileName);
                } else if (fileName.startsWith("finger/")) {
                    fingerprintFilesArray.add(encodedResource);
                    logger.info("Added file '{}' to fingerprintFilesArray", fileName);
                } else {
                    logger.warn("File '{}' does not match any known category. Ignoring.", fileName);
                }
            }

            zipInputStream.closeEntry();
            fileCount++;
        }

        logger.info("Total files processed from ZIP: {}", fileCount);

        suspectInfo.put("face", faceImagesArray);
        suspectInfo.put("voice", voiceFilesArray);
        suspectInfo.put("fingerprint", fingerprintFilesArray);

    } catch (Exception e) {
        logger.error("Error extracting ZIP contents", e);
    }

    return suspectInfo;
  }


  private JSONObject getSuspectProfile(String suspectProfileID, String providerID, String solidToken) {
    String providerPod = registry.getPodById(providerID);

    byte[] encryptedFile = downloadFile(providerPod, suspectProfileID, solidToken);
    logger.info("Encrypted file size: {}", encryptedFile != null ? encryptedFile.length : 0);

    byte[] decryptedFile = tensorBE.queryEncryptor("decrypt", "face", encryptedFile, suspectProfileID + ".zip.enc", providerID);
    String possibleErrorMessage = new String(decryptedFile, StandardCharsets.UTF_8);
    // DEBUG: Save decrypted file to disk for manual inspection
    try (FileOutputStream fos = new FileOutputStream("/tmp/decrypted_" + suspectProfileID + ".zip")) {
        fos.write(decryptedFile);
        logger.info("Decrypted ZIP written to disk at /tmp/decrypted_{}.zip", suspectProfileID);
        logger.info("Decrypted file size: {}", decryptedFile.length);

    } catch (IOException e) {
        logger.error("Failed to write decrypted ZIP to disk", e);
    }
    JSONObject suspectInfo = extractZipContents(decryptedFile);

    return suspectInfo;
  }

}
