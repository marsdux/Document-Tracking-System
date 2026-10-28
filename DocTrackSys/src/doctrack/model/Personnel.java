package doctrack.model;

public class Personnel {
    private int personnelId;
    private String fullName;
    private String position;
    private Integer officeId;
    private boolean active;

    // convenience, populated by DAO joins
    private String officeName;

    public int getPersonnelId() { return personnelId; }
    public void setPersonnelId(int personnelId) { this.personnelId = personnelId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public Integer getOfficeId() { return officeId; }
    public void setOfficeId(Integer officeId) { this.officeId = officeId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getOfficeName() { return officeName; }
    public void setOfficeName(String officeName) { this.officeName = officeName; }

    @Override
    public String toString() {
        return fullName + (position != null && !position.isEmpty() ? " (" + position + ")" : "");
    }
}
