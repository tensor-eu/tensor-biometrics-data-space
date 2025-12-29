package it.eng.idsa.dataapp.service.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;

import java.net.URISyntaxException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;

import it.eng.idsa.dataapp.domain.DspRequestRequest;
import it.eng.idsa.dataapp.service.DspRequestService;
import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

import it.eng.idsa.dataapp.util.TensorBE;

@Service
public class DspRequestServiceImpl implements DspRequestService {
  private static final Logger logger = LoggerFactory.getLogger(DspRequestService.class);
  
  private String encryptorAPI;
  private String dataSharingPlatformAPI;
  private final TensorBE tensorBE;
  private final TENSORConnectorRegistry registry;

  public DspRequestServiceImpl(@Value("${application.encryptorAPI}") String encryptorAPI,  @Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, TensorBE tensorBE, TENSORConnectorRegistry registry) {
    this.encryptorAPI = encryptorAPI;
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.tensorBE = tensorBE;
    this.registry = registry;
  }

  @Override
  public DspRequestRequest parseIncomingDspRequestRequest(String providerId, String consumerId,
      String suspectProfileId,
      String facialImageSimilarityScore,
      String fingerprintSimilarityScore,
      String voiceprintSimilarityScore,
      String descriptiveText,
      MultipartFile faceFile,
      MultipartFile voiceFile,
      MultipartFile fingerprintFile) {
    try {
      return new DspRequestRequest(providerId, consumerId, suspectProfileId, facialImageSimilarityScore,
          fingerprintSimilarityScore, voiceprintSimilarityScore, descriptiveText, faceFile, voiceFile, fingerprintFile);
    } catch (Exception e) {
      logger.error("Error parsing incoming body for DSP request", e);
    }
    return new DspRequestRequest();
  }

  @Override
  public ResponseEntity<JSONObject> createDSPAccessRequest(DspRequestRequest dspRequestRequest, HttpHeaders httpHeaders,
      String solidToken)
      throws URISyntaxException, IOException {
    try {
      JSONObject response = createAccessRequest(solidToken,
          dspRequestRequest.getProviderId(),
          dspRequestRequest.getConsumerId(),
          dspRequestRequest.getSuspectProfileId(), dspRequestRequest.getFacialImageSimilarityScore(),
          dspRequestRequest.getFingerprintSimilarityScore(), dspRequestRequest.getVoiceprintSimilarityScore(),
          dspRequestRequest.getDescriptiveText(),
          dspRequestRequest.getFaceFile(),
          dspRequestRequest.getVoiceFile(),
          dspRequestRequest.getFingerprintFile());
      if (response == null) {
        throw new Exception("Response is null");
      }
      return new ResponseEntity<>(response, HttpStatus.OK);
    }

    catch (Exception e) {
      logger.error("Following error occurred: {}", e.getMessage(), e);
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("message", "Message could not be processed");
      return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  public String checkAndCreateRSAKey(String fileName) throws Exception {
    // First, check if the file exists in the classpath (resources)
    ClassPathResource resource = new ClassPathResource(fileName);
    if (!resource.exists()) {
      // Generate RSA key pair
      KeyPair keyPair = generateRSAKeyPair();
      PublicKey publicKey = keyPair.getPublic();
      PrivateKey privateKey = keyPair.getPrivate();

      // Define a writable path (in Docker, you could use /tmp or an external volume)
      String writablePath = "/tmp/" + fileName;
      Path path = Paths.get(writablePath);

      // Save the public key to file
      saveKey(writablePath, publicKey);

      return new String(Files.readAllBytes(path));
    }

    // If the file exists in resources, load and return it
    Path path = Paths.get(resource.getURI());
    return new String(Files.readAllBytes(path));
  }

  private KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    return keyGen.generateKeyPair();
  }

  private void saveKey(String filePath, PublicKey key) throws Exception {
    // Convert public key to PEM format
    String publicKeyPEM = "-----BEGIN PUBLIC KEY-----\n" +
        Base64.getEncoder().encodeToString(key.getEncoded()) +
        "\n-----END PUBLIC KEY-----";
    // Write to file
    File file = new File(filePath);
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(publicKeyPEM.getBytes());
    }
  }

  private boolean hasExtension(String fileName) {
    return fileName != null && fileName.contains(".") && fileName.lastIndexOf('.') < fileName.length() - 1;
  }

