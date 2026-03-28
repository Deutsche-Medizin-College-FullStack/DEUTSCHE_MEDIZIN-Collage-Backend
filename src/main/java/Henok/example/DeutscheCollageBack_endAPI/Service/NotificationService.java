package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.NotificationDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.NotificationResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Notification;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Repository.NotificationRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    // Create notification for a list of roles or a single user
    public void createNotification(List<Role> roles, User singleUser, Role senderRole, String message) {
        // Validate inputs
        if ((roles == null || roles.isEmpty()) && singleUser == null) {
            return; // Empty roles and no single user: no-op
        }
        if (senderRole == null || message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Sender role and message are required");
        }

        // Collect users to notify
        Set<User> usersToNotify = new HashSet<>();
        if (roles != null && !roles.isEmpty()) {
            // Fetch users by roles
            for (Role role : roles) {
                List<User> roleUsers = userRepository.findByRole(role);
                if (!roleUsers.isEmpty()) {
                    usersToNotify.addAll(roleUsers);
                }
            }
        }
        if (singleUser != null) {
            // Verify single user exists
            if (!userRepository.existsById(singleUser.getId())) {
                throw new IllegalStateException("User not found: " + singleUser.getId());
            }
            usersToNotify.add(singleUser);
        }

        // Create notifications
        for (User user : usersToNotify) {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setSenderRole(senderRole);
            notification.setMessage(message);
            // createdAt/isRead auto-set in entity
            notificationRepository.save(notification);
        }
        // Purpose: Creates notifications for users by roles or a single user.
        // Why Set: Avoids duplicates if a user has multiple roles or is also singleUser.
        // Why no-op on empty: Allows flexibility without throwing errors unnecessarily.
        // Error handling: Throws for invalid inputs or missing users.
    }

    // Retrieve all notifications for a user
    public List<NotificationDTO> getUserNotifications(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return notifications.stream()
                .map(n -> new NotificationDTO(
                        n.getId(),
                        n.getSenderRole().name(),
                        n.getMessage(),
                        n.getCreatedAt(),
                        n.isRead()
                ))
                .collect(Collectors.toList());
        // Purpose: Fetches user’s notifications, mapped to DTO for safe API exposure.
        // Why DTO: Prevents leaking entity internals (e.g., User object).
        // Error handling: Throws for null user.
    }

    // Mark a notification as read for a user
    public void markNotificationAsRead(User user, Long notificationId) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID cannot be null");
        }
        Notification notification = notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new IllegalStateException("Notification not found or not owned by user: " + notificationId));
        if (notification.isRead()) {
            return; // Already read, no action needed
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        // Purpose: Marks a specific notification as read for the authenticated user.
        // Why user check: Ensures users can only mark their own notifications.
        // Error handling: Throws for invalid notification or unauthorized access.
    }

    // Add to existing NotificationService class
    public List<NotificationDTO> getLatestFiveNotifications(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        Pageable pageable = PageRequest.of(0, 5); // Limit to 5 records
        List<Notification> notifications = notificationRepository.findTop5ByUserOrderByCreatedAtDesc(user, pageable);
        return notifications.stream()
                .map(n -> new NotificationDTO(
                        n.getId(),
                        n.getSenderRole().name(),
                        n.getMessage(),
                        n.getCreatedAt(),
                        n.isRead()
                ))
                .collect(Collectors.toList());
        // Purpose: Fetches the 5 most recent notifications for a user, sorted by createdAt descending.
        // Why Pageable: Efficiently limits to 5 at DB level, avoiding overfetching.
        // Error handling: Throws for null user.
    }

    // Add this method to NotificationService.java
    public NotificationResponseDTO getLatestNotificationsWithStats(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        // Get latest 5 notifications
        Pageable pageable = PageRequest.of(0, 5);
        List<Notification> latestNotifications = notificationRepository
                .findTop5ByUserOrderByCreatedAtDesc(user, pageable);

        // Get statistics
        long total = notificationRepository.countByUser(user);
        long unread = notificationRepository.countByUserAndIsRead(user, false);
        long read = total - unread;

        // Map to DTOs
        List<NotificationDTO> notificationDTOs = latestNotifications.stream()
                .map(n -> new NotificationDTO(
                        n.getId(),
                        n.getSenderRole().name(),
                        n.getMessage(),
                        n.getCreatedAt(),
                        n.isRead()
                ))
                .collect(Collectors.toList());

        // Build response
        NotificationResponseDTO.Info info = new NotificationResponseDTO.Info(total, unread, read);

        return new NotificationResponseDTO(info, notificationDTOs);
    }

    public void markAllNotificationsAsRead(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        }
        // Purpose: Marks all unread notifications for a user as read.
        // Why loop: Ensures only unread notifications are updated, saving DB operations.
        // Error handling: Throws for null user; no-op if no notifications.
    }
}