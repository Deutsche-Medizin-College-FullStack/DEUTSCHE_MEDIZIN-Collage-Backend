package Henok.example.DeutscheCollageBack_endAPI.DTO;

import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class FormTemplateListDTO {

    private Long id;
    private String name;
    private String description;
    private Set<Role> forRoles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDownloadable;

}