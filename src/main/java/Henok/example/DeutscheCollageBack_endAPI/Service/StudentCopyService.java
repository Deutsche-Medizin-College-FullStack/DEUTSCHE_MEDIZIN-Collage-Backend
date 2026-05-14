package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.CourseGradeDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.SimplifiedStudentCopyDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyOptions;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.AcademicYear;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.ProgramLevel;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Semester;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.AcademicYearRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.SemesterRepo;
import Henok.example.DeutscheCollageBack_endAPI.Service.Utility.AcademicYearUtilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentCopyService {

    @Autowired
    private StudentDetailsRepository studentDetailsRepository;
    @Autowired
    private BatchClassYearSemesterRepo batchClassYearSemesterRepo;
    @Autowired
    private DepartmentBCYSRepository departmentBCYSRepository;
    @Autowired
    private ClassYearRepository classYearRepository;
    @Autowired
    private SemesterRepo semesterRepo;
    @Autowired
    private ProgressionSequenceRepository progressionSequenceRepository;
    @Autowired
    private StudentCourseScoreRepo studentCourseScoreRepo;
    @Autowired
    private GradingSystemService gradingSystemService;
    @Autowired
    private AcademicYearRepo academicYearRepo;
    @Autowired
    private AcademicYearUtilityService academicYearUtilityService;


    // Constants for grade letter suffixes based on course source
    private static final String SUFFIX_INTERNAL = "";          // sourceId = 1 (from within the school)
    private static final String SUFFIX_REPEAT    = "**";       // sourceId = 2 (repeated course)
    private static final String SUFFIX_EXTERNAL  = "*";        // sourceId = 3 (from outside the school)

    private static final double MINIMUM_PASSING_GPA = 2.0; // Minimum GPA to pass a semester
    private static final double MINIMUM_PASSFAIL_SCORE = 50.0; // Minimum score to pass a course (for Pass/Fail logic) 



    /**
     * Generates a student copy for a specific classyear and semester.
     * 
     * @param request The request containing studentId, classYearId, and semesterId
     * @return StudentCopyDTO containing all student information and course grades
     * @throws ResourceNotFoundException if student, classyear, semester, or batch-class-year-semester not found
     */
    // @Transactional(readOnly = true)
    // public StudentCopyDTO generateStudentCopy(StudentCopyRequestDTO request) {
    //     // System.out.println("Generating a full student Copy for student: " + request);

    //     // 1. Get student
    //     StudentDetails student = studentDetailsRepository.findById(request.getStudentId())
    //             .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + request.getStudentId()));

    //     // 2. Get requested classYear and semester
    //     ClassYear classYear = classYearRepository.findById(request.getClassYearId())
    //             .orElseThrow(() -> new ResourceNotFoundException("ClassYear not found with id: " + request.getClassYearId()));

    //     Semester semester = semesterRepo.findById(request.getSemesterId())
    //             .orElseThrow(() -> new ResourceNotFoundException("Semester not found with id: " + request.getSemesterId()));


    //     // 3. CRITICAL FIX: Find ALL historical BCYS for the requested classYear + semester
    //     //    This handles cases where student repeats the same year/semester in different batches
    //     List<BatchClassYearSemester> historicalBCYSList = studentCourseScoreRepo
    //             .findByStudentAndIsReleasedTrue(student.getUser()).stream()
    //             .map(StudentCourseScore::getBatchClassYearSemester)
    //             .filter(bcys ->
    //                     bcys.getClassYear().getId().equals(classYear.getId()) &&
    //                     bcys.getSemester().getAcademicPeriodCode().equals(semester.getAcademicPeriodCode())
    //             )
    //             .distinct()                    // remove duplicate BCYS if any
    //             .toList();
    //     // System.out.println("\tFound " + historicalBCYSList.size() + " historical BCYS records for student in ClassYear " + classYear.getClassYear() + " and Semester " + semester.getAcademicPeriod());
    //     if (historicalBCYSList.isEmpty()) {
    //         throw new ResourceNotFoundException(
    //                 "No historical record found for student in ClassYear " + classYear.getClassYear() +
    //                         " Semester " + semester.getAcademicPeriodCode()
    //         );
    //     }

    //     // We keep the original variable name for minimal code change
    //     BatchClassYearSemester historicalBCYS = historicalBCYSList.get(0);   // Use first one for academic context, GPA calculation, etc.

    //     // 4. Get ALL courses from ALL matching BCYS (this fixes the missing courses from later batches)
    //     List<StudentCourseScore> courseScores = new ArrayList<>();
    //     for (BatchClassYearSemester bcys : historicalBCYSList) {
    //         List<StudentCourseScore> scoresForThisBCYS = studentCourseScoreRepo
    //                 .findByStudentAndBatchClassYearSemester(student.getUser(), bcys);
    //         courseScores.addAll(scoresForThisBCYS);
    //     }

    //     // 5. Get grading system
    //     // System.out.println("getting Grading System ...");
    //     Department department = student.getDepartmentEnrolled();
    //     GradingSystem gradingSystem = gradingSystemService.findApplicableGradingSystem(department);
    //             // System.out.println("Finished grading system");

    //     // 6. Build course grades - Support both normal grading and Pass/Fail courses
    //     // System.out.println("Building course grades ...");
    //     List<CourseGradeDTO> courseGrades = new ArrayList<>();
    //     boolean hasFailedPassFailCourse = false;

    //     for (StudentCourseScore score : courseScores) {
    //         if (score.getScore() == null || !score.isReleased()) {
    //             continue;
    //         }

    //         Course course = score.getCourse();
    //         int totalCrHrs = course.getTheoryHrs() + course.getLabHrs();

    //         CourseGradeDTO cg = new CourseGradeDTO();
    //         cg.setCourseCode(course.getCCode());
    //         cg.setCourseTitle(course.getCTitle());
    //         cg.setTotalCrHrs(totalCrHrs);

    //         boolean passed = false;
    //         if (course.isPassFail()) {
    //             // === PASS/FAIL COURSE LOGIC ===
    //             if (score.getScore() == 1)
    //                 passed = true; // Special case: if score is exactly 1 → treat as 100 (some systems use 1 for pass)
    //             else
    //                 passed = score.getScore() >= MINIMUM_PASSFAIL_SCORE;   // Pass if score meets/exceeds threshold

    //             cg.setScore(score.getScore()); // Optional: include raw score in DTO for reference
    //             cg.setLetterGrade(passed ? "P" : "F");
    //             cg.setGradePoint(0.0);           // Pass/Fail courses usually do NOT affect GPA
    //             if (!passed) {
    //                 hasFailedPassFailCourse = true;
    //             }
    //             // Optional: you can add this if you extended CourseGradeDTO
    //             // cg.setIsPassFail(true);

    //         } else {
    //             // === NORMAL LETTER GRADE LOGIC ===
    //             MarkInterval interval = gradingSystem.getIntervals().stream()
    //                     .filter(i -> score.getScore() >= i.getMin() && score.getScore() <= i.getMax())
    //                     .findFirst()
    //                     .orElseThrow(() -> new IllegalStateException("No matching interval for score: " + score.getScore()));

    //             String letterGrade = interval.getGradeLetter();
    //             Double gradePoint = totalCrHrs * interval.getGivenValue();

    //             // Suffix logic (for repeated / external courses)
    //             String suffix = "";
    //             if (score.getCourseSource().getSourceID() == 2) suffix = "**";
    //             else if (score.getCourseSource().getSourceID() == 3) suffix = "*";

    //             cg.setScore(score.getScore());
    //             cg.setLetterGrade(letterGrade + suffix);
    //             cg.setGradePoint(gradePoint);
    //         }

    //         courseGrades.add(cg);
    //     }

    //     // 7. Calculate GPA & CGPA (already using the new ProgressionSequence version)
    //     //System.out.println("Calculating GPA and CGPA ...");
    //     double semesterGPA = calculateGPA(courseGrades);
    //     double semesterCGPA = calculateCGPA(student.getUser(), historicalBCYS, gradingSystem);   // ← now correct
    //     String semesterGPALetter = resolveGradeLetterForGpa(semesterGPA, gradingSystem);
    //     String semesterCGPALetter = resolveGradeLetterForGpa(semesterCGPA, gradingSystem);
    //     PreviousAcademicTotals previousTotals = calculatePreviousAcademicTotals(
    //         student.getUser(),
    //         student.getDepartmentEnrolled(),
    //         historicalBCYS,
    //         gradingSystem
    //     );
    //     String previousCGPALetter = resolveGradeLetterForGpa(previousTotals.cgpa(), gradingSystem);
    //     // System.out.println("Finished calculating GPA and CGPA");

    //     String status = (semesterGPA >= MINIMUM_PASSING_GPA && !hasFailedPassFailCourse) ? "PASSED" : "FAILED";

    //     // 10. Find AcademicYear and DepartmentBCYS for this historical BCYS (to get academic year and department-specific info)
    //     // Find DepartmentBCYS once (used for both academicYear and studentBCYS)
    //     DepartmentBCYS deptBCYS = departmentBCYSRepository
    //             .findByBcysAndDepartment(historicalBCYS, department)
    //             .orElseThrow(() -> new ResourceNotFoundException(
    //                     "No cohort record found for department " +
    //                     department.getDeptName() + " and BCYS " +
    //                     historicalBCYS.getDisplayName()
    //             ));

    //     AcademicYear academicYear = deptBCYS.getAcademicYear();

    //     // 11. Build response DTO
    //     StudentCopyDTO dto = new StudentCopyDTO();
    //     dto.setStudentBCYS(deptBCYS.getDisplayName());
    //     // Student Information
    //     dto.setIdNumber(student.getUser().getUsername());
    //     dto.setFullName(String.join(" ",
    //             student.getFirstNameENG(),
    //             student.getFatherNameENG(),
    //             student.getGrandfatherNameENG()).trim());
    //     dto.setGender(student.getGender().name());

    //     // Program Information
    //     StudentCopyDTO.ProgramModalityInfo programModalityInfo = new StudentCopyDTO.ProgramModalityInfo();
    //     programModalityInfo.setId(student.getProgramModality().getModalityCode());
    //     programModalityInfo.setName(student.getProgramModality().getModality());
    //     dto.setProgramModality(programModalityInfo);

    //     StudentCopyDTO.ProgramLevelInfo programLevelInfo = new StudentCopyDTO.ProgramLevelInfo();
    //     ProgramLevel programLevel = student.getDepartmentEnrolled().getProgramLevel();
    //     if (programLevel != null) {
    //         programLevelInfo.setId(programLevel.getCode());
    //         programLevelInfo.setName(programLevel.getName());
    //     }
    //     dto.setProgramLevel(programLevelInfo);

    //     dto.setDateEnrolledGC(student.getDateEnrolledGC());

    //     StudentCopyDTO.DepartmentInfo departmentInfo = new StudentCopyDTO.DepartmentInfo();
    //     departmentInfo.setId(department.getDptID());
    //     departmentInfo.setName(department.getDeptName());
    //     dto.setDepartment(departmentInfo);

    //     dto.setDateOfBirthGC(student.getDateOfBirthGC());

    //     // Academic Context
    //     StudentCopyDTO.ClassYearInfo classYearInfo = new StudentCopyDTO.ClassYearInfo();
    //     classYearInfo.setId(classYear.getId());
    //     classYearInfo.setName(classYear.getClassYear());
    //     dto.setClassyear(classYearInfo);

    //     StudentCopyDTO.SemesterInfo semesterInfo = new StudentCopyDTO.SemesterInfo();
    //     semesterInfo.setId(semester.getAcademicPeriodCode());
    //     semesterInfo.setName(semester.getAcademicPeriod());
    //     dto.setSemester(semesterInfo);

    //     if (academicYear != null) {
    //         StudentCopyDTO.AcademicYearInfo academicYearInfo = new StudentCopyDTO.AcademicYearInfo();
    //         academicYearInfo.setYearCode(academicYear.getYearCode());
    //         academicYearInfo.setYearGC(academicYear.getAcademicYearGC());
    //         dto.setAcademicYear(academicYearInfo);
    //     }

    //     // Course Grades
    //     dto.setCourses(courseGrades);

    //     // GPA Information
    //     dto.setSemesterGPA(semesterGPA);
    //     dto.setSemesterCGPA(semesterCGPA);
    //     dto.setSemesterGPALetter(semesterGPALetter);
    //     dto.setSemesterCGPALetter(semesterCGPALetter);
    //     dto.setPreviousCredit(previousTotals.totalCreditHours());
    //     dto.setPreviousGradePoint(previousTotals.totalGradePoints());
    //     dto.setPreviousCGPA(previousTotals.cgpa());
    //     dto.setPreviousCGPALetter(previousCGPALetter);
    //     dto.setStatus(status);

    //     return dto;
    // }

// ===================================================================================================================================================

    /**
     * ORIGINAL METHOD - 100% Backward Compatible
     * Existing calls will continue to work without any change.
     */
    @Transactional(readOnly = true)
    public StudentCopyDTO generateStudentCopy(StudentCopyRequestDTO request) {
        return generateStudentCopy(request, null);   // null means full computation
    }

    /**
     * NEW OVERLOADED METHOD - Main implementation
     * All new parameters are optional. If options = null → full original behavior.
     * Fields not calculated will remain null in the returned DTO.
     */
    @Transactional(readOnly = true)
    public StudentCopyDTO generateStudentCopy(StudentCopyRequestDTO request, StudentCopyOptions options) {
        if (options == null) {
            options = StudentCopyOptions.full();
        }

        // 1. Get student
        StudentDetails student = studentDetailsRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + request.getStudentId()));

        // 2. Get requested classYear and semester
        ClassYear classYear = classYearRepository.findById(request.getClassYearId())
                .orElseThrow(() -> new ResourceNotFoundException("ClassYear not found with id: " + request.getClassYearId()));

        Semester semester = semesterRepo.findById(request.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found with id: " + request.getSemesterId()));

        // 3. CRITICAL: Find ALL historical BCYS for the requested classYear + semester
        List<BatchClassYearSemester> historicalBCYSList = findHistoricalBCYS(student.getUser(), classYear, semester);
        if (historicalBCYSList.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No historical record found for student in ClassYear " + classYear.getClassYear() +
                            " Semester " + semester.getAcademicPeriodCode()
            );
        }

        BatchClassYearSemester primaryBCYS = historicalBCYSList.get(0);

        // 4. Get ALL courses from ALL matching BCYS
        List<StudentCourseScore> courseScores = fetchAllRelevantCourseScores(student.getUser(), historicalBCYSList);

        // 5. Grading System - Use provided one if available (big optimization)
        Department department = student.getDepartmentEnrolled();
        GradingSystem gradingSystem = options.getGradingSystem() != null
                ? options.getGradingSystem()
                : gradingSystemService.findApplicableGradingSystem(department);

        // 6. Build course grades
        List<CourseGradeDTO> courseGrades = buildCourseGrades(courseScores, gradingSystem);

        // 7. Conditional heavy calculations
        double semesterGPA = 0.0;
        double semesterCGPA = 0.0;
        PreviousAcademicTotals previousTotals = new PreviousAcademicTotals(0, 0.0, 0.0);
        String status = null;

        if (options.isIncludeGPA()) {
            semesterGPA = calculateGPA(courseGrades);

            if (options.isCalculateCGPA()) {
                semesterCGPA = calculateCGPA(student.getUser(), primaryBCYS, gradingSystem);
            }

            if (options.isIncludePreviousTotals()) {
                previousTotals = calculatePreviousAcademicTotals(
                        student.getUser(), department, primaryBCYS, gradingSystem);
            }

            if (options.isIncludeStatus()) {
                boolean hasFailedPassFail = hasFailedPassFailCourse(courseScores);
                status = (semesterGPA >= MINIMUM_PASSING_GPA && !hasFailedPassFail) ? "PASSED" : "FAILED";
            }
        }

        // 8. Build final DTO (only fill requested sections)
        return buildStudentCopyDTO(student, classYear, semester, primaryBCYS, department,
                courseGrades, semesterGPA, semesterCGPA, previousTotals, status, options, gradingSystem);
    }

        // ==================== HELPER 1 ====================
    /**
     * Finds ALL historical BCYS records for a student in a specific ClassYear + Semester.
     * This is critical for students who repeated the same year/semester in different batches.
     */
    private List<BatchClassYearSemester> findHistoricalBCYS(User student, ClassYear classYear, Semester semester) {
        return studentCourseScoreRepo
                .findByStudentAndIsReleasedTrue(student).stream()
                .map(StudentCourseScore::getBatchClassYearSemester)
                .filter(bcys ->
                        bcys.getClassYear().getId().equals(classYear.getId()) &&
                        bcys.getSemester().getAcademicPeriodCode().equals(semester.getAcademicPeriodCode())
                )
                .distinct()
                .toList();
    }

    // ==================== HELPER 2 ====================
    /**
     * Fetches all relevant course scores from all historical BCYS records.
     * Preserves original behavior of combining scores from multiple BCYS.
     */
    private List<StudentCourseScore> fetchAllRelevantCourseScores(User student, List<BatchClassYearSemester> historicalBCYSList) {
        List<StudentCourseScore> courseScores = new ArrayList<>();
        for (BatchClassYearSemester bcys : historicalBCYSList) {
            List<StudentCourseScore> scoresForThisBCYS = studentCourseScoreRepo
                    .findByStudentAndBatchClassYearSemester(student, bcys);
            courseScores.addAll(scoresForThisBCYS);
        }
        return courseScores;
    }

    // ==================== HELPER 3 ====================
    /**
     * Builds CourseGradeDTO list with support for both normal grading and Pass/Fail courses.
     * Exact same logic as your original code.
     */
    private List<CourseGradeDTO> buildCourseGrades(List<StudentCourseScore> courseScores, GradingSystem gradingSystem) {
        List<CourseGradeDTO> courseGrades = new ArrayList<>();
        // hasFailedPassFailCourse will be checked later if needed

        for (StudentCourseScore score : courseScores) {
            if (score.getScore() == null || !score.isReleased()) {
                continue;
            }

            Course course = score.getCourse();
            int totalCrHrs = course.getTheoryHrs() + course.getLabHrs();

            CourseGradeDTO cg = new CourseGradeDTO();
            cg.setCourseCode(course.getCCode());
            cg.setCourseTitle(course.getCTitle());
            cg.setTotalCrHrs(totalCrHrs);

            if (course.isPassFail()) {
                // === PASS/FAIL COURSE LOGIC ===
                boolean passed = false;
                if (score.getScore() == 1) {
                    passed = true;
                } else {
                    passed = score.getScore() >= MINIMUM_PASSFAIL_SCORE;
                }

                cg.setScore(score.getScore());
                cg.setLetterGrade(passed ? "P" : "F");
                cg.setGradePoint(0.0);           // Pass/Fail does not affect GPA
            } else {
                // === NORMAL LETTER GRADE LOGIC ===
                MarkInterval interval = gradingSystem.getIntervals().stream()
                        .filter(i -> score.getScore() >= i.getMin() && score.getScore() <= i.getMax())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No matching interval for score: " + score.getScore()));

                String letterGrade = interval.getGradeLetter();
                Double gradePoint = totalCrHrs * interval.getGivenValue();

                // Suffix logic
                String suffix = "";
                if (score.getCourseSource().getSourceID() == 2) suffix = SUFFIX_REPEAT;
                else if (score.getCourseSource().getSourceID() == 3) suffix = SUFFIX_EXTERNAL;

                cg.setScore(score.getScore());
                cg.setLetterGrade(letterGrade + suffix);
                cg.setGradePoint(gradePoint);
            }

            courseGrades.add(cg);
        }
        return courseGrades;
    }

        // ==================== HELPER 4 ====================
    /**
     * Checks if any Pass/Fail course was failed.
     * Used for determining overall semester status.
     */
    private boolean hasFailedPassFailCourse(List<StudentCourseScore> courseScores) {
        for (StudentCourseScore score : courseScores) {
            if (score.getScore() == null || !score.isReleased()) continue;
            
            Course course = score.getCourse();
            if (course.isPassFail()) {
                boolean passed = (score.getScore() == 1) || (score.getScore() >= MINIMUM_PASSFAIL_SCORE);
                if (!passed) {
                    return true;
                }
            }
        }
        return false;
    }

        // ==================== HELPER 5 ====================
    /**
     * Builds the final StudentCopyDTO.
     * Only populates fields based on the provided options.
     * Keeps exact same structure and calculations as original.
     * Accepts pre-fetched gradingSystem for letter grade resolution.
     */
    private StudentCopyDTO buildStudentCopyDTO(
            StudentDetails student,
            ClassYear classYear,
            Semester semester,
            BatchClassYearSemester primaryBCYS,
            Department department,
            List<CourseGradeDTO> courseGrades,
            double semesterGPA,
            double semesterCGPA,
            PreviousAcademicTotals previousTotals,
            String status,
            StudentCopyOptions options,
            GradingSystem gradingSystem) {     // ← added

        StudentCopyDTO dto = new StudentCopyDTO();
        dto.setCourses(courseGrades);

        // Student Information
        if (options.isIncludeStudentInfo()) {
            dto.setIdNumber(student.getUser().getUsername());
            dto.setFullName(String.join(" ",
                    student.getFirstNameENG(),
                    student.getFatherNameENG(),
                    student.getGrandfatherNameENG()).trim());
            dto.setGender(student.getGender().name());
            dto.setDateOfBirthGC(student.getDateOfBirthGC());
        }

        // Program Information
        if (options.isIncludeProgramInfo()) {
            StudentCopyDTO.ProgramModalityInfo programModalityInfo = new StudentCopyDTO.ProgramModalityInfo();
            programModalityInfo.setId(student.getProgramModality().getModalityCode());
            programModalityInfo.setName(student.getProgramModality().getModality());
            dto.setProgramModality(programModalityInfo);

            StudentCopyDTO.ProgramLevelInfo programLevelInfo = new StudentCopyDTO.ProgramLevelInfo();
            ProgramLevel programLevel = student.getDepartmentEnrolled().getProgramLevel();
            if (programLevel != null) {
                programLevelInfo.setId(programLevel.getCode());
                programLevelInfo.setName(programLevel.getName());
            }
            dto.setProgramLevel(programLevelInfo);

            dto.setDateEnrolledGC(student.getDateEnrolledGC());

            StudentCopyDTO.DepartmentInfo departmentInfo = new StudentCopyDTO.DepartmentInfo();
            departmentInfo.setId(department.getDptID());
            departmentInfo.setName(department.getDeptName());
            dto.setDepartment(departmentInfo);
        }

        // Academic Context
        if (options.isIncludeAcademicContext()) {
            StudentCopyDTO.ClassYearInfo classYearInfo = new StudentCopyDTO.ClassYearInfo();
            classYearInfo.setId(classYear.getId());
            classYearInfo.setName(classYear.getClassYear());
            dto.setClassyear(classYearInfo);

            StudentCopyDTO.SemesterInfo semesterInfo = new StudentCopyDTO.SemesterInfo();
            semesterInfo.setId(semester.getAcademicPeriodCode());
            semesterInfo.setName(semester.getAcademicPeriod());
            dto.setSemester(semesterInfo);

            DepartmentBCYS deptBCYS = departmentBCYSRepository
                    .findByBcysAndDepartment(primaryBCYS, department)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No cohort record found for department " + department.getDeptName()));

            AcademicYear academicYear = deptBCYS.getAcademicYear();
            if (academicYear != null) {
                StudentCopyDTO.AcademicYearInfo academicYearInfo = new StudentCopyDTO.AcademicYearInfo();
                academicYearInfo.setYearCode(academicYear.getYearCode());
                academicYearInfo.setYearGC(academicYear.getAcademicYearGC());
                dto.setAcademicYear(academicYearInfo);
            }

            dto.setStudentBCYS(deptBCYS.getDisplayName());
        }

        // GPA & Status Information
        if (options.isIncludeGPA()) {
            dto.setSemesterGPA(semesterGPA);
            dto.setSemesterCGPA(semesterCGPA);

            dto.setSemesterGPALetter(resolveGradeLetterForGpa(semesterGPA, gradingSystem));
            dto.setSemesterCGPALetter(resolveGradeLetterForGpa(semesterCGPA, gradingSystem));

            if (options.isIncludePreviousTotals()) {
                dto.setPreviousCredit(previousTotals.totalCreditHours());
                dto.setPreviousGradePoint(previousTotals.totalGradePoints());
                dto.setPreviousCGPA(previousTotals.cgpa());
                dto.setPreviousCGPALetter(resolveGradeLetterForGpa(previousTotals.cgpa(), gradingSystem));
            }

            if (options.isIncludeStatus()) {
                dto.setStatus(status);
            }
        }

        return dto;
    }

