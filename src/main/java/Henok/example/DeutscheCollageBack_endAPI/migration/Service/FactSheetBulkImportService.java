package Henok.example.DeutscheCollageBack_endAPI.migration.Service;

// Updated FactSheetBulkImportService

import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.BulkImportResult;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.StudentCourseScoreImportDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FactSheetBulkImportService {

    private final UserRepository userRepository;
    private final CourseRepo courseRepository;
    private final BatchClassYearSemesterRepo batchRepository;
    private final CourseSourceRepo courseSourceRepository;
    private final StudentCourseScoreRepo scoreRepository;
    private final StudentDetailsRepository studentDetailsRepository;  // ← new dependency


    // Easy to change later if category ID changes
    private static final Long COMMON_COURSE_CATEGORY_ID = 3L;

    /**
     * Memory-safe bulk import with department-aware common course correction.
     * IMPORTANT: This is a critical one-time migration — NO records are skipped.
     * All records are saved. Mismatches are only logged (warnings or corrections).
     */
    public BulkImportResult bulkImportScores(List<StudentCourseScoreImportDTO> dtoList) {
        List<String> failedRecords = new ArrayList<>();  // now only used for severe parsing errors
        int successfulCount = 0;
        int total = dtoList.size();

        System.out.println("=== STARTING CRITICAL BULK IMPORT OF " + total + " RECORDS ===");
        System.out.println("Common course category ID: " + COMMON_COURSE_CATEGORY_ID);

        for (int i = 0; i < total; i++) {
            StudentCourseScoreImportDTO dto = dtoList.get(i);

            String rawStudentId = safeTrim(dto.getStudent_user_id());
            String rawCourseId  = safeTrim(dto.getCourse_id());
            String rawBatchId   = safeTrim(dto.getBatch_class_year_semester_id());
            String rawSourceId  = safeTrim(dto.getSource_id());

            String recordKey = rawStudentId + " - " + rawCourseId + " - " + rawBatchId;

            try {
                // Parse IDs
                Long studentId = parseLong(rawStudentId, "student_user_id");
                Long courseId  = parseLong(rawCourseId,  "course_id");
                Long batchId   = parseLong(rawBatchId,  "batch_class_year_semester_id");
                Long sourceId  = parseLong(rawSourceId, "source_id");

                recordKey = studentId + " - " + courseId + " - " + batchId;

                // 1. Get student & department
                User user = userRepository.findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + studentId));

                StudentDetails details = studentDetailsRepository.findByUser(user)
                        .orElseThrow(() -> new ResourceNotFoundException("StudentDetails not found for user: " + studentId));

                Department studentDept = details.getDepartmentEnrolled();
                if (studentDept == null) {
                    logWarning(studentId, courseId, batchId, "Student has no enrolled department - using original course");
                }

                // 2. Get requested course
                Course requestedCourse = courseRepository.findById(courseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

                Course finalCourse = requestedCourse;  // default

                // 3. Common course correction logic (only for common courses)
                if (isCommonCourse(requestedCourse)) {

                    if (!Objects.equals(requestedCourse.getDepartment(), studentDept)) {

                        // Try to find the correct common course for student's department
                        Optional<Course> correctedOpt = courseRepository.findBycTitleAndCategoryAndDepartment(
                                requestedCourse.getCTitle(),
                                requestedCourse.getCategory(),
                                studentDept
                        );

                        if (correctedOpt.isPresent()) {
                            Course corrected = correctedOpt.get();
                            finalCourse = corrected;

                            logCorrection(
                                    studentId,
                                    requestedCourse.getCID(), corrected.getCID(),
                                    batchId,
                                    "Common course reassigned from dept " +
                                            (requestedCourse.getDepartment() != null ? requestedCourse.getDepartment().getDeptName() : "null") +
                                            " → " + (studentDept != null ? studentDept.getDeptName() : "null")
                            );
                        } else {
                            logWarning(
                                    studentId, courseId, batchId,
                                    "Common course department mismatch - no matching common course found for student's dept"
                            );
                        }
                    }
                    // else: departments match → no action needed
                } else {
                    // Not common course → check mismatch but never correct
                    if (studentDept != null && !Objects.equals(requestedCourse.getDepartment(), studentDept)) {
                        logWarning(
                                studentId, courseId, batchId,
                                "Department-specific course mismatch: student in " +
                                        studentDept.getDeptName() + ", course belongs to " +
                                        (requestedCourse.getDepartment() != null ? requestedCourse.getDepartment().getDeptName() : "null")
                        );
                    }
                }

                // 4. Proceed with other entities
                BatchClassYearSemester batch = batchRepository.findById(batchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + batchId));

                CourseSource source = courseSourceRepository.findById(sourceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Source not found: " + sourceId));

                // 5. Duplicate check (still useful)
                boolean exists = scoreRepository.existsByStudentAndCourseAndBatchClassYearSemesterAndCourseSource(
                        user, finalCourse, batch, source);

                if (exists) {
                    logWarning(studentId, finalCourse.getCID(), batchId, "Duplicate score entry - skipped saving");
                    continue;  // ← only case we skip saving (duplicate protection)
                }

                // 6. Parse score & isReleased
                Double parsedScore = parseDouble(dto.getScore());

                boolean isReleased;
                String isReleasedRaw = safeTrim(dto.getIs_released());
                if ("1".equals(isReleasedRaw)) {
                    isReleased = true;
                } else if ("0".equals(isReleasedRaw)) {
                    isReleased = false;
                } else {
                    isReleased = parsedScore != null;
                }

                // 7. Save
                StudentCourseScore scoreEntity = new StudentCourseScore();
                scoreEntity.setStudent(user);
                scoreEntity.setCourse(finalCourse);           // ← may be corrected
                scoreEntity.setBatchClassYearSemester(batch);
                scoreEntity.setCourseSource(source);
                scoreEntity.setScore(parsedScore);
                scoreEntity.setReleased(isReleased);          // note: field name was 'released' in your last code

                scoreRepository.save(scoreEntity);
                successfulCount++;

            } catch (NumberFormatException e) {
                String field = e.getMessage();
                logSevereError(rawStudentId, rawCourseId, rawBatchId, "Invalid format in " + field);
                failedRecords.add(recordKey + " (parse error: " + field + ")");
            } catch (ResourceNotFoundException e) {
                logSevereError(rawStudentId, rawCourseId, rawBatchId, e.getMessage());
                failedRecords.add(recordKey + " (" + e.getMessage() + ")");
            } catch (Exception e) {
                logSevereError(rawStudentId, rawCourseId, rawBatchId, "Unexpected: " + e.getClass().getSimpleName());
                failedRecords.add(recordKey + " (unexpected error)");
            }

            // Progress
            if ((i + 1) % 1000 == 0 || (i + 1) == total) {
                System.out.println("Processed " + (i + 1) + "/" + total +
                        " | Saved: " + successfulCount +
                        " | Severe errors: " + failedRecords.size());
            }
        }

        System.out.println("=== CRITICAL IMPORT FINISHED ===");
        System.out.println("Total: " + total);
        System.out.println("Saved: " + successfulCount);
        System.out.println("Severe parse/reference errors: " + failedRecords.size());
        System.out.println("=====================================");

        return new BulkImportResult(successfulCount, failedRecords);
    }

    // ────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────

    private boolean isCommonCourse(Course course) {
        return course.getCategory() != null &&
                COMMON_COURSE_CATEGORY_ID.equals(course.getCategory().getCatID());
    }

    private String safeTrim(String s) {
        return s != null ? s.trim() : "";
    }

    private void logCorrection(Long studentId, Long oldCourseId, Long newCourseId, Long batchId, String reason) {
        System.out.println("CORRECTION ─ Student " + studentId +
                " | Old course: " + oldCourseId +
                " → New course: " + newCourseId +
                " | Batch: " + batchId +
                " | Reason: " + reason);
    }

    private void logWarning(Long studentId, Long courseId, Long batchId, String msg) {
        System.out.println("WARNING ─ Student " + studentId +
                " | Course " + courseId +
                " | Batch " + batchId +
                " | " + msg);
    }

    private void logSevereError(String studentId, String courseId, String batchId, String msg) {
        System.out.println("SEVERE ERROR ─ " + studentId + " - " + courseId + " - " + batchId +
                " | " + msg);
    }

    // Keep these helper methods unchanged
    private Long parseLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException(fieldName);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(fieldName);
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("score");
        }
    }

    private void logFailure(String studentId, String courseId, String batchId, String error) {
        System.out.println("============ " + studentId + " ============");
        System.out.println("* " + studentId);
        System.out.println("* " + courseId);
        System.out.println("* " + batchId);
        System.out.println("* " + error);
        System.out.println();
    }
}