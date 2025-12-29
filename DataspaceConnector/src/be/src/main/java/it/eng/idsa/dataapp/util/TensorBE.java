package it.eng.idsa.dataapp.util;

import java.io.*;

import java.util.*;

import java.net.URISyntaxException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

@Component
public class TensorBE {

  private static final Logger logger = LoggerFactory.getLogger(TensorBE.class);

  private String dataSharingPlatformAPI;
  private String encryptorAPI;
  private final TENSORConnectorRegistry registry;

  public TensorBE(@Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, @Value("${application.encryptorAPI}") String encryptorAPI, TENSORConnectorRegistry registry) {
    super();
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.encryptorAPI = encryptorAPI;
    this.registry = registry;
  }

  public boolean isValidImage(byte[] imageBytes) {
    try {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        return image != null;
    } catch (Exception e) {
        return false;
    }
  }

  //TODO: Update this with custom biometric data provided in the request.
  private Map<String, String> getFuzzyExtractorFile(String user) {
    Map<String, String> fuzzyExtractorFile = new HashMap<>();
    try {
        String filename = registry.getFuzzyExtractorFileById(user);
        System.out.println("Fuzzy extractor file: " + filename);
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);

        if (is == null) {
            throw new FileNotFoundException("Image file not found: " + filename);
        }

        byte[] fileBytes = is.readAllBytes();
        is.close();

        boolean valid = isValidImage(fileBytes);
        System.out.println("Is valid image: " + valid);

        fuzzyExtractorFile.put("filename", filename);
        fuzzyExtractorFile.put("content", Base64.getEncoder().encodeToString(fileBytes)); // If you want to keep content in Base64 for debug/logging
        fuzzyExtractorFile.put("binary", "true"); // Flag to indicate it's a binary image

    } catch (Exception e) {
        e.printStackTrace();
    }
    return fuzzyExtractorFile;
}


  private void addRequestFilePart(PrintWriter writer, OutputStream outputStream, String fieldName, String fileName,
      byte[] uploadFile, String boundary, String CRLF, String charset) throws IOException {
    System.out.println("fieldName: "+ fieldName);
    System.out.println("fileName: "+ fileName);
    System.out.println("boundary: "+ boundary);
    System.out.println("CRLF: "+ CRLF);
    System.out.println("charset: "+ charset);

    // Boundary and form-data headers
    writer.append("--").append(boundary).append(CRLF);
    writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
        .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);

    // Set content type or default to application/octet-stream
    String contentType = "application/octet-stream"; // Assuming unknown content type
    writer.append("Content-Type: ").append(contentType).append(CRLF);
    writer.append(CRLF).flush(); // End of headers

    // Write the byte array (file content) directly to the outputStream
    outputStream.write(uploadFile);
    outputStream.flush();

    // Finish part with CRLF
    writer.append(CRLF).flush(); // Blank line after the file content
  }

  public byte[] queryEncryptor(String queryType, String mode, byte[] file, String fileName, String user) {
    // Variable for binary file to be returned
    byte[] resultFile = null;
    try {
      // Information about multipart request 
      String boundary = "Boundary-" + System.currentTimeMillis();
      String CRLF = "\r\n"; 
      String charset = "UTF-8";

      // Encryptor variables based on queryType
      String endpoint = "";
      String feFileIndicator = "";
      String fileIndicator ="";
      
      if (queryType.equals("encrypt")) {
        endpoint = "/encrypt";
        feFileIndicator = "enroll_image";
        fileIndicator = "file_to_encrypt";
      } else if (queryType.equals("decrypt")) {
        endpoint = "/decrypt";
        feFileIndicator = "verify_image";
        fileIndicator = "encrypted_file";
      } else {
        throw new IOException("Query type not supported by Encryptor");
      }

      System.out.println("queryEncryptor args: " + fileName);

      // Prepare URL
      URL url = new URL(encryptorAPI + endpoint + "?mode=" + mode);
   
      // Create the connection
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

      // Open connection
      OutputStream outputStream = connection.getOutputStream();
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);

      // Add fuzzy extractor image in the request parts
      Map<String, String> fuzzyExtractorFile = getFuzzyExtractorFile(user);
      String fuzzyExtractorFileName = fuzzyExtractorFile.get("filename");

      // Use binary bytes (not base64-decoded from string)
      InputStream imageStream = getClass().getClassLoader().getResourceAsStream(fuzzyExtractorFileName);
      byte[] fuzzyExtractorFileContent = imageStream.readAllBytes();
      imageStream.close();

      addRequestFilePart(writer, outputStream, feFileIndicator, fuzzyExtractorFileName, fuzzyExtractorFileContent, boundary, CRLF, charset);

      // Add file to encrypt
      addRequestFilePart(writer, outputStream, fileIndicator, fileName, file, boundary, CRLF,
          charset);
    
      // End of multipart form data
      writer.append("--").append(boundary).append("--").append(CRLF).flush();

      // Handle the response
      int responseCode = connection.getResponseCode();
      System.out.println("Query Encryptor responseCode: " + responseCode);
      if (responseCode == HttpURLConnection.HTTP_OK) {
        // Read the response into a ByteArrayOutputStream
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096]; // 4KB buffer size
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          byteArrayOutputStream.write(buffer, 0, bytesRead);
        }

        // Convert to byte array
        resultFile = byteArrayOutputStream.toByteArray();

        // Close streams
        byteArrayOutputStream.close();
        inputStream.close();
      }

      connection.disconnect();


    } catch (IOException e) {
      logger.error("Error while encrypting suspect profile", e);
    }

    return resultFile;
  }

}