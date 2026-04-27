package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.AssessmentScoresResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.DepartmentHead.DepartmentHeadDashboardDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.DepartmentHead.DepartmentTeacherDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Heads.DepartmentHeadResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Heads.DepartmentHeadUpdateRequest;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Assessment;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.AssessmentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Service.DepartmentHeadService;
import Henok.example.DeutscheCollageBack_endAPI.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/department-heads")
@RequiredArgsConstructor
public class DepartmentHeadController {

    private final DepartmentHeadService departmentHeadService;
    private final UserService userService;

    // -----------[Bulk Update Department Head Account Status]------------------
    // description - Updates account status in bulk using a body payload of userId + status.
    // endpoint - PATCH /api/department-heads/account
    // body - [{ "userId": 1, "status": "ENABLED" }, { "userId": 2, "status": "DISABLED" }]
    // success response - 200 OK with processed/success/failed counts and per-item results
    // ErrorResponse - { "error": "message" } (400, 500)
    @PatchMapping("/account")
    public ResponseEntity<?> updateDepartmentHeadAccountStatusBulk(@RequestBody List<Map<String, Object>> requests) {
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

    // -----------[Update Department Head]------------------
    // description - Updates only the fields provided in the request (partial update).
    //               Only non-null/non-empty values are applied. Files can be updated if provided.
    // endpoint - PATCH /api/department-heads/{id}
    // body - DepartmentHeadUpdateRequest (JSON) + optional multipart files: photo, documents
    // success response - 200 OK with updated DepartmentHeadResponse DTO
    // ErrorResponse - { "error": "message" } (400, 404, 500)
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateDepartmentHead(
            @PathVariable Long id,
            @Valid @RequestPart("data") DepartmentHeadUpdateRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "documents", required = false) MultipartFile documents) {

        try {
            request.setPhoto(photo);
            request.setDocuments(documents);

            DepartmentHeadResponse updated = departmentHeadService.updateDepartmentHead(id, request);

            return ResponseEntity.ok(updated);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (BadRequestException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update department head: " + e.getMessage()));
        }
    }

    // ==================== GET FILES ====================
    @GetMapping(value = "/get-photo/{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<?> getPhoto(@PathVariable Long id) {
        try {
            byte[] photo = departmentHeadService.getDepartmentHeadPhoto(id);
            if (photo == null || photo.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Photo not found"));
            }
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(photo);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve photo"));
        }
    }

    @GetMapping(value = "/get-document/{id}")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        try {
            byte[] doc = departmentHeadService.getDepartmentHeadDocument(id);
            if (doc == null || doc.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Document not found"));
            }
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dept_head_doc_" + id + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(doc);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve document"));
        }
    }

    // -----------[Update My Profile]------------------
    // description - Updates the authenticated department head's profile.
    //               Restricted fields (documents, department, etc.) are ignored.
    // endpoint - PATCH /api/department-heads/update
    @PatchMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestPart("data") DepartmentHeadUpdateRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        
        try {
            DepartmentHeadResponse updated = departmentHeadService.updateDepartmentHeadSelf(authenticatedUser, request, photo);
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update profile: " + e.getMessage()));
        }
    }

    // -----------[Get All Department Heads]------------------
    // description - Retrieves all department heads with simplified response including username and foreign key names.
    // endpoint - GET /api/department-heads
    // body - none
    // success response - 200 OK with List<DepartmentHeadResponse>
    // ErrorResponse - { "error": "message" } (500 only)
    @GetMapping
    public ResponseEntity<?> getAllDepartmentHeads() {
        try {
            List<DepartmentHeadResponse> list = departmentHeadService.getAllDepartmentHeads();
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve department heads"));
        }
    }

    // -----------[Get Department Head By ID]------------------
    // description - Retrieves a single department head by ID including username and foreign key names.
    // endpoint - GET /api/department-heads/{id}
    // body - none
    // success response - 200 OK with DepartmentHeadResponse
    // ErrorResponse - { "error": "message" } (404, 500)
    @GetMapping("/{id}")
    public ResponseEntity<?> getDepartmentHeadById(@PathVariable Long id) {
        try {
            DepartmentHeadResponse response = departmentHeadService.getDepartmentHeadById(id);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve department head"));
        }
    }


    // -----------[Get My Profile]------------------
    // description -
    // Returns the authenticated department head's own profile.
    // Excludes the actual document bytes – only shows hasDocument: true/false.
    // Residence places shown as {id, name}.
    // Department shown as {id, name, modality, level}.
    // Accessible only by the department head himself (or higher roles if needed).
    // endpoint -
    // GET /api/department-heads/profile
    // body - none
    // success response - 200 OK with profile JSON (see example below)
    // ErrorResponse - { "error": "message" } (401, 404, 500)
    @GetMapping("/profile")
    public ResponseEntity<?> getMyProfile() {
        try {
            // Get current authenticated user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();

            Map<String, Object> profile = departmentHeadService.getMyProfile(currentUser);

            return ResponseEntity.ok(profile);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve profile: " + e.getMessage()));
        }
    }


    // -----------[Get My Photo]------------------
    // description -
    // Returns the authenticated department head's photo as binary data.
    // Requires JWT token with ROLE_DEPARTMENT_HEAD.
    // endpoint -
    // GET /api/department-heads/profile/photo
    // body - none
    // success response -
    // Status: 200 OK
    // Content-Type: image/jpeg (or actual mime type)
    // Raw binary photo data
    // If no photo: 204 No Content
    // ErrorResponse -
    // { "error": "message" } (404 if profile not found, 500 unexpected)
    @GetMapping("/profile/photo")
    public ResponseEntity<?> getMyPhoto(@AuthenticationPrincipal User currentUser) {
        try {
            byte[] photo = departmentHeadService.getMyPhoto(currentUser);

            if (photo == null || photo.length == 0) {
                return ResponseEntity.noContent().build(); // 204 No Content
            }

            return ResponseEntity
                    .ok()
                    .contentType(MediaType.IMAGE_JPEG) // adjust if you store mime type
                    .body(photo);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve photo"));
        }
    }

    // -----------[Get My Document]------------------
    // description -
    // Returns the authenticated department head's document (PDF/other file) as binary.
    // Requires JWT token with ROLE_DEPARTMENT_HEAD.
    // endpoint -
    // GET /api/department-heads/profile/document
    // body - none
    // success response -
    // Status: 200 OK
    // Content-Type: application/pdf (or application/octet-stream)
    // Raw binary document data + header Content-Disposition for download
    // If no document: 204 No Content
    // ErrorResponse -
    // { "error": "message" } (404 if profile not found, 500 unexpected)
    @GetMapping("/profile/document")
    public ResponseEntity<?> getMyDocument(@AuthenticationPrincipal User currentUser) {
        try {
            byte[] document = departmentHeadService.getMyDocument(currentUser);

            if (document == null || document.length == 0) {
                return ResponseEntity.noContent().build(); // 204 No Content
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition
                    .attachment()
                    .filename("department_head_documents.pdf")
                    .build());

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(document);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve document"));
        }
    }

    // Allows only users with DEAN role to change the department of an existing department head.
    // Checks that the new department does not already have a head (uniqueness).
    @PatchMapping("/{id}/reassign-department")
    public ResponseEntity<?> reassignDepartment(
            @PathVariable Long id,
            @RequestBody @Valid Map<String, Long> request) {

        try {
            Long newDepartmentId = request.get("newDepartmentId");
            if (newDepartmentId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "newDepartmentId is required"));
            }

            Map<String, Object> result = departmentHeadService.reassignDepartment(id, newDepartmentId);

            return ResponseEntity.ok(result);

        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reassign department"));
        }
    }

    // -----------[Get Department Head Dashboard]------------------
    // description - Returns comprehensive dashboard information for the authenticated department head including department info, summary statistics, pending approvals, instructors, and students.
    // endpoint - GET /api/department-heads/dashboard?startDate={date}&endDate={date}
    // body - N/A (uses query parameters for date range)
    // success response - 200 OK with DepartmentHeadDashboardDTO
    // ErrorResponse - { "error": "message" } (404, 500)
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(
            @AuthenticationPrincipal User authenticatedUser,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // Default to current academic year if not provided (you can adjust this logic)
            java.time.LocalDate start = startDate != null ? java.time.LocalDate.parse(startDate) : java.time.LocalDate.now().minusMonths(6);
            java.time.LocalDate end = endDate != null ? java.time.LocalDate.parse(endDate) : java.time.LocalDate.now();
            
            DepartmentHeadDashboardDTO dashboard = departmentHeadService.getDashboard(authenticatedUser, start, end);
            return ResponseEntity.ok(dashboard);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve dashboard: " + e.getMessage()));
        }
    }

    // -----------[Get Department Teachers]------------------
    // description - Returns all teachers assigned to courses offered by the department.
    // endpoint - GET /api/department-heads/teachers
    // body - N/A
    // success response - 200 OK with List<DepartmentTeacherDTO>
    // ErrorResponse - { "error": "message" } (404, 500)
    @GetMapping("/teachers")
    public ResponseEntity<?> getDepartmentTeachers(@AuthenticationPrincipal User authenticatedUser) {
        try {
            List<DepartmentTeacherDTO> teachers = departmentHeadService.getDepartmentTeachers(authenticatedUser);
            return ResponseEntity.ok(teachers);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve teachers: " + e.getMessage()));
        }
    }

    // -----------[Get My Department Courses]------------------
    // description -
    // Returns the list of all courses belonging to the department of the currently logged-in department head.
    // Requires valid JWT token with ROLE_DEPARTMENT_HEAD.
    // Only courses of the authenticated head's department are returned.
    @GetMapping("/my-courses")
    public ResponseEntity<?> getMyDepartmentCourses(@AuthenticationPrincipal User currentUser) {
        try {
            List<Map<String, Object>> courses = departmentHeadService.getMyDepartmentCourses(currentUser);

            return ResponseEntity.ok(courses);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            System.out.println("Error in getMyDepartmentCourses: " + e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve courses"));
        }
    }

    // Returns all students enrolled in the department of the currently logged-in department head.
    // Also includes department-level statistics.
    // Requires valid JWT token with ROLE_DEPARTMENT_HEAD.
    @GetMapping("/my-students")
    public ResponseEntity<?> getMyDepartmentStudents(@AuthenticationPrincipal User currentUser) {
        try {
            Map<String, Object> response = departmentHeadService.getMyDepartmentStudents(currentUser);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve students"));
        }
    }

    // -----------[Get Approved Assessment Scores]------------------
    // description - Returns AssessmentScoresResponse for all assessments approved by teachers that are assigned to courses in the department.
    // endpoint - GET /api/department-heads/assessments/scores
    // body - N/A
    // success response - 200 OK with List<AssessmentScoresResponse>
    // ErrorResponse - { "error": "message" } (404, 500)
    @GetMapping("/assessments/scores")
    public ResponseEntity<?> getApprovedAssessmentScores(@AuthenticationPrincipal User authenticatedUser) {
        try {
            List<AssessmentScoresResponse> responses = departmentHeadService.getApprovedAssessmentScores(authenticatedUser);
            return ResponseEntity.ok(responses);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve assessment scores: " + e.getMessage()));
        }
    }

    // Updated Controller method
    @PutMapping("/assignments/{teacherCourseAssignmentId}/approve-all")
    public ResponseEntity<?> approveOrRejectAllAssessmentsInAssignment(
            @AuthenticationPrincipal User authenticatedUser,
            @PathVariable Long teacherCourseAssignmentId,
            @RequestParam AssessmentStatus status) {

        try {
            // Validate status
            if (status != AssessmentStatus.ACCEPTED && status != AssessmentStatus.REJECTED) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Status must be ACCEPTED or REJECTED"));
            }

            List<Assessment> updated = departmentHeadService.approveOrRejectAllAssessmentsInAssignment(
                    authenticatedUser, teacherCourseAssignmentId, status);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "All assessments processed successfully");
            response.put("count", updated.size());
            response.put("statusApplied", status.name());

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process assessments: " + e.getMessage()));
        }
    }
}
