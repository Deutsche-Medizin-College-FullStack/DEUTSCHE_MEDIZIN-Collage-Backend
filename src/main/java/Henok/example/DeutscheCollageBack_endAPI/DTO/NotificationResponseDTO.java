package Henok.example.DeutscheCollageBack_endAPI.DTO;

import lombok.Data;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

// Put this in a new file: NotificationResponseDTO.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDTO {

    private Info info;
    private List<NotificationDTO> notifications;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Info {
        private long totalNotifications;
        private long unreadCount;
        private long readCount;
    }
}


