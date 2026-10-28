package doctrack.model;

public class Document {
    private int documentId;
    private String trackingNo;
    private String docType;
    private String direction;      // INCOMING, OUTGOING, INTERNAL
    private String subject;
    private String sourceName;
    private Integer originOfficeId;
    private Integer currentOfficeId;
    private Integer currentHolderId;
    private String status;
    private String priority;
    private String dateReceived;
    private String dueDate;
    private String dateClosed;
    private String remarks;
    private String dateCreated;
    private Integer createdBy;

    // convenience display fields, populated by DAO joins (not stored directly)
    private String currentOfficeName;
    private String currentHolderName;

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public Integer getOriginOfficeId() { return originOfficeId; }
    public void setOriginOfficeId(Integer originOfficeId) { this.originOfficeId = originOfficeId; }

    public Integer getCurrentOfficeId() { return currentOfficeId; }
    public void setCurrentOfficeId(Integer currentOfficeId) { this.currentOfficeId = currentOfficeId; }

    public Integer getCurrentHolderId() { return currentHolderId; }
    public void setCurrentHolderId(Integer currentHolderId) { this.currentHolderId = currentHolderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getDateReceived() { return dateReceived; }
    public void setDateReceived(String dateReceived) { this.dateReceived = dateReceived; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getDateClosed() { return dateClosed; }
    public void setDateClosed(String dateClosed) { this.dateClosed = dateClosed; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getDateCreated() { return dateCreated; }
    public void setDateCreated(String dateCreated) { this.dateCreated = dateCreated; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public String getCurrentOfficeName() { return currentOfficeName; }
    public void setCurrentOfficeName(String currentOfficeName) { this.currentOfficeName = currentOfficeName; }

    public String getCurrentHolderName() { return currentHolderName; }
    public void setCurrentHolderName(String currentHolderName) { this.currentHolderName = currentHolderName; }

    @Override
    public String toString() {
        return trackingNo + " - " + subject;
    }
}