// ===================================================================================================================================================



    /**
     * Calculates GPA for a list of course grades.
     * Formula: Sum of (GradePoint) / Sum of (Credit Hours)
     */
    public double calculateGPA(List<CourseGradeDTO> courseGrades) {
        if (courseGrades == null || courseGrades.isEmpty()) {
            return 0.0;
        }

        double totalGradePoints = courseGrades.stream()
                .mapToDouble(CourseGradeDTO::getGradePoint)
                .sum();

        int totalCreditHours = courseGrades.stream()
                .mapToInt(CourseGradeDTO::getTotalCrHrs)
                .sum();

        if (totalCreditHours == 0) {
            return 0.0;
        }

        return totalGradePoints / totalCreditHours;
    }


    /**
     * Calculates CGPA for a student using released course scores.
     *
     * Progression ordering rules:
     * - If requestedBCYS is null               → includes ALL released courses
     * - If requestedBCYS is provided           → includes only courses whose progression
     *                                            sequence number <= requested sequence number
     *
     * Sequence lookup priority:
     * 1. Department-specific rule (student's current department)
     * 2. Global rule (department = null)
     *
     * If no sequence rule exists for a BCYS (specific or global) → course is included (conservative)
     *
     * Special handling:
     * - If requested BCYS batch name = "0" (graduated/not learning) → full history
     *
     * @param student the student user
     * @param requestedBCYS target semester for cumulative calculation (null = all)
     * @param gradingSystem grading intervals to convert raw score → grade point
     * @return CGPA rounded to 2 decimal places (0.0 if no valid credits)
     */
    public double calculateCGPA(User student, BatchClassYearSemester requestedBCYS, GradingSystem gradingSystem) {
        // 1. Get student's current department
        Department studentDept = studentDetailsRepository.findByUser(student)
                .map(StudentDetails::getDepartmentEnrolled)
                .orElse(null);

        // 2. Fetch all released scores
        List<StudentCourseScore> allScores = studentCourseScoreRepo
                .findByStudentAndIsReleasedTrue(student);

        if (allScores.isEmpty()) {
            return 0.0;
        }

        // 3. If no requested BCYS → full history
        if (requestedBCYS == null) {
            return calculateFromScores(allScores, gradingSystem);
        }

        // 4. Special case: graduated / not learning (batch = 0)
        if (requestedBCYS.getBatch() != null && "0".equals(requestedBCYS.getBatch().getBatchName())) {
            return calculateFromScores(allScores, gradingSystem);
        }

        // 5. Determine requested sequence number
        Integer requestedSequence = getProgressionSequenceNumber(
                studentDept,
                requestedBCYS.getClassYear(),
                requestedBCYS.getSemester()
        );

        // If no sequence found for requested → include everything (fail open)
        if (requestedSequence == null) {
            return calculateFromScores(allScores, gradingSystem);
        }

        // 6. Pre-load all possible sequences for faster lookup (department + global)
        Map<String, Integer> sequenceMap = buildSequenceMap(studentDept);

        // 7. Filter scores
        List<StudentCourseScore> relevantScores = allScores.stream()
                .filter(score -> {
                    BatchClassYearSemester scoreBCYS = score.getBatchClassYearSemester();

                    // Optional strict batch matching (uncomment if needed)
                    // if (!scoreBCYS.getBatch().getId().equals(requestedBCYS.getBatch().getId())) {
                    //     return false;
                    // }

                    Integer scoreSeq = getSequenceFromMap(
                            sequenceMap,
                            studentDept,
                            scoreBCYS.getClassYear(),
                            scoreBCYS.getSemester()
                    );

                    // If no sequence → include (conservative)
                    if (scoreSeq == null) {
                        return true;
                    }

                    return scoreSeq <= requestedSequence;
                })
                .collect(Collectors.toList());

        // 8. Calculate final CGPA
        return calculateFromScores(relevantScores, gradingSystem);
    }

    /**
     * Helper: Builds a map of (classYearId + "_" + semesterCode) → sequenceNumber
     * Includes both department-specific and global rules (global overrides missing specific)
     */
    private Map<String, Integer> buildSequenceMap(Department dept) {
        Map<String, Integer> map = new HashMap<>();

        // Global rules first (lower priority)
        List<ProgressionSequence> globals = progressionSequenceRepository.findByDepartmentIsNull();
        for (ProgressionSequence g : globals) {
            String key = g.getClassYear().getId() + "_" + g.getSemester().getAcademicPeriodCode();
            map.put(key, g.getSequenceNumber());
        }

        // Department-specific rules (override globals if exist)
        if (dept != null) {
            List<ProgressionSequence> specifics = progressionSequenceRepository.findByDepartment(dept);
            for (ProgressionSequence s : specifics) {
                String key = s.getClassYear().getId() + "_" + s.getSemester().getAcademicPeriodCode();
                map.put(key, s.getSequenceNumber());
            }
        }

        return map;
    }

    /**
     * Helper: Gets sequence number with department → global fallback
     */
    private Integer getProgressionSequenceNumber(Department dept, ClassYear cy, Semester sem) {
        if (cy == null || sem == null) {
            return null;
        }

        // Try specific
        if (dept != null) {
            Optional<ProgressionSequence> specific = progressionSequenceRepository
                    .findByDepartmentAndClassYearAndSemester(dept, cy, sem);
            if (specific.isPresent()) {
                return specific.get().getSequenceNumber();
            }
        }

        // Fallback to global
        Optional<ProgressionSequence> global = progressionSequenceRepository
                .findByDepartmentIsNullAndClassYearAndSemester(cy, sem);
        return global.map(ProgressionSequence::getSequenceNumber).orElse(null);
    }

    /**
     * Helper: Looks up sequence from pre-built map
     */
    private Integer getSequenceFromMap(Map<String, Integer> map, Department dept, ClassYear cy, Semester sem) {
        if (cy == null || sem == null) {
            return null;
        }
        String key = cy.getId() + "_" + sem.getAcademicPeriodCode();
        return map.get(key);
    }

    private boolean sameClassYearAndSemester(BatchClassYearSemester left, BatchClassYearSemester right) {
        if (left == null || right == null) {
            return false;
        }

        if (left.getClassYear() == null || right.getClassYear() == null ||
                left.getSemester() == null || right.getSemester() == null) {
            return false;
        }

        return Objects.equals(left.getClassYear().getId(), right.getClassYear().getId()) &&
                Objects.equals(left.getSemester().getAcademicPeriodCode(), right.getSemester().getAcademicPeriodCode());
    }

    private record GradeComputationTotals(double totalGradePoints, int totalCreditHours) {}

    private record PreviousAcademicTotals(int totalCreditHours, double totalGradePoints, double cgpa) {}

    private PreviousAcademicTotals calculatePreviousAcademicTotals(
            User student,
            Department studentDept,
            BatchClassYearSemester requestedBCYS,
            GradingSystem gradingSystem
    ) {
        if (student == null || requestedBCYS == null || gradingSystem == null) {
            return new PreviousAcademicTotals(0, 0.0, 0.0);
        }

        List<StudentCourseScore> allScores = studentCourseScoreRepo.findByStudentAndIsReleasedTrue(student);
        if (allScores.isEmpty()) {
            return new PreviousAcademicTotals(0, 0.0, 0.0);
        }

        Integer requestedSequence = getProgressionSequenceNumber(
                studentDept,
                requestedBCYS.getClassYear(),
                requestedBCYS.getSemester()
        );

        Map<String, Integer> sequenceMap = buildSequenceMap(studentDept);

        List<StudentCourseScore> previousScores = allScores.stream()
                .filter(score -> {
                    BatchClassYearSemester scoreBCYS = score.getBatchClassYearSemester();
                    if (scoreBCYS == null) {
                        return false;
                    }

                    Integer scoreSequence = getSequenceFromMap(
                            sequenceMap,
                            studentDept,
                            scoreBCYS.getClassYear(),
                            scoreBCYS.getSemester()
                    );

                    if (requestedSequence != null && scoreSequence != null) {
                        return scoreSequence < requestedSequence;
                    }

                    // Fallback if sequence cannot be determined: exclude only the requested class year + semester.
                    return !sameClassYearAndSemester(scoreBCYS, requestedBCYS);
                })
                .collect(Collectors.toList());

        GradeComputationTotals totals = computeWeightedTotals(previousScores, gradingSystem);
        double previousCgpa = totals.totalCreditHours() == 0
                ? 0.0
                : totals.totalGradePoints() / totals.totalCreditHours();

        return new PreviousAcademicTotals(totals.totalCreditHours(), totals.totalGradePoints(), previousCgpa);
    }

    /**
     * Core calculation logic (extracted for reuse)
     */
    private double calculateFromScores(List<StudentCourseScore> scores, GradingSystem gradingSystem) {
        GradeComputationTotals totals = computeWeightedTotals(scores, gradingSystem);
        return totals.totalCreditHours() == 0 ? 0.0 : totals.totalGradePoints() / totals.totalCreditHours();
    }

    private GradeComputationTotals computeWeightedTotals(List<StudentCourseScore> scores, GradingSystem gradingSystem) {
        double totalGradePoints = 0.0;
        int totalCreditHours = 0;

        if (scores == null || scores.isEmpty() || gradingSystem == null || gradingSystem.getIntervals() == null) {
            return new GradeComputationTotals(0.0, 0);
        }

        List<MarkInterval> intervals = gradingSystem.getIntervals();

        for (StudentCourseScore scs : scores) {
            if (scs.getScore() == null) continue;

            Course course = scs.getCourse();
            if (course == null) continue;

            int crHrs = course.getTheoryHrs() + course.getLabHrs();
            if (crHrs <= 0) continue;

            MarkInterval interval = intervals.stream()
                    .filter(i -> scs.getScore() >= i.getMin() && scs.getScore() <= i.getMax())
                    .findFirst()
                    .orElse(null);

            if (interval != null) {
                totalGradePoints += crHrs * interval.getGivenValue();
                totalCreditHours += crHrs;
            }
        }

        return new GradeComputationTotals(totalGradePoints, totalCreditHours);
    }

    /**
     * Resolves a letter grade for an aggregated GPA value using the grading system.
     * GPA is mapped to the highest interval whose givenValue is <= GPA.
     */
    private String resolveGradeLetterForGpa(double gpa, GradingSystem gradingSystem) {
        if (gradingSystem == null || gradingSystem.getIntervals() == null || gradingSystem.getIntervals().isEmpty()) {
            return null;
        }

        final double epsilon = 1e-9;

        List<MarkInterval> sortedByGivenValueDesc = gradingSystem.getIntervals().stream()
                .filter(Objects::nonNull)
                .filter(interval -> interval.getGradeLetter() != null)
                .sorted(Comparator.comparingDouble(MarkInterval::getGivenValue).reversed())
                .collect(Collectors.toList());

        if (sortedByGivenValueDesc.isEmpty()) {
            return null;
        }

        for (MarkInterval interval : sortedByGivenValueDesc) {
            if (gpa + epsilon >= interval.getGivenValue()) {
                return interval.getGradeLetter();
            }
        }

        // If GPA is below the minimum threshold, return the lowest letter in the grading system.
        return sortedByGivenValueDesc.get(sortedByGivenValueDesc.size() - 1).getGradeLetter();
    }

    /**
     * Generates a simplified student copy for transcripts/grade reports.
     * Maintains original behavior: returns academic context + courses + all GPA fields.
     */
    @Transactional(readOnly = true)
    public SimplifiedStudentCopyDTO generateSimplifiedStudentCopy(
            StudentCopyRequestDTO request,
            StudentCopyOptions options) {

        if (options == null) {
            // Default for Simplified: Include academic + GPA, but exclude personal info
            options = StudentCopyOptions.builder()
                    .includeStudentInfo(false)
                    .includeProgramInfo(false)
                    .includeAcademicContext(true)
                    .includeGPA(true)
                    .calculateCGPA(true)
                    .includePreviousTotals(true)
                    .includeStatus(true)
                    .build();
        }

        // Call main method
        StudentCopyDTO fullCopy = generateStudentCopy(request, options);

        // Convert to SimplifiedStudentCopyDTO (exact fields expected by GradeReport)
        SimplifiedStudentCopyDTO simplified = new SimplifiedStudentCopyDTO();

        // Academic Context
        if (fullCopy.getClassyear() != null) {
            SimplifiedStudentCopyDTO.ClassYearInfo classYearInfo = new SimplifiedStudentCopyDTO.ClassYearInfo();
            classYearInfo.setId(fullCopy.getClassyear().getId());
            classYearInfo.setName(fullCopy.getClassyear().getName());
            simplified.setClassyear(classYearInfo);
        }

        if (fullCopy.getSemester() != null) {
            SimplifiedStudentCopyDTO.SemesterInfo semesterInfo = new SimplifiedStudentCopyDTO.SemesterInfo();
            semesterInfo.setId(fullCopy.getSemester().getId());
            semesterInfo.setName(fullCopy.getSemester().getName());
            simplified.setSemester(semesterInfo);
        }

        if (fullCopy.getAcademicYear() != null) {
            SimplifiedStudentCopyDTO.AcademicYearInfo academicYearInfo = new SimplifiedStudentCopyDTO.AcademicYearInfo();
            academicYearInfo.setYearCode(fullCopy.getAcademicYear().getYearCode());
            academicYearInfo.setYearGC(fullCopy.getAcademicYear().getYearGC());
            simplified.setAcademicYear(academicYearInfo);
        }

        // Course Grades
        simplified.setCourses(fullCopy.getCourses());

        // GPA Information - Copy all fields that original method returned
        simplified.setSemesterGPA(fullCopy.getSemesterGPA());
        simplified.setSemesterCGPA(fullCopy.getSemesterCGPA());
        simplified.setSemesterGPALetter(fullCopy.getSemesterGPALetter());
        simplified.setSemesterCGPALetter(fullCopy.getSemesterCGPALetter());
        simplified.setPreviousCredit(fullCopy.getPreviousCredit());
        simplified.setPreviousGradePoint(fullCopy.getPreviousGradePoint());
        simplified.setPreviousCGPA(fullCopy.getPreviousCGPA());
        simplified.setPreviousCGPALetter(fullCopy.getPreviousCGPALetter());
        simplified.setStatus(fullCopy.getStatus());

        // Optional but useful
//        simplified.setStudentBCYS(fullCopy.getStudentBCYS());

        return simplified;
    }

    /**
     * Backward Compatible Overload
     */
    @Transactional(readOnly = true)
    public SimplifiedStudentCopyDTO generateSimplifiedStudentCopy(StudentCopyRequestDTO request) {
        return generateSimplifiedStudentCopy(request, null);
    }

    /**
     * Generates student copies for multiple students for the same classyear and semester.
     * 
     * @param studentIds List of student IDs
     * @param classYearId ClassYear ID
     * @param semesterId Semester ID
     * @return List of StudentCopyDTO for each student
     */
    @Transactional(readOnly = true)
    public List<StudentCopyDTO> generateStudentCopiesForMultipleStudents(List<Long> studentIds, Long classYearId, String semesterId) {
        List<StudentCopyDTO> studentCopies = new ArrayList<>();
        
        for (Long studentId : studentIds) {
            try {
                StudentCopyRequestDTO request = new StudentCopyRequestDTO();
                request.setStudentId(studentId);
                request.setClassYearId(classYearId);
                request.setSemesterId(semesterId);
                
                StudentCopyDTO studentCopy = generateStudentCopy(request);
                studentCopies.add(studentCopy);
            } catch (Exception e) {
                // Skip students that have errors, continue with others
                continue;
            }
        }
        
        return studentCopies;
    }

}

