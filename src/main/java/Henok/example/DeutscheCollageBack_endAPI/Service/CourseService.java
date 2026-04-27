package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.CourseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.CourseResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.ClassYear;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Course;
import Henok.example.DeutscheCollageBack_endAPI.Entity.CourseCategory;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Department;
import Henok.example.DeutscheCollageBack_endAPI.Entity.ProgressionSequence;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Semester;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.ClassYearRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.CourseCategoryRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.CourseRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.DepartmentRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.ProgressionSequenceRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.SemesterRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseService {

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

    @Autowired
    private ProgressionSequenceRepository progressionSequenceRepository;

    public Set<String> addCoursesSkipDuplicates(List<CourseDTO> courseDTOs) {
        if (courseDTOs == null || courseDTOs.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> skippedCodes = new HashSet<>();
        List<Course> validCourses = new ArrayList<>();

        for (CourseDTO dto : courseDTOs) {
            if (dto.getCCode() == null || dto.getCCode().trim().isEmpty()) {
                skippedCodes.add("(empty code)");
                continue;
            }

            if (courseRepository.existsBycCode(dto.getCCode())) {
                skippedCodes.add(dto.getCCode());
                continue;
            }

            try {
                Course course = mapToEntity(dto);
                validateCourse(course);
                validatePrerequisites(course);
                validCourses.add(course);
            } catch (Exception e) {
                skippedCodes.add(dto.getCCode() + " (invalid data)");
            }
        }

        if (!validCourses.isEmpty()) {
            courseRepository.saveAll(validCourses);
        }

        return skippedCodes;
    }

    public void addCourse(CourseDTO courseDTO) {
        if (courseDTO == null) {
            throw new IllegalArgumentException("Course DTO cannot be null");
        }

        Course course = mapToEntity(courseDTO);
        System.out.println("After Mapping is done, DTO");
        validateCourse(course);

        if (courseRepository.existsBycCode(course.getCCode())) {
            throw new IllegalArgumentException("Course code already exists: " + course.getCCode());
        }

        validatePrerequisites(course);
        courseRepository.save(course);
    }

    private CourseResponseDTO toResponseDTO(Course course) {
        // Prerequisites are safe (assuming List is never null — if it can be, add null check)
        List<CourseResponseDTO.PrerequisiteDTO> prereqDTOs = (course.getPrerequisites() != null)
                ? course.getPrerequisites().stream()
                .map(prereq -> new CourseResponseDTO.PrerequisiteDTO(
                        prereq.getCID(),           // assuming getId() here (you had getCID() before)
                        prereq.getCCode(),
                        prereq.getCTitle()
                ))
                .collect(Collectors.toList())
                : Collections.emptyList();

        // Null-safe references
        CourseResponseDTO.RefDTO categoryRef = null;
        if (course.getCategory() != null) {
            categoryRef = new CourseResponseDTO.RefDTO(
                    course.getCategory().getCatID(),
                    course.getCategory().getCatName()
            );
        }

        CourseResponseDTO.RefDTO departmentRef = null;
        if (course.getDepartment() != null) {
            departmentRef = new CourseResponseDTO.RefDTO(
                    course.getDepartment().getDptID(),
                    course.getDepartment().getDeptName()
            );
        }

        CourseResponseDTO.RefDTO classYearRef = null;
        if (course.getClassYear() != null) {
            classYearRef = new CourseResponseDTO.RefDTO(
                    course.getClassYear().getId(),
                    course.getClassYear().getClassYear()
            );
        }

        CourseResponseDTO.SemesterRefDTO semesterRef = null;
        if (course.getSemester() != null) {
            semesterRef = new CourseResponseDTO.SemesterRefDTO(
                    course.getSemester().getAcademicPeriodCode(),
                    course.getSemester().getAcademicPeriod()
            );
        }

        return new CourseResponseDTO(
                course.getCID(),                // assuming getId() is the correct method now
                course.getCTitle(),
                course.getCCode(),
                course.getTheoryHrs(),
                course.getLabHrs(),
            course.isPassFail(),
                categoryRef,
                departmentRef,
                classYearRef,
                semesterRef,
                prereqDTOs
        );
    }

    /**
     * Core method: Returns filtered list of Course entities.
     * All parameters are optional (null = no filter on that field).
     * This is the ONLY place where filtering logic lives.
     * All other get-methods should call this one.
     */
    private List<Course> getCoursesFiltered(
            Long departmentId,
            String semesterId,
            Long classYearId,
            Long categoryId) {

        // Build dynamic specification
        Specification<Course> spec = Specification.where(null);

        if (departmentId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("department").get("dptID"), departmentId));
        }

        if (semesterId != null && !semesterId.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("semester").get("academicPeriodCode"), semesterId));
        }

        if (classYearId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("classYear").get("id"), classYearId));
        }

        if (categoryId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("category").get("catID"), categoryId));
        }

        List<Course> courses = courseRepository.findAll(spec);
        courses.sort(Comparator
            .comparingInt(this::getProgressionSequenceSortKey)
            .thenComparing(course -> course.getClassYear() == null ? null : course.getClassYear().getId(), Comparator.nullsLast(Long::compareTo))
            .thenComparing(course -> course.getSemester() == null ? null : course.getSemester().getAcademicPeriodCode(), Comparator.nullsLast(String::compareTo))
            .thenComparing(Course::getCCode, Comparator.nullsLast(String::compareTo)));

        return courses;
    }

    /**
     * Original filtered method - kept as-is, now delegates to core method
     */
    public List<CourseResponseDTO> getAllCoursesFiltered(
            Long departmentId, String semesterId, Long classYearId, Long categoryId) {

        List<Course> courses = getCoursesFiltered(departmentId, semesterId, classYearId, categoryId);

        // Optional: you can decide whether to throw or return empty
        // if (courses.isEmpty()) {
        //     throw new ResourceNotFoundException("No courses found matching the filters");
        // }

        return courses.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Minimal list (Map version) - now uses the core filtering method
     */
    public List<Map<String, Object>> getCoursesMinimalListFiltered(
            Long departmentId, Long classYearId, Long semesterId) {

        // Note: this method doesn't support categoryId filter (as per your original signature)
        // If you want to add it later → just pass null for categoryId
        List<Course> courses = getCoursesFiltered(
                departmentId,
                null,               // no semesterId in original minimal method
                classYearId,
                null                // no categoryId in original minimal method
        );

        return courses.stream()
                .map(course -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", course.getCID());          // ← assuming getId() is correct
                    map.put("cCode", course.getCCode());
                    map.put("cTitle", course.getCTitle());
                    map.put("department", course.getDepartment().getDeptName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get single course DTO by ID - now consistent
     */
    public CourseResponseDTO getCourseDTOById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + id));
        return toResponseDTO(course);
    }

    /**
     * Get single raw entity by ID (used internally or by other services)
     */
    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + id));
    }

    /**
     * Get all courses for a specific department - now delegates to core
     */
    public List<CourseResponseDTO> getCoursesByDepartment(Long departmentId) {
        List<Course> courses = getCoursesFiltered(
                departmentId,
                null, null, null   // no other filters
        );

        if (courses.isEmpty()) {
            throw new ResourceNotFoundException("No courses found for department with id: " + departmentId);
        }

        return courses.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public void updateCourse(Long id, CourseDTO courseDTO) {
        if (courseDTO == null) {
            throw new IllegalArgumentException("Course DTO cannot be null");
        }

        Course existingCourse = getCourseById(id);

        if (courseDTO.getCTitle() != null) {
            existingCourse.setCTitle(courseDTO.getCTitle());
        }

        String newCCode = courseDTO.getCCode();
        if (newCCode != null && !existingCourse.getCCode().equals(newCCode) && courseRepository.existsBycCode(newCCode)) {
            throw new IllegalArgumentException("Course code already exists: " + newCCode);
        }
        if (newCCode != null && !newCCode.isEmpty()) {
            existingCourse.setCCode(newCCode);
        }

        if (courseDTO.getTheoryHrs() != null) {
            existingCourse.setTheoryHrs(courseDTO.getTheoryHrs());
        }

        if (courseDTO.getLabHrs() != null) {
            existingCourse.setLabHrs(courseDTO.getLabHrs());
        }

        if (courseDTO.getIsPassFail() != null) {
            existingCourse.setPassFail(courseDTO.getIsPassFail());
        }

        if (courseDTO.getCCatagoryID() != null) {
            CourseCategory category = courseCategoryRepository.findById(courseDTO.getCCatagoryID())
                    .orElseThrow(() -> new ResourceNotFoundException("Course category not found with id: " + courseDTO.getCCatagoryID()));
            existingCourse.setCategory(category);
        }

        if (courseDTO.getDepartmentID() != null) {
            Department department = departmentRepository.findById(courseDTO.getDepartmentID())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + courseDTO.getDepartmentID()));
            existingCourse.setDepartment(department);
        }

        if (courseDTO.getClassYearID() != null) {
            ClassYear classYear = classYearRepository.findById(courseDTO.getClassYearID())
                    .orElseThrow(() -> new ResourceNotFoundException("Class year not found with id: " + courseDTO.getClassYearID()));
            existingCourse.setClassYear(classYear);
        }

        if (courseDTO.getSemesterID() != null && !courseDTO.getSemesterID().isEmpty()) {
            Semester semester = semesterRepository.findById(courseDTO.getSemesterID())
                    .orElseThrow(() -> new ResourceNotFoundException("Semester not found with id: " + courseDTO.getSemesterID()));
            existingCourse.setSemester(semester);
        }

        if (courseDTO.getPrerequisiteIds() != null) {
            Set<Course> prerequisites = courseDTO.getPrerequisiteIds().stream()
                    .map(prereqId -> courseRepository.findById(prereqId)
                            .orElseThrow(() -> new ResourceNotFoundException("Prerequisite course not found with id: " + prereqId)))
                    .collect(Collectors.toSet());
            validatePrerequisites(id, prerequisites);
            existingCourse.setPrerequisites(prerequisites);
        }

        courseRepository.save(existingCourse);
    }

    public void deleteCourse(Long id) {
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course not found with id: " + id);
        }
        courseRepository.deleteById(id);
    }

    public List<Course> getPrerequisitesByCourseId(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        Set<Course> prerequisites = course.getPrerequisites();
        if (prerequisites.isEmpty()) {
            throw new ResourceNotFoundException("No prerequisites found for course with id: " + courseId);
        }
        return new ArrayList<>(prerequisites);
    }

    public void addPrerequisite(Long courseId, Long prerequisiteId) {
        Course course = getCourseById(courseId);
        Course prerequisite = getCourseById(prerequisiteId);

        if (courseId.equals(prerequisiteId)) {
            throw new IllegalArgumentException("A course cannot be its own prerequisite");
        }

        if (hasCircularDependency(course, prerequisite)) {
            throw new IllegalArgumentException("Adding this prerequisite would create a circular dependency");
        }

        course.getPrerequisites().add(prerequisite);
        courseRepository.save(course);
    }

    public void removePrerequisite(Long courseId, Long prerequisiteId) {
        Course course = getCourseById(courseId);
        Course prerequisite = getCourseById(prerequisiteId);

        if (!course.getPrerequisites().removeIf(prereq -> prereq.getCID().equals(prerequisiteId))) {
            throw new ResourceNotFoundException("Prerequisite with id " + prerequisiteId + " not found for course " + courseId);
        }

        courseRepository.save(course);
    }

    // ==================== Helper Functions ==========================================================================================
    private Course mapToEntity(CourseDTO dto) {
        CourseCategory category = courseCategoryRepository.findById(dto.getCCatagoryID())
                .orElseThrow(() -> new ResourceNotFoundException("Course category not found with id: " + dto.getCCatagoryID()));
        Department department = departmentRepository.findById(dto.getDepartmentID())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + dto.getDepartmentID()));
        ClassYear classYear = classYearRepository.findById(dto.getClassYearID())
                .orElseThrow(() -> new ResourceNotFoundException("Class year not found with id: " + dto.getClassYearID()));
        Semester semester = semesterRepository.findById(dto.getSemesterID())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found with id: " + dto.getSemesterID()));

        Set<Course> prerequisites = dto.getPrerequisiteIds() != null
                ? dto.getPrerequisiteIds().stream()
                .map(id -> courseRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Prerequisite course not found with id: " + id)))
                .collect(Collectors.toSet())
                : new HashSet<>();

        return new Course(null, dto.getCTitle(), dto.getCCode(),
                dto.getTheoryHrs() != null ? dto.getTheoryHrs() : 0,
                dto.getLabHrs() != null ? dto.getLabHrs() : 0,
            category, department, prerequisites, classYear, semester,
            dto.getIsPassFail() != null ? dto.getIsPassFail() : false);
    }

    private void validateCourse(Course course) {
        if (course.getCTitle() == null || course.getCTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Course title cannot be null or empty");
        }
        if (course.getCCode() == null || course.getCCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Course code cannot be null or empty");
        }
        if (course.getTheoryHrs() == null || course.getTheoryHrs() < 0) {
            throw new IllegalArgumentException("Theory hours must be a non-negative integer");
        }
        if (course.getLabHrs() == null || course.getLabHrs() < 0) {
            throw new IllegalArgumentException("Lab hours must be a non-negative integer");
        }
        if (course.getCategory() == null) {
            throw new IllegalArgumentException("Course category cannot be null");
        }
        if (course.getDepartment() == null) {
            throw new IllegalArgumentException("Department cannot be null");
        }
        if (course.getClassYear() == null) {
            throw new IllegalArgumentException("Class year cannot be null");
        }
        if (course.getSemester() == null) {
            throw new IllegalArgumentException("Semester cannot be null");
        }
    }

    private void validatePrerequisites(Course course) {
        if (course.getCID() != null && course.getPrerequisites().contains(course)) {
            throw new IllegalArgumentException("A course cannot be its own prerequisite");
        }
        if (hasCircularDependency(course, course.getPrerequisites())) {
            throw new IllegalArgumentException("Prerequisites would create a circular dependency");
        }
    }

    private void validatePrerequisites(Long courseId, Set<Course> prerequisites) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        if (prerequisites.contains(course)) {
            throw new IllegalArgumentException("A course cannot be its own prerequisite");
        }
        if (hasCircularDependency(course, prerequisites)) {
            throw new IllegalArgumentException("Prerequisites would create a circular dependency");
        }
    }

    private boolean hasCircularDependency(Course course, Course prerequisite) {
        if (course.getCID() == null || prerequisite.getCID() == null) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        return checkCircularDependency(prerequisite, course.getCID(), visited);
    }

    private boolean hasCircularDependency(Course course, Set<Course> prerequisites) {
        if (course.getCID() == null) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        for (Course prereq : prerequisites) {
            if (checkCircularDependency(prereq, course.getCID(), visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkCircularDependency(Course current, Long targetId, Set<Long> visited) {
        if (current.getCID() == null || visited.contains(current.getCID())) {
            return false;
        }
        if (current.getCID().equals(targetId)) {
            return true;
        }
        visited.add(current.getCID());
        for (Course prereq : current.getPrerequisites()) {
            if (checkCircularDependency(prereq, targetId, visited)) {
                return true;
            }
        }
        return false;
    }

    private int getProgressionSequenceSortKey(Course course) {
        Department department = course.getDepartment();
        ClassYear classYear = course.getClassYear();
        Semester semester = course.getSemester();

        if (classYear == null || semester == null) {
            return Integer.MAX_VALUE;
        }

        if (department != null) {
            return progressionSequenceRepository
                    .findByDepartmentAndClassYearAndSemester(department, classYear, semester)
                    .map(ProgressionSequence::getSequenceNumber)
                    .orElseGet(() -> progressionSequenceRepository
                            .findByDepartmentIsNullAndClassYearAndSemester(classYear, semester)
                            .map(ProgressionSequence::getSequenceNumber)
                            .orElse(Integer.MAX_VALUE));
        }

        return progressionSequenceRepository
                .findByDepartmentIsNullAndClassYearAndSemester(classYear, semester)
                .map(ProgressionSequence::getSequenceNumber)
                .orElse(Integer.MAX_VALUE);
    }
}