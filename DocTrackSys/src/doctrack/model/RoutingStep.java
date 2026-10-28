package doctrack.model;

public class RoutingStep {
    private int stepId;
    private int documentId;
    private String batchId;     // groups rows created by one multi-recipient/multi-document send
    private Integer fromOfficeId;
    private Integer toOfficeId;
    private Integer toPersonnelId;
    private String action;      // RECEIVED, FORWARDED, REVIEWED, COMPLIED, RETURNED, CLOSED
    private String actionDate;
    private String remarks;
    private Integer loggedBy;

    // convenience, populated by DAO joins
    private String fromOfficeName;
    private String toOfficeName;
    private String toPersonnelName;
    private String loggedByName;

    public int getStepId() { return stepId; }
    public void setStepId(int stepId) { this.stepId = stepId; }

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public Integer getFromOfficeId() { return fromOfficeId; }
    public void setFromOfficeId(Integer fromOfficeId) { this.fromOfficeId = fromOfficeId; }

    public Integer getToOfficeId() { return toOfficeId; }
    public void setToOfficeId(Integer toOfficeId) { this.toOfficeId = toOfficeId; }

    public Integer getToPersonnelId() { return toPersonnelId; }
    public void setToPersonnelId(Integer toPersonnelId) { this.toPersonnelId = toPersonnelId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActionDate() { return actionDate; }
    public void setActionDate(String actionDate) { this.actionDate = actionDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Integer getLoggedBy() { return loggedBy; }
    public void setLoggedBy(Integer loggedBy) { this.loggedBy = loggedBy; }

    public String getFromOfficeName() { return fromOfficeName; }
    public void setFromOfficeName(String fromOfficeName) { this.fromOfficeName = fromOfficeName; }

    public String getToOfficeName() { return toOfficeName; }
    public void setToOfficeName(String toOfficeName) { this.toOfficeName = toOfficeName; }

    public String getToPersonnelName() { return toPersonnelName; }
    public void setToPersonnelName(String toPersonnelName) { this.toPersonnelName = toPersonnelName; }

    public String getLoggedByName() { return loggedByName; }
    public void setLoggedByName(String loggedByName) { this.loggedByName = loggedByName; }
}
