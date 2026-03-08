package Henok.example.DeutscheCollageBack_endAPI.migration.Service;

import Henok.example.DeutscheCollageBack_endAPI.Entity.ClassYear;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Course;
import Henok.example.DeutscheCollageBack_endAPI.Entity.CourseCategory;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Department;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Semester;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.ClassYearRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.CourseCategoryRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.CourseRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.DepartmentRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.SemesterRepo;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.BulkImportCourseResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.CourseCreateDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// CourseService.java
// Service handling bulk course import with proper validation and prerequisite resolution.
@Service
public class CourseMigrationService {

    @Autowired
    private CourseRepo courseRepository;

    @Autowired
    private CourseCategoryRepo courseCategoryRepository;

    @Autowired
    private DepartmentRepo departmentRepository;

    @Autowired
    private ClassYearRepository classYearRepository;

    @Autowired
    private SemesterRepo semesterRepository;

    /**
     * Bulk imports courses with partial success.
     * Uniqueness rule: (cCode + department) must be unique.
     * If any individual course fails, it is skipped, logged via System.out.println,
     * and recorded in failedCourses as "Title(Code)".
     */
    @Transactional
    public BulkImportCourseResponseDTO bulkImport(List<CourseCreateDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new BadRequestException("Import data cannot be empty");
        }

        BulkImportCourseResponseDTO response = new BulkImportCourseResponseDTO();
        List<Course> successfulCourses = new ArrayList<>();
        List<String> failedCourses = new ArrayList<>();

        // Track processed (code, departmentId) pairs in current batch to prevent intra-batch duplicates
        Set<String> processedKeys = new HashSet<>();

        for (CourseCreateDTO dto : dtos) {
            String title = dto.getTitle() != null ? dto.getTitle().trim() : "Unknown";
            String code = dto.getCode() != null ? dto.getCode().trim() : "";

            try {
                // Basic required field validation
                if (dto.getTitle() == null || title.isEmpty() ||
                        dto.getCode() == null || code.isEmpty() ||
                        dto.getCategoryId() == null) {
                    throw new BadRequestException("Title, code and categoryId are required");
                }

                Long departmentId = dto.getDepartmentId(); // can be null

                // Unique key for (code, department) combination
                String uniquenessKey = code + "|" + (departmentId == null ? "null" : departmentId);

                // Check intra-batch duplicate
                if (processedKeys.contains(uniquenessKey)) {
                    System.out.println("======================");
                    System.out.println(title + "(" + code + "), Duplicate in import batch");
                    System.out.println("======================");
                    failedCourses.add(title + "(" + code + ")");
                    continue;
                }

                // Check database for existing (code + department)
                if (courseRepository.existsByCCodeAndDepartmentId(code, departmentId)) {
                    System.out.println("======================");
                    System.out.println(title + "(" + code + "), Already exists in database with same department");
                    System.out.println("======================");
                    failedCourses.add(title + "(" + code + ")");
                    continue;
                }

                Course course = new Course();
                course.setCTitle(title);
                course.setCCode(code);
                course.setTheoryHrs(dto.getTheoryHrs() != null ? dto.getTheoryHrs() : 0);
                course.setLabHrs(dto.getLabHrs() != null ? dto.getLabHrs() : 0);

                // Required: Category
                CourseCategory category = courseCategoryRepository.findById(dto.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + dto.getCategoryId()));
                course.setCategory(category);

                // Optional: Department
                if (departmentId != null) {
                    Department dept = departmentRepository.findById(departmentId)
                            .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));
                    course.setDepartment(dept);
                }

                // Optional: ClassYear
                if (dto.getClassYearId() != null) {
                    ClassYear cy = classYearRepository.findById(dto.getClassYearId())
                            .orElseThrow(() -> new ResourceNotFoundException("ClassYear not found: " + dto.getClassYearId()));
                    course.setClassYear(cy);
                }

                // Optional: Semester
                if (dto.getSemesterId() != null && !dto.getSemesterId().trim().isEmpty()) {
                    Semester sem = semesterRepository.findById(dto.getSemesterId())
                            .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + dto.getSemesterId()));
                    course.setSemester(sem);
                }

                course.setPrerequisites(new HashSet<>());

                successfulCourses.add(course);
                processedKeys.add(uniquenessKey);

            } catch (Exception e) {
                // Log any exception with the required format
                System.out.println("======================");
                System.out.println(title + "(" + code + "), " + e.getMessage());
                System.out.println("======================");
                failedCourses.add(title + "(" + code + ")");
            }
        }

        // Save successful courses
        if (!successfulCourses.isEmpty()) {
            courseRepository.saveAll(successfulCourses);

            // Resolve prerequisites for successful courses
            for (Course course : successfulCourses) {
                CourseCreateDTO originalDto = dtos.stream()
                        .filter(d -> d.getCode().equals(course.getCCode()))
                        .findFirst()
                        .orElse(null);

                if (originalDto != null && originalDto.getPrerequisiteCodes() != null) {
                    Set<Course> prereqs = new HashSet<>();
                    for (String prereqCode : originalDto.getPrerequisiteCodes()) {
                        Optional<Course> prereqInBatch = successfulCourses.stream()
                                .filter(c -> c.getCCode().equals(prereqCode))
                                .findFirst();

                        Course prereq = prereqInBatch.orElse(null);

                        if (prereq == null) {
                            prereq = courseRepository.findBycCode(prereqCode).orElse(null);
                        }

                        if (prereq != null) {
                            prereqs.add(prereq);
                        }
                    }
                    course.setPrerequisites(prereqs);
                }
            }

            courseRepository.saveAll(successfulCourses);
        }

        response.setNumberOfImportedCourses(successfulCourses.size());
        response.setFailedCourses(failedCourses);
        return response;
    }
}
