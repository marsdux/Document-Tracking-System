package doctrack.ui;

import doctrack.dao.OfficeDAO;
import doctrack.dao.PersonnelDAO;
import doctrack.model.Office;
import doctrack.model.Personnel;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * One selectable forwarding target: either a whole office (no specific
 * person named yet) or a specific person within an office. Used by the
 * multi-select recipient list in RoutingDialog and BatchForwardDialog so
 * a single send action can target any mix of offices and individuals.
 */
public final class RecipientEntry {
    public final Integer officeId;
    public final Integer personnelId;
    private final String label;

    private RecipientEntry(Integer officeId, Integer personnelId, String label) {
        this.officeId = officeId;
        this.personnelId = personnelId;
        this.label = label;
    }

    public static RecipientEntry office(Office o) {
        return new RecipientEntry(o.getOfficeId(), null, "[Office] " + o.getOfficeName());
    }

    public static RecipientEntry personnel(Personnel p) {
        return new RecipientEntry(p.getOfficeId(), p.getPersonnelId(),
                "        " + p.getFullName() + (p.getPosition() != null && !p.getPosition().isEmpty()
                        ? " (" + p.getPosition() + ")" : "") + " — " + p.getOfficeName());
    }

    @Override
    public String toString() { return label; }

    /** Builds the combined office + personnel picklist, offices first, each followed by its people. */
    public static List<RecipientEntry> loadAll(OfficeDAO officeDAO, PersonnelDAO personnelDAO) throws SQLException {
        List<RecipientEntry> entries = new ArrayList<>();
        List<Office> offices = officeDAO.findActive();
        for (Office o : offices) {
            entries.add(RecipientEntry.office(o));
            List<Personnel> people = personnelDAO.findByOffice(o.getOfficeId());
            for (Personnel p : people) {
                entries.add(RecipientEntry.personnel(p));
            }
        }
        return entries;
    }
}
