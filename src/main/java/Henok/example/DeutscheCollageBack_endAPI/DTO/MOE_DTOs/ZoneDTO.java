package Henok.example.DeutscheCollageBack_endAPI.DTO.MOE_DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoneDTO {

    private String zoneCode; // Unique code for the zone
    private String zone; // Name of the zone
    private String regionCode; // Code of the associated region
}