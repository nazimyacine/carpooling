package fr.esilv.poolup.admin;

import java.time.Instant;

import fr.esilv.poolup.users.User;

/**
 * Report as shown on the admin screen, with what is needed to decide without another call:
 * who reported, what is targeted (message text, trip route, account) and who is concerned.
 */
public record AdminReportResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        String reason,
        ReportStatus status,
        Instant createdAt,
        Person reporter,
        Target target) {

    public record Person(Long id, String firstName, String lastName, String email) {

        static Person from(User user) {
            return user == null ? null
                    : new Person(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
        }
    }

    /**
     * {@code exists} is false once the target is gone (e.g. message deleted by an admin).
     * {@code concernedUser} is the message sender, the trip driver or the reported account.
     * {@code content} is the message text (MESSAGE only); {@code tripId} is set for MESSAGE and TRIP.
     */
    public record Target(boolean exists, String label, String content, Long tripId, Person concernedUser) {

        static Target missing(String label) {
            return new Target(false, label, null, null, null);
        }
    }
}
