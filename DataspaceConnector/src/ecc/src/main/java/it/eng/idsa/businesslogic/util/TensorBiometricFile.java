package it.eng.idsa.businesslogic.util;

public class TensorBiometricFile {
  private String fileName;
  private byte[] content;
  private String contentType;

  public TensorBiometricFile(String fileName, byte[] content, String contentType) {
    this.fileName = fileName;
    this.content = content;
    this.contentType = contentType;
  }

  public String getFileName() {
    return this.fileName;
  }
  public void setFileName(String fileName) {
     this.fileName = fileName;
  }

  public byte[] getContent() {
    return this.content;
  }
  public void setContent(byte[] content) {
     this.content = content;
  }

  public String getContentType() {
    return this.contentType;
  }
  public void setContentType(String contentType) {
     this.contentType = contentType;
  }
}