package fr.esilv.poolup.chat;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.bookings.BookingRepository;
import fr.esilv.poolup.bookings.BookingStatus;
import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.trips.Trip;
import fr.esilv.poolup.trips.TripRepository;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(Long tripId, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> ApiException.notFound("Trajet introuvable."));
        ensureUserCanAccessTripChat(trip, userId);
        return messageRepository.findByTripIdOrderBySentAtAscIdAsc(tripId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Transactional
    public MessageResponse sendMessage(Long tripId, Long senderId, String content) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> ApiException.notFound("Trajet introuvable."));
        ensureUserCanAccessTripChat(trip, senderId);

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));

        Message message = new Message(trip, sender, content.trim());
        return MessageResponse.from(messageRepository.saveAndFlush(message));
    }

    private void ensureUserCanAccessTripChat(Trip trip, Long userId) {
        if (Objects.equals(trip.getDriver().getId(), userId)) {
            return;
        }
        boolean confirmedPassenger = bookingRepository.existsByTripIdAndPassengerIdAndStatus(
                trip.getId(), userId, BookingStatus.CONFIRMED);
        if (!confirmedPassenger) {
            throw ApiException.forbidden("Vous n'avez pas accès à cette discussion.");
        }
    }
}
