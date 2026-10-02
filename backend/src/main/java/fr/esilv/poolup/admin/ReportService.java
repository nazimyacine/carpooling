package fr.esilv.poolup.admin;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.admin.AdminReportResponse.Person;
import fr.esilv.poolup.admin.AdminReportResponse.Target;
import fr.esilv.poolup.chat.Message;
import fr.esilv.poolup.chat.MessageRepository;
import fr.esilv.poolup.chat.MessageService;
import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.trips.Trip;
import fr.esilv.poolup.trips.TripRepository;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;

    /**
     * Any signed-in user can report a trip, another user, or a message of a discussion they take part in
     * (a private discussion cannot be reported by someone who cannot read it, rule 5).
     */
    @Transactional
    public SubmittedReportResponse submit(Long reporterId, ReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));
        checkTarget(reporterId, request.targetType(), request.targetId());
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(reporterId, request.targetType(),
                request.targetId(), ReportStatus.OPEN)) {
            throw ApiException.conflict("Vous avez déjà signalé ceci ; un administrateur va le traiter.");
        }
        Report report = new Report(reporter, request.targetType(), request.targetId(), request.reason().trim());
        return SubmittedReportResponse.from(reportRepository.save(report));
    }

    /** {@code status} null: all reports. Newest first. */
    @Transactional(readOnly = true)
    public List<AdminReportResponse> list(ReportStatus status) {
        List<Report> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDescIdDesc()
                : reportRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
        return reports.stream().map(this::toAdminResponse).toList();
    }

    /** "Classer" a report: after an action (message deleted, account suspended) or with no follow-up. */
    @Transactional
    public AdminReportResponse resolve(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Signalement introuvable."));
        if (report.getStatus() == ReportStatus.RESOLVED) {
            throw ApiException.conflict("Ce signalement est déjà traité.");
        }
        report.resolve();
        return toAdminResponse(report);
    }

    private void checkTarget(Long reporterId, ReportTargetType type, Long targetId) {
        switch (type) {
            case TRIP -> {
                if (!tripRepository.existsById(targetId)) {
                    throw ApiException.notFound("Trajet introuvable.");
                }
            }
            case USER -> {
                if (!userRepository.existsById(targetId)) {
                    throw ApiException.notFound("Utilisateur introuvable.");
                }
                if (Objects.equals(reporterId, targetId)) {
                    throw ApiException.badRequest("Vous ne pouvez pas vous signaler vous-même.");
                }
            }
            case MESSAGE -> {
                // Same answer for a missing message and a message of a discussion the user cannot read:
                // the reply must not reveal what private discussions contain.
                Message message = messageRepository.findById(targetId)
                        .filter(found -> messageService.canAccessTripChat(found.getTrip(), reporterId))
                        .orElseThrow(() -> ApiException.notFound("Message introuvable."));
                if (Objects.equals(message.getSender().getId(), reporterId)) {
                    throw ApiException.badRequest("Vous ne pouvez pas signaler votre propre message.");
                }
            }
        }
    }

    private AdminReportResponse toAdminResponse(Report report) {
        return new AdminReportResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getStatus(), report.getCreatedAt(), Person.from(report.getReporter()),
                describeTarget(report.getTargetType(), report.getTargetId()));
    }

    private Target describeTarget(ReportTargetType type, Long targetId) {
        return switch (type) {
            case TRIP -> tripRepository.findById(targetId)
                    .map(trip -> new Target(true, route(trip), null, trip.getId(), Person.from(trip.getDriver())))
                    .orElse(Target.missing("Trajet supprimé"));
            case USER -> userRepository.findById(targetId)
                    .map(user -> new Target(true, user.getFirstName() + " " + user.getLastName(), null, null,
                            Person.from(user)))
                    .orElse(Target.missing("Compte supprimé"));
            case MESSAGE -> messageRepository.findById(targetId)
                    .map(message -> new Target(true, "Message dans la discussion " + route(message.getTrip()),
                            message.getContent(), message.getTrip().getId(), Person.from(message.getSender())))
                    .orElse(Target.missing("Message supprimé"));
        };
    }

    private static String route(Trip trip) {
        return trip.getDepartureCity().getName() + " → " + trip.getArrivalCity().getName();
    }
}
