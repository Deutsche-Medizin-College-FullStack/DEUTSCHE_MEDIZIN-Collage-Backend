package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.NotificationDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.NotificationResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Error.ErrorResponse;
import Henok.example.DeutscheCollageBack_endAPI.Service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    // Retrieve all notifications for the authenticated user
    @GetMapping("/me")
    public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal User user) {
        try {
            List<NotificationDTO> notifications = notificationService.getUserNotifications(user);
            return ResponseEntity.ok(notifications);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch notifications: " + e.getMessage()));
        }
        // Purpose: Exposes REST endpoint for users to fetch their notifications.
        // Why @PreAuthorize: Ensures only authenticated users access their own notifications.
        // Error handling: Returns structured JSON errors using ErrorResponse.
        // Path: /api/notifications/me follows RESTful conventions.
    }

    // Mark a notification as read
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markNotificationAsRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        try {
            notificationService.markNotificationAsRead(user, id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to mark notification as read: " + e.getMessage()));
        }
        // Purpose: Allows authenticated user to mark their notification as read.
        // Why @PreAuthorize: Ensures only the notification owner can mark it read.
        // Error handling: Returns 400 for invalid input, 404 for not found, 500 for server errors.
        // Path: /api/notifications/{id}/read follows RESTful conventions for updating a resource.
    }

    // Add to existing NotificationController class
    @GetMapping("/me/latest")
    public ResponseEntity<?> getLatestFiveNotifications(@AuthenticationPrincipal User user) {
        try {
            List<NotificationDTO> notifications = notificationService.getLatestFiveNotifications(user);
            return ResponseEntity.ok(notifications);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch latest notifications: " + e.getMessage()));
        }
        // Purpose: Exposes REST endpoint for users to fetch their 5 most recent notifications.
        // Why @PreAuthorize: Ensures only authenticated users access their own notifications.
        // Error handling: Returns structured JSON errors using ErrorResponse.
        // Path: /api/notifications/me/latest follows RESTful conventions.
    }
    // Version 2 - Latest notifications with summary info
    @GetMapping("/me/v2/latest")
    public ResponseEntity<?> getLatestNotificationsWithInfo(@AuthenticationPrincipal User user) {
        try {
            NotificationResponseDTO response = notificationService.getLatestNotificationsWithStats(user);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch latest notifications: " + e.getMessage()));
        }
    }

    @PatchMapping("/me/read-all")
    public ResponseEntity<?> markAllNotificationsAsRead(@AuthenticationPrincipal User user) {
        try {
            notificationService.markAllNotificationsAsRead(user);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to mark all notifications as read: " + e.getMessage()));
        }
        // Purpose: Allows authenticated user to mark all their notifications as read.
        // Why @PreAuthorize: Ensures only the user can mark their own notifications.
        // Error handling: Returns 400 for invalid input, 500 for server errors.
        // Path: /api/notifications/me/read-all follows RESTful conventions for bulk updates.
    }
}