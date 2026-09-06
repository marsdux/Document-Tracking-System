package doctrack.model;

public class Attachment {
    private int attachmentId;
    private int documentId;
    private String fileName;
    private String filePath;
    private String description;
    private Integer uploadedBy;
    private String dateUploaded;

    public int getAttachmentId() { return attachmentId; }
    public void setAttachmentId(int attachmentId) { this.attachmentId = attachmentId; }

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Integer uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getDateUploaded() { return dateUploaded; }
    public void setDateUploaded(String dateUploaded) { this.dateUploaded = dateUploaded; }

    @Override
    public String toString() { return fileName; }
}
