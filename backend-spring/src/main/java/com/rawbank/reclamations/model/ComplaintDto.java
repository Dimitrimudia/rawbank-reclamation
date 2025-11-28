package com.rawbank.reclamations.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public class ComplaintDto {
    private String SITE;
    private String ZONE;
    private String DEPARTEMENT;
    private String DOMAINE;
    private String AWSTemplateFormatVersion;
    private String TYPERECLAMATION;
    private String CANALUTILISE;
    private String MOTIFBCC;
    private String AGENCECLIENT;

    // 8 chiffres stricts
    @Pattern(regexp = "^\\d{8}$", message = "NUMEROCLIENT doit contenir exactement 8 chiffres")
    private String NUMEROCLIENT;

    private Map<String, Object> Conditions;

    // 10 chiffres stricts
    @Pattern(regexp = "^\\d{10}$", message = "TELEPHONECLIENT doit contenir exactement 10 chiffres")
    private String TELEPHONECLIENT;
    private String COMPTESOURCE;
    // format jour-mois-année: d-mm-yyy (ex: 1-12-2025)
    @Pattern(regexp = "^([1-9]|[12][0-9]|3[01])-((0?[1-9])|(1[0-2]))-\\d{3}$", message = "DATETRANSACTION doit être au format d-mm-yyy")
    private String DATETRANSACTION;

    private String NUMEROCARTE;
    private Object MONTANT; // peut être String ou Number en entrée
    private String MONTANTCONVERTI;
    private String DEVISE;
    private Boolean EXTOURNE;
    private String GERANTAGENCE;

    @NotBlank
    private String MOTIF;
    private String DESCRIPTION;
    private String AVISMOTIVE;

    public String getSITE() { return SITE; }
    public void setSITE(String SITE) { this.SITE = SITE; }
    public String getZONE() { return ZONE; }
    public void setZONE(String ZONE) { this.ZONE = ZONE; }
    public String getDEPARTEMENT() { return DEPARTEMENT; }
    public void setDEPARTEMENT(String DEPARTEMENT) { this.DEPARTEMENT = DEPARTEMENT; }
    public String getDOMAINE() { return DOMAINE; }
    public void setDOMAINE(String DOMAINE) { this.DOMAINE = DOMAINE; }
    public String getAWSTemplateFormatVersion() { return AWSTemplateFormatVersion; }
    public void setAWSTemplateFormatVersion(String AWSTemplateFormatVersion) { this.AWSTemplateFormatVersion = AWSTemplateFormatVersion; }
    public String getTYPERECLAMATION() { return TYPERECLAMATION; }
    public void setTYPERECLAMATION(String TYPERECLAMATION) { this.TYPERECLAMATION = TYPERECLAMATION; }
    public String getCANALUTILISE() { return CANALUTILISE; }
    public void setCANALUTILISE(String CANALUTILISE) { this.CANALUTILISE = CANALUTILISE; }
    public String getMOTIFBCC() { return MOTIFBCC; }
    public void setMOTIFBCC(String MOTIFBCC) { this.MOTIFBCC = MOTIFBCC; }
    public String getAGENCECLIENT() { return AGENCECLIENT; }
    public void setAGENCECLIENT(String AGENCECLIENT) { this.AGENCECLIENT = AGENCECLIENT; }
    public String getNUMEROCLIENT() { return NUMEROCLIENT; }
    public void setNUMEROCLIENT(String NUMEROCLIENT) { this.NUMEROCLIENT = NUMEROCLIENT; }
    public Map<String, Object> getConditions() { return Conditions; }
    public void setConditions(Map<String, Object> conditions) { Conditions = conditions; }
    public String getTELEPHONECLIENT() { return TELEPHONECLIENT; }
    public void setTELEPHONECLIENT(String TELEPHONECLIENT) { this.TELEPHONECLIENT = TELEPHONECLIENT; }
    public String getCOMPTESOURCE() { return COMPTESOURCE; }
    public void setCOMPTESOURCE(String COMPTESOURCE) { this.COMPTESOURCE = COMPTESOURCE; }
    public String getDATETRANSACTION() { return DATETRANSACTION; }
    public void setDATETRANSACTION(String DATETRANSACTION) { this.DATETRANSACTION = DATETRANSACTION; }
    public String getNUMEROCARTE() { return NUMEROCARTE; }
    public void setNUMEROCARTE(String NUMEROCARTE) { this.NUMEROCARTE = NUMEROCARTE; }
    public Object getMONTANT() { return MONTANT; }
    public void setMONTANT(Object MONTANT) { this.MONTANT = MONTANT; }
    public String getMONTANTCONVERTI() { return MONTANTCONVERTI; }
    public void setMONTANTCONVERTI(String MONTANTCONVERTI) { this.MONTANTCONVERTI = MONTANTCONVERTI; }
    public String getDEVISE() { return DEVISE; }
    public void setDEVISE(String DEVISE) { this.DEVISE = DEVISE; }
    public Boolean getEXTOURNE() { return EXTOURNE; }
    public void setEXTOURNE(Boolean EXTOURNE) { this.EXTOURNE = EXTOURNE; }
    public String getGERANTAGENCE() { return GERANTAGENCE; }
    public void setGERANTAGENCE(String GERANTAGENCE) { this.GERANTAGENCE = GERANTAGENCE; }
    public String getMOTIF() { return MOTIF; }
    public void setMOTIF(String MOTIF) { this.MOTIF = MOTIF; }
    public String getDESCRIPTION() { return DESCRIPTION; }
    public void setDESCRIPTION(String DESCRIPTION) { this.DESCRIPTION = DESCRIPTION; }
    public String getAVISMOTIVE() { return AVISMOTIVE; }
    public void setAVISMOTIVE(String AVISMOTIVE) { this.AVISMOTIVE = AVISMOTIVE; }
}
