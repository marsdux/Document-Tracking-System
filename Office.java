package doctrack.model;

public class Office {
    private int officeId;
    private String officeName;
    private Integer parentOfficeId;
    private boolean active;
    private int sortOrder;

    public Office() { }

    public Office(int officeId, String officeName, Integer parentOfficeId) {
        this.officeId = officeId;
        this.officeName = officeName;
        this.parentOfficeId = parentOfficeId;
        this.active = true;
    }

    public int getOfficeId() { return officeId; }
    public void setOfficeId(int officeId) { this.officeId = officeId; }

    public String getOfficeName() { return officeName; }
    public void setOfficeName(String officeName) { this.officeName = officeName; }

    public Integer getParentOfficeId() { return parentOfficeId; }
    public void setParentOfficeId(Integer parentOfficeId) { this.parentOfficeId = parentOfficeId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String toString() { return officeName; }
}
