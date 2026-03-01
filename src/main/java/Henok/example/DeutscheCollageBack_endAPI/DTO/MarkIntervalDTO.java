package Henok.example.DeutscheCollageBack_endAPI.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkIntervalDTO {

    private Long id;
    private String description;
    private double min;
    private double max;
    private double givenValue;
    private String gradeLetter;
}