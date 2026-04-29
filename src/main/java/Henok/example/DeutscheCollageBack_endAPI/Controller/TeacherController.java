package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherListDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherCoursesResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherDashboardResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherProfileResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherStudentsResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.TeacherRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.TeacherDetail;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.ErrorResponse;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Service.TeacherService;
import Henok.example.DeutscheCollageBack_endAPI.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final UserService userService;

    // -----------[Bulk Update Teacher Account Status]------------------
    // description - Updates account status in bulk using a body payload of userId + status.
    // endpoint - PATCH /api/teachers/account
    // body - [{ "userId": 1, "status": "ENABLED" }, { "userId": 2, "status": "DISABLED" }]
    // success response - 200 OK with processed/success/failed counts and per-item results
    // ErrorResponse - { "error": "message" } (400, 500)
    @PatchMapping("/account")
    public ResponseEntity<?> updateTeacherAccountStatusBulk(@RequestBody List<Map<String, Object>> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Request body cannot be empty"));
            }

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;

            for (Map<String, Object> item : requests) {
                Map<String, Object> result = new HashMap<>();
                try {
                    Object userIdObj = item.get("userId");
                    Object statusObj = item.get("status");

                    if (userIdObj == null || statusObj == null) {
                        throw new IllegalArgumentException("Both userId and status are required");
                    }

                    Long userId;
                    try {
                        userId = Long.valueOf(String.valueOf(userIdObj));
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("userId must be a valid number");
                    }

                    ensureTeacherRole(userId);

                    String status = String.valueOf(statusObj).trim().toUpperCase();
                    if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
                        throw new IllegalArgumentException("status must be ENABLED or DISABLED");
                    }

                    if ("ENABLED".equals(status)) {
                        userService.enableUser(userId);
                    } else {
                        userService.disableUser(userId);
                    }

                    result.put("userId", userId);
                    result.put("status", status);
                    result.put("success", true);
                    result.put("message", "Account status updated successfully");
                    successCount++;

                } catch (ResourceNotFoundException | IllegalArgumentException e) {
                    result.put("userId", item.get("userId"));
                    result.put("status", item.get("status"));
                    result.put("success", false);
                    result.put("error", e.getMessage());
                } catch (Exception e) {
                    result.put("userId", item.get("userId"));
                    result.put("status", item.get("status"));
                    result.put("success", false);
                    result.put("error", "Failed to process item: " + e.getMessage());
                }
                results.add(result);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("processed", requests.size());
            response.put("success", successCount);
            response.put("failed", requests.size() - successCount);
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process bulk account status update: " + e.getMessage()));
        }
    }

    private void ensureTeacherRole(Long userId) {
        User user = userService.getUserById(userId);
        if (user.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("User with id " + userId + " is not a teacher");
        }
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            List<TeacherListDTO> teachers = teacherService.getAllTeachers();
            return ResponseEntity.ok(teachers);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            TeacherResponseDTO teacher = teacherService.getTeacherById(id);
            return ResponseEntity.ok(teacher);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An unexpected error occurred: " + e.getMessage()));
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            teacherService.deleteTeacher(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("An unexpected error occurred: " + e.getMessage()));
        }
    }

    // ------------[Get Authenticated Teacher's Full Profile] --------
    // endpoint - GET /api/profile
    // security - only users with TEACHER role can access
    @GetMapping("/profile")
    public ResponseEntity<?> getMyTeacherProfile() {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = (User) userService.loadUserByUsername(username);

            // Role guard – only teachers can use this endpoint
            if (user.getRole() != Role.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: only teachers can access this profile"));
            }

            TeacherProfileResponse response = teacherService.getTeacherProfileByUser(user);
            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Teacher profile details not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load teacher profile"));
        }
    }

    @PatchMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal User authenticatedUser,
            @RequestPart("data") TeacherRegisterRequest request,
            @RequestPart(name = "photograph", required = false) MultipartFile photograph) {
        // NOTE: "document" multipart is NOT accepted here as per requirements.

        try {
            TeacherResponseDTO updated = teacherService.updateTeacherSelf(authenticatedUser, request, photograph);
            return ResponseEntity.ok(updated);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update profile: " + e.getMessage()));
        }
    }

    // Keeping the admin update endpoint with different path or role check if needed, 
    // but the prompt asked specifically for the new endpoint. 
    // The previous implementation used PUT /{id}. I will leave it as is.
    // ==================== GET FILES ====================
    @GetMapping(value = "/get-photo/{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<?> getPhoto(@PathVariable Long id) {
        try {
            byte[] photo = teacherService.getTeacherPhoto(id);
            if (photo == null || photo.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Photo not found"));
            }
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG) // Assuming JPEG, browser will handle PNG too mostly
                .body(photo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to retrieve photo"));
        }
    }

    @GetMapping(value = "/get-document/{id}")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        try {
            byte[] doc = teacherService.getTeacherDocument(id);
            if (doc == null || doc.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Document not found"));
            }
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"teacher_doc_" + id + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(doc);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to retrieve document"));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestPart("data") TeacherRegisterRequest request,
            @RequestPart(name = "photograph", required = false) MultipartFile photograph,
            @RequestPart(name = "document", required = false) MultipartFile document) {
        try {
            TeacherDetail updated = teacherService.updateTeacher(id, request, photograph, document);
            return ResponseEntity.ok(teacherService.toDetailDto(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }


    // Retrieves all students that the authenticated teacher is teaching
    // Why: Based on TeacherCourseAssignment → finds all courses + BCYS the teacher teaches
    //      → then finds all students enrolled in those courses via StudentCourseScore
    // Security: Only TEACHER role (handled in SecurityConfig), ownership validated in service
    @GetMapping("/my-students")
    public ResponseEntity<?> getMyStudents(@AuthenticationPrincipal User authenticatedUser) {

        try {
            TeacherStudentsResponse response = teacherService.getStudentsTaughtByTeacher(authenticatedUser);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve students: " + e.getMessage()));
        }
    }

    // Returns all students enrolled in a specific course assignment (course + BCYS) taught by the authenticated teacher
    // Why: Teacher needs to see only the students for the selected course when creating assessments or grading
    @GetMapping("/courses/{teacherCourseAssignmentId}/students")
    public ResponseEntity<?> getStudentsByCourseAssignment(
            @AuthenticationPrincipal User authenticatedUser,
            @PathVariable Long teacherCourseAssignmentId) {

        try {
            TeacherStudentsResponse response = teacherService.getStudentsByCourseAssignment(
                    authenticatedUser, teacherCourseAssignmentId);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve students: " + e.getMessage()));
        }
    }


    // Retrieves all courses (with batch/semester details) assigned to the authenticated teacher
    // Why: Teacher needs to know which courses they are currently teaching to create assessments or view scores
    // Security: Only TEACHER role reaches here (handled in SecurityConfig)
    @GetMapping("/my-courses")
    public ResponseEntity<?> getMyAssignedCourses(@AuthenticationPrincipal User authenticatedUser) {

        try {
            TeacherCoursesResponse response = teacherService.getAssignedCourses(authenticatedUser);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve assigned courses: " + e.getMessage()));
        }
    }

    // Provides all key information for the teacher's dashboard in a single call
    // Why: Reduces multiple API calls from frontend, gives quick overview
    // Security: Only TEACHER role (handled in SecurityConfig)
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal User authenticatedUser) {

        try {
            TeacherDashboardResponse response = teacherService.getDashboardData(authenticatedUser);
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to load dashboard: " + e.getMessage()));
        }
    }
}