package Henok.example.DeutscheCollageBack_endAPI.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;
    private String senderRole; // String to avoid exposing enum directly
    private String message;
    private Date createdAt;
    private boolean isRead;
    // private Long numOfUnreadNotifications; // Optional: total unread count for the user

    // Purpose: Maps Notification entity to safe API response, hiding User entity.
}
