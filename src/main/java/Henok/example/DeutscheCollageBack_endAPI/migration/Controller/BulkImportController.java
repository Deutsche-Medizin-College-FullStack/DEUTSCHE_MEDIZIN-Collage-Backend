package Henok.example.DeutscheCollageBack_endAPI.migration.Controller;

import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.*;
import Henok.example.DeutscheCollageBack_endAPI.migration.Service.BulkStudentImportService;
import Henok.example.DeutscheCollageBack_endAPI.migration.Service.CourseMigrationService;
import Henok.example.DeutscheCollageBack_endAPI.migration.Service.FactSheetBulkImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Bulk Student Import Controller
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class BulkImportController {

    private final BulkStudentImportService bulkStudentImportService;
    private final CourseMigrationService courseService;
    private final FactSheetBulkImportService factSheetBulkImportService;

    // -----------[Bulk Student Import]------------------
    // description - One-time endpoint for importing legacy students in bulk.
    //               Receives a list of StudentImportDTO (no files, no photos/documents).
    //               Skips invalid records, continues with valid ones.
    //               Secured for admin only (adjust @PreAuthorize as needed).
    @PostMapping("/students/bulk")
    public ResponseEntity<Map<String, Object>> bulkImportStudents(@RequestBody List<StudentImportDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new BadRequestException("Request body cannot be empty");
        }

        BulkImportStudentResult result = bulkStudentImportService.importStudents(dtos);

        Map<String, Object> response = new HashMap<>();
        response.put("successCount", result.getSuccessCount());
        response.put("failedCount", result.getFailedCount());
        response.put("failedUsernames", result.getFailedUsernames());
        response.put("message", "Bulk import completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Bulk import endpoint.
     * On failure of individual records, they are skipped and listed in failedCourses.
     * Only ADMIN can perform this operation.
     */
    @PostMapping("/courses/bulk")
    public ResponseEntity<BulkImportCourseResponseDTO> bulkImport(@RequestBody List<CourseCreateDTO> dtos) {
        BulkImportCourseResponseDTO response = courseService.bulkImport(dtos);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fact-sheet/bulk")
    public ResponseEntity<?> bulkImportScores(@RequestBody List<StudentCourseScoreImportDTO> scoreList) {

        // Why: All processing and logging is delegated to the service.
        // Service returns success count and list of failed record identifiers.
        BulkImportResult result = factSheetBulkImportService.bulkImportScores(scoreList);

        if (result.getFailedRecords().isEmpty()) {
            return ResponseEntity.ok()
                    .body(Map.of(
                            "message", "All " + result.getSuccessfulCount() + " scores imported successfully",
                            "successfulCount", result.getSuccessfulCount()
                    ));
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .body(Map.of(
                        "message", result.getSuccessfulCount() + " scores imported successfully, " +
                                result.getFailedRecords().size() + " records failed",
                        "successfulCount", result.getSuccessfulCount(),
                        "failedCount", result.getFailedRecords().size(),
                        "failedRecords", result.getFailedRecords()
                ));
    }
}
