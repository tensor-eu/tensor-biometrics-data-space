package it.eng.idsa.businesslogic.service;

public class TENSORConnector {
    private String id;
    private String ip;
    private String pod;
    private String ethAddress;
    private String fuzzyExtractorFile;
    private String cmsAPI;
    private String solidAPI;
    private String dspAPI;

    public TENSORConnector(String id, String ip, String pod, String ethAddress, String fuzzyExtractorFile, String cmsAPI, String solidAPI, String dspAPI) {
        this.id = id;
        this.ip = ip;
        this.pod = pod;
        this.ethAddress = ethAddress;
        this.fuzzyExtractorFile = fuzzyExtractorFile;
        this.cmsAPI = cmsAPI;
        this.solidAPI = solidAPI;
        this.dspAPI = dspAPI;
    }

    // Getters
    public String getId() { return id; }
    public String getIp() { return ip; }
    public String getPod() { return pod; }
    public String getEthAddress() { return ethAddress; }
    public String getFuzzyExtractorFile() { return fuzzyExtractorFile; }
    public String getCmsAPI() { return cmsAPI; }
    public String getSolidAPI() { return solidAPI; }
    public String getDspAPI() { return dspAPI; }
}