  private String getEncryptedKey(String filename) {
    String encryptionKey = null;
    try {
      String url = encryptorAPI + "/get-encrypted-key/" + filename;
      String rsaPublicKey = checkAndCreateRSAKey("id_rsa_connector.pem");
      String encodedPublicKey = URLEncoder.encode(rsaPublicKey, "UTF-8");
      encodedPublicKey = encodedPublicKey.replace("+", "%20");
      encodedPublicKey = encodedPublicKey.replace("%0A", "%20");

      URL obj = new URL(url + "?rsa_public_key=" + encodedPublicKey);

      // Open connection
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      con.setRequestMethod("POST"); // Set request method to POST

      // Set request headers
      con.setRequestProperty("Accept", "application/json");

      // Send an empty body (to match curl -d '')
      con.setDoOutput(true);
      OutputStream os = con.getOutputStream();
      os.write(new byte[0]); // Sending an empty body
      os.flush();
      os.close();
      // Since you're sending an empty body, you can skip the output stream code

      // Get the response code and handle the response
      int responseCode = con.getResponseCode();
      System.out.println("encrypted key response code " + responseCode);
      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuilder response = new StringBuilder();

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      // Convert the response to a JSONObject
      JSONParser parser = new JSONParser();
      JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());

      // Extract the "encrypted_key" value
      encryptionKey = (String) jsonResponse.get("encrypted_key");

      byte[] decodedBytes = Base64.getDecoder().decode(encryptionKey);

      StringBuilder hexString = new StringBuilder();
      for (byte b : decodedBytes) {
        // Convert each byte to hexadecimal and append to the string
        String hex = String.format("%02x", b);
        hexString.append(hex);
      }

      encryptionKey = "0x" + hexString.toString();
      con.disconnect(); // Clean up the connection
    } catch (Exception e) {
      e.printStackTrace();
    }
    return encryptionKey;
  }

  private void allowAccessToFile(String solidToken, String consumerPod, String providerPod, String suspectProfileId,
      String fileName, String solidPodAPI) {
    HttpURLConnection connection = null;
    try {
      
      String accessUrl = dataSharingPlatformAPI + "/api/access/read/" + consumerPod + "%2Fdsp_requests%2F"
          + suspectProfileId
          + "%2F" + solidPodAPI + "%2F" + providerPod + "%2Fprofile%2Fcard%23me";
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
      connection.disconnect();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void uploadFileToPod(String solidPod, String suspectProfileId, byte[] resourceFile,
      String fileName, String solidToken) {
    try {
      File file = new File(fileName);
      try (OutputStream os = new FileOutputStream(file)) {
        os.write(resourceFile);
      }

      // Construct the upload URL
      String uploadUrl = dataSharingPlatformAPI + "/api/resources/" + solidPod + "%2Fdsp_requests%2F"
          + suspectProfileId +
          "%2F";

      System.out.println("uploadURL " + uploadUrl);
      System.out.println("fileName: "+ fileName);
      HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Cookie", solidToken);

      String boundary = "---------------------------" + System.currentTimeMillis();
      String charset = "UTF-8";
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

      try (
          OutputStream output = connection.getOutputStream();
          PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
          FileInputStream fileInputStream = new FileInputStream(file);
          BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
        // Text Field
        writer.append("--" + boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"index\"").append("\r\n");
        writer.append("Content-Type: text/plain; charset=" + charset).append("\r\n\r\n");
        writer.append(fileName).append("\r\n").flush();

        // File Field
        writer.append("--" + boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"")
            .append("\r\n");
        writer.append("Content-Type: " + HttpURLConnection.guessContentTypeFromName(file.getName())).append("\r\n\r\n")
            .flush();

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
          output.write(buffer, 0, bytesRead);
        }
        output.flush();

        // End of multipart/form-data
        writer.append("\r\n--" + boundary + "--").append("\r\n").flush();
      }

      // Response
      int responseCode = connection.getResponseCode();
      System.out.println("RESPONSE CODE UPLOAD " + responseCode);

      connection.disconnect();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private byte[] createCaseFile(String caseDescriptiveText) {
    System.out.println("In create case file");
    byte[] readBytes = null;
    String jsonContent = "{ \"caseDescriptiveText\": \"" + caseDescriptiveText + "\" }";
    byte[] byteArray = jsonContent.getBytes(); // Convert JSON text to byte array

    // Define .json file
    File file = new File("caseInfo.json");

    // Write byte array to file
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(byteArray);
      System.out.println("JSON file created: " + file.getAbsolutePath());
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Optional: Read back the file into a byte array
    try {
      readBytes = Files.readAllBytes(file.toPath());
      System.out.println("Read from file: " + new String(readBytes));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return readBytes;
  }

  public static byte[] convertMultipartFileToBytes(MultipartFile multipartFile) throws IOException {
    System.out.println("Converted multipart file to bytes ");
    return multipartFile != null && !multipartFile.isEmpty() ? multipartFile.getBytes() : null;
  }

  private static void addBytesToZip(byte[] fileData, String fileName, ZipOutputStream zipOut) throws IOException {
    System.out.println("adding bytes to zip");
    if (fileData != null && fileName != null) {
      ZipEntry zipEntry = new ZipEntry(fileName);
      zipOut.putNextEntry(zipEntry);
      zipOut.write(fileData);
      zipOut.closeEntry();
    }
  }

  private byte[] prepareCapturedData(String descriptiveText, MultipartFile faceFile,
      MultipartFile voiceFile,
      MultipartFile fingerprintFile, String encryptedZipFileName, String consumerId) {
    byte[] encryptedZipFile = null;
    try {
      // Case info file
      byte[] caseInfoFileBytes = createCaseFile(descriptiveText);
      System.out.println("After caseInfoFileBtyes");
      // Face file
      byte[] faceFileBytes = convertMultipartFileToBytes(faceFile);
      System.out.println("After faceFileBytes");
      // Voice file
      byte[] voiceFileBytes = convertMultipartFileToBytes(voiceFile);
      System.out.println("After voiceFileBytes");
      // Fingerprint file
      byte[] fingerprintFileBytes = convertMultipartFileToBytes(fingerprintFile);
      System.out.println("After fingerprintFileBytes");

      // Create a zip
      System.out.println("Creating zip");
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (ZipOutputStream zipOut = new ZipOutputStream(byteArrayOutputStream)) {

        // Add byte[] files to ZIP
        addBytesToZip(caseInfoFileBytes, "info/caseInfo.json", zipOut);
        if (faceFile != null) {
          addBytesToZip(faceFileBytes, "face/" + faceFile.getOriginalFilename(), zipOut);
        }
        if (voiceFile != null) {
          addBytesToZip(voiceFileBytes, "voice/" + voiceFile.getOriginalFilename(), zipOut);
        }
        if (fingerprintFile != null) {
          addBytesToZip(fingerprintFileBytes, "finger/" + fingerprintFile.getOriginalFilename(), zipOut);
        }
      }

      byte[] zipFile = byteArrayOutputStream.toByteArray();
      encryptedZipFile = tensorBE.queryEncryptor("encrypt", "face", zipFile, encryptedZipFileName, consumerId);
      return encryptedZipFile;

    } catch (Exception e) {
      e.printStackTrace();
    }
    return encryptedZipFile;
  }

  private JSONObject createAccessRequest(String solidToken, String providerId, String consumerId,
      String suspectProfileId,
      String facialImageSimilarityScore,
      String fingerprintSimilarityScore,
      String voiceprintSimilarityScore,
      String descriptiveText,
      MultipartFile faceFile,
      MultipartFile voiceFile,
      MultipartFile fingerprintFile) {
    URL url;
    HttpURLConnection connection = null;
    JSONObject jsonObject = null;
    
    String providerPod = registry.getPodById(providerId);
    String consumerPod = registry.getPodById(consumerId);
    String providerSolidAPI = registry.getSolidAPIById(providerId);
    String consumerSolidAPI = registry.getSolidAPIById(consumerId);

    try {
      logger.info("Creating access request in Data Sharing Platform for resource with index {}", suspectProfileId);

      // Send encrypted data
      String zipFileName = UUID.randomUUID().toString() + ".zip";
      //String zipFileName = suspectProfileId + ".zip";
      String encryptedZipFileName = zipFileName + ".enc";

      byte[] encryptedZipFile = prepareCapturedData(descriptiveText, faceFile, voiceFile, fingerprintFile,
          zipFileName, consumerId);
      if (encryptedZipFileName == null){
        throw new Exception("encryptedZipFile is null");
      }
      // Get AES encryption key encrypted with RSA
      String encryptionKey = getEncryptedKey(zipFileName);
      System.out
          .println("Successfully created encryption key with RSA Public Key of current device " + zipFileName);
      if (encryptionKey == null) {
        throw new Exception("Encryption key value is null");
      }
      String encodedEncryptionKey = URLEncoder.encode(encryptionKey, "UTF-8");
      encodedEncryptionKey = encodedEncryptionKey.replace("+", "%20");
      encodedEncryptionKey = encodedEncryptionKey.replace("%0A", "%20");

      url = new URL(dataSharingPlatformAPI + "/api/requests");

      connection = (HttpURLConnection) url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
      connection.setRequestProperty("Cookie", solidToken);

      try (OutputStream os = connection.getOutputStream();
          OutputStreamWriter osw = new OutputStreamWriter(os);
          BufferedWriter wr = new BufferedWriter(osw)) {

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("recipientWebId", providerSolidAPI + "/" + providerPod + "/profile/card#me");
        bodyObject.put("resIndex", suspectProfileId);
        bodyObject.put("duration", 600);
        bodyObject.put("accessType", "read");
        bodyObject.put("encryptionKey", encodedEncryptionKey);
        JSONArray scoresArray = new JSONArray();
        scoresArray.add(facialImageSimilarityScore);
        scoresArray.add(fingerprintSimilarityScore);
        scoresArray.add(voiceprintSimilarityScore);

        bodyObject.put("scores", scoresArray);

        String body = bodyObject.toJSONString();
        System.out.println("DSP REQUEST BODY " + body);
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
        jsonObject = (JSONObject) parser.parse(response.toString());
        jsonObject.put("recipientWebId", providerSolidAPI + "/" + providerPod + "/profile/card#me");
        jsonObject.put("resIndex", suspectProfileId);
        jsonObject.put("duration", 600);
        jsonObject.put("accessType", "read");
        jsonObject.put("encKey", encodedEncryptionKey);

        uploadFileToPod(consumerPod, suspectProfileId, encryptedZipFile,
            encryptedZipFileName, solidToken);

        // Allow access to files for Provider for 1 hour
        allowAccessToFile(solidToken, consumerPod, providerPod, suspectProfileId, encryptedZipFileName, providerSolidAPI);

      }

      connection.disconnect();

      return jsonObject;

    } catch (Exception e) {
      System.out.println("error" + e);
      throw new RuntimeException(e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

  }

}
