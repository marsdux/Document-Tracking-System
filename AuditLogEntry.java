package doctrack.model;

public class AuditLogEntry {
    private int logId;
    private Integer documentId;
    private String documentTrackingNo; // joined; null if the document was later deleted
    private Integer userId;
    private String userFullName;       // joined; null if the user account was later removed
    private String actionType;
    private String details;
    private String actionDate;

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public Integer getDocumentId() { return documentId; }
    public void setDocumentId(Integer documentId) { this.documentId = documentId; }

    public String getDocumentTrackingNo() { return documentTrackingNo; }
    public void setDocumentTrackingNo(String documentTrackingNo) { this.documentTrackingNo = documentTrackingNo; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getActionDate() { return actionDate; }
    public void setActionDate(String actionDate) { this.actionDate = actionDate; }
}
