package fr.esilv.poolup.chat;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long tripId,
        Long senderId,
        String senderFirstName,
        String senderLastName,
        String content,
        Instant sentAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getTrip().getId(),
                message.getSender().getId(),
                message.getSender().getFirstName(),
                message.getSender().getLastName(),
                message.getContent(),
                message.getSentAt());
    }
}
