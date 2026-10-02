package fr.esilv.poolup.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.esilv.poolup.trips.TripResponse;

import lombok.RequiredArgsConstructor;

/**
 * Administration screens. Every route is under {@code /api/admin/**}, reserved to the ADMIN role by
 * SecurityConfig. A report is handled in two calls: the action (delete the message, suspend the account),
 * then {@code resolve}; "Classer sans suite" is {@code resolve} alone.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReportService reportService;
    private final AdminService adminService;

    /** {@code status} OPEN ("À traiter") or RESOLVED ("Traités"); all reports if omitted. */
    @GetMapping("/reports")
    public List<AdminReportResponse> reports(@RequestParam(required = false) ReportStatus status) {
        return reportService.list(status);
    }

    @PostMapping("/reports/{id}/resolve")
    public AdminReportResponse resolve(@PathVariable Long id) {
        return reportService.resolve(id);
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users(@RequestParam(required = false) String query) {
        return adminService.listUsers(query);
    }

    @PostMapping("/users/{id}/suspend")
    public AdminUserResponse suspend(@PathVariable Long id) {
        return adminService.suspend(id);
    }

    @PostMapping("/users/{id}/reactivate")
    public AdminUserResponse reactivate(@PathVariable Long id) {
        return adminService.reactivate(id);
    }

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable Long id) {
        adminService.deleteMessage(id);
    }

    @GetMapping("/trips")
    public List<TripResponse> trips() {
        return adminService.listTrips();
    }
}
