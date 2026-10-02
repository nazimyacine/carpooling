package fr.esilv.poolup.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.chat.MessageRepository;
import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.trips.TripRepository;
import fr.esilv.poolup.trips.TripResponse;
import fr.esilv.poolup.users.Role;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserRepository;
import fr.esilv.poolup.users.UserStatus;

import lombok.RequiredArgsConstructor;

/** Sanctions and moderation. Only reachable through {@code /api/admin/**} (role ADMIN, see SecurityConfig). */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final TripRepository tripRepository;

    /** {@code query} (optional) is searched in the email, first name and last name. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(String query) {
        List<User> users = query == null || query.isBlank()
                ? userRepository.findAllByOrderByCreatedAtDescIdDesc()
                : userRepository.search("%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%");
        return users.stream().map(AdminUserResponse::from).toList();
    }

    /**
     * A suspended account can no longer sign in, and its current token is refused at the next request
     * (see the token validator in SecurityConfig). Administrators cannot be suspended, so the
     * administration can never lock itself out.
     */
    @Transactional
    public AdminUserResponse suspend(Long userId) {
        User user = findUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.conflict("Un compte administrateur ne peut pas être suspendu.");
        }
        user.setStatus(UserStatus.SUSPENDED);
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse reactivate(Long userId) {
        User user = findUser(userId);
        user.setStatus(UserStatus.ACTIVE);
        return AdminUserResponse.from(user);
    }

    /** Moderation of an abusive message: removed from the discussion for everyone. */
    @Transactional
    public void deleteMessage(Long messageId) {
        if (!messageRepository.existsById(messageId)) {
            throw ApiException.notFound("Message introuvable.");
        }
        messageRepository.deleteById(messageId);
    }

    /** Every trip, whatever its status, latest departure first. */
    @Transactional(readOnly = true)
    public List<TripResponse> listTrips() {
        return tripRepository.findAll((trip, query, cb) -> cb.conjunction(), Sort.by(Sort.Direction.DESC, "departureAt", "id"))
                .stream()
                .map(TripResponse::from)
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable."));
    }

    /** Typed characters are matched literally: "%" or "_" in the search box are not wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
