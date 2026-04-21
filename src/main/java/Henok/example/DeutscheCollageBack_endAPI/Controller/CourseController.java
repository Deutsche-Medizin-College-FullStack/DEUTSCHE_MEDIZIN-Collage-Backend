package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.CourseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.CourseResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Course;
import Henok.example.DeutscheCollageBack_endAPI.Error.ErrorResponse;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.CourseRepo;
import Henok.example.DeutscheCollageBack_endAPI.Service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepo courseRepo;

    // TEMPORARY ONE-TIME HELPER: remove this method and its caller after use.
    private void setAllCoursesPassFailFalseOneTime() {
        courseRepo.backfillNullPassFailToFalse();
    }

    @PostMapping
    public ResponseEntity<?> addCourses(@RequestBody List<CourseDTO> courseDTOs) {
        try {
            Set<String> skippedCodes = courseService.addCoursesSkipDuplicates(courseDTOs);

            if (skippedCodes.isEmpty()) {
                return ResponseEntity.ok("All courses added successfully");
            } else if (skippedCodes.size() == courseDTOs.size()) {
                return ResponseEntity.ok("No courses were added. All had duplicate codes: " + skippedCodes);
            } else {
                return ResponseEntity.ok(
                        "Courses added successfully. Skipped duplicate course codes: " + skippedCodes
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to process courses: " + e.getMessage()));
        }
    }

    @PostMapping("/single")
    public ResponseEntity<?> addCourse(@RequestBody CourseDTO courseDTO) {
        try {
            System.out.println("Before anything checking DTO : " + courseDTO);
            courseService.addCourse(courseDTO);
            return ResponseEntity.ok("Course added successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add course: " + e.getMessage()));
        }
    }

    /**
     * Get all courses with optional filtering by department, semester, class year, and category.
     * All parameters are optional. If none provided → returns all courses.
     * Returns CourseResponseDTO list (minimal safe representation)
     */
    @GetMapping
    public ResponseEntity<?> getAllCourses(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String semesterId,      // String because academicPeriodCode is String
            @RequestParam(required = false) Long classYearId,
            @RequestParam(required = false) Long categoryId) {

        try {
            List<CourseResponseDTO> courses = courseService.getAllCoursesFiltered(
                    departmentId, semesterId, classYearId, categoryId);

            if (courses.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            return ResponseEntity.ok(courses);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve courses: " + e.getMessage()));
        }
    }

    /**
     * Returns minimal list of courses (id, cCode, cTitle only) with optional filtering
     * by department, class year and/or semester.
     * All parameters are optional. If none provided → returns all courses.
     */
    @GetMapping("/list")
    public ResponseEntity<?> getCoursesMinimalList(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long classYearId,
            @RequestParam(required = false) Long semesterId) {

        try {
            // TEMPORARY ONE-TIME CALL: remove this line after your one-time update.
            setAllCoursesPassFailFalseOneTime();

            List<Map<String, Object>> courseList = courseService.getCoursesMinimalListFiltered(
                    departmentId, classYearId, semesterId);

            if (courseList.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            return ResponseEntity.ok(courseList);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve course list: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable Long id) {
        try {
            CourseResponseDTO course = courseService.getCourseDTOById(id);
            return ResponseEntity.ok(course);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve course: " + e.getMessage()));
        }
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<?> getCoursesByDepartment(@PathVariable Long departmentId) {
        try {
            List<CourseResponseDTO> courses = courseService.getCoursesByDepartment(departmentId);
            return ResponseEntity.ok(courses);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve courses for department: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @RequestBody CourseDTO courseDTO) {
        try {
            courseService.updateCourse(id, courseDTO);
            return ResponseEntity.ok("Course updated successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update course: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        try {
            courseService.deleteCourse(id);
            return ResponseEntity.ok("Course deleted successfully");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete course: " + e.getMessage()));
        }
    }

    @PostMapping("/{courseId}/prerequisites/{prerequisiteId}")
    public ResponseEntity<?> addPrerequisite(@PathVariable Long courseId, @PathVariable Long prerequisiteId) {
        try {
            courseService.addPrerequisite(courseId, prerequisiteId);
            return ResponseEntity.ok("Prerequisite added successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add prerequisite: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{courseId}/prerequisites/{prerequisiteId}")
    public ResponseEntity<?> removePrerequisite(@PathVariable Long courseId, @PathVariable Long prerequisiteId) {
        try {
            courseService.removePrerequisite(courseId, prerequisiteId);
            return ResponseEntity.ok("Prerequisite removed successfully");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove prerequisite: " + e.getMessage()));
        }
    }
}