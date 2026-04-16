package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.CourseGradeDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.SimplifiedStudentCopyDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyDTO;
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

import java.time.LocalDate;
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



    /**
     * Generates a student copy for a specific classyear and semester.
     * 
     * @param request The request containing studentId, classYearId, and semesterId
     * @return StudentCopyDTO containing all student information and course grades
     * @throws ResourceNotFoundException if student, classyear, semester, or batch-class-year-semester not found
     */
    @Transactional(readOnly = true)
    public StudentCopyDTO generateStudentCopy(StudentCopyRequestDTO request) {
        // System.out.println("Generating a full student Copy for student: " + request.getStudentId());

        // 1. Get student
        StudentDetails student = studentDetailsRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + request.getStudentId()));

        // 2. Get requested classYear and semester
        ClassYear classYear = classYearRepository.findById(request.getClassYearId())
                .orElseThrow(() -> new ResourceNotFoundException("ClassYear not found with id: " + request.getClassYearId()));

        Semester semester = semesterRepo.findById(request.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found with id: " + request.getSemesterId()));

        // 3. CRITICAL FIX: Find ALL historical BCYS for the requested classYear + semester
        //    This handles cases where student repeats the same year/semester in different batches
        List<BatchClassYearSemester> historicalBCYSList = studentCourseScoreRepo
                .findByStudentAndIsReleasedTrue(student.getUser()).stream()
                .map(StudentCourseScore::getBatchClassYearSemester)
                .filter(bcys ->
                        bcys.getClassYear().getId().equals(classYear.getId()) &&
                        bcys.getSemester().getAcademicPeriodCode().equals(semester.getAcademicPeriodCode())
                )
                .distinct()                    // remove duplicate BCYS if any
                .toList();

        if (historicalBCYSList.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No historical record found for student in ClassYear " + classYear.getClassYear() +
                            " Semester " + semester.getAcademicPeriodCode()
            );
        }

        // We keep the original variable name for minimal code change
        BatchClassYearSemester historicalBCYS = historicalBCYSList.get(0);   // Use first one for academic context, GPA calculation, etc.

        // 4. Get ALL courses from ALL matching BCYS (this fixes the missing courses from later batches)
        List<StudentCourseScore> courseScores = new ArrayList<>();
        for (BatchClassYearSemester bcys : historicalBCYSList) {
            List<StudentCourseScore> scoresForThisBCYS = studentCourseScoreRepo
                    .findByStudentAndBatchClassYearSemester(student.getUser(), bcys);
            courseScores.addAll(scoresForThisBCYS);
        }

        // 5. Get grading system
        // System.out.println("getting Grading System ...");
        Department department = student.getDepartmentEnrolled();
        GradingSystem gradingSystem = gradingSystemService.findApplicableGradingSystem(department);
                // System.out.println("Finished grading system");

        // 6. Build course grades (unchanged)
        // System.out.println("Building course grades ...");
        List<CourseGradeDTO> courseGrades = new ArrayList<>();
        for (StudentCourseScore score : courseScores) {
            if (score.getScore() == null || !score.isReleased()) continue;

            Course course = score.getCourse();
            int totalCrHrs = course.getTheoryHrs() + course.getLabHrs();

            MarkInterval interval = gradingSystem.getIntervals().stream()
                    .filter(i -> score.getScore() >= i.getMin() && score.getScore() <= i.getMax())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No matching interval for score: " + score.getScore()));

            String letterGrade = interval.getGradeLetter();
            Double gradePoint = totalCrHrs * interval.getGivenValue();

            // Suffix logic
            String suffix = "";
            if (score.getCourseSource().getSourceID() == 2) suffix = "**";
            else if (score.getCourseSource().getSourceID() == 3) suffix = "*";

            CourseGradeDTO cg = new CourseGradeDTO();
            cg.setCourseCode(course.getCCode());
            cg.setCourseTitle(course.getCTitle());
            cg.setTotalCrHrs(totalCrHrs);
            cg.setLetterGrade(letterGrade + suffix);
            cg.setGradePoint(gradePoint);

            courseGrades.add(cg);
        }
        // System.out.println("Finished building course grades, total courses: " + courseGrades.size());

        // 7. Calculate GPA & CGPA (already using the new ProgressionSequence version)
        //System.out.println("Calculating GPA and CGPA ...");
        double semesterGPA = calculateGPA(courseGrades);
        double semesterCGPA = calculateCGPA(student.getUser(), historicalBCYS, gradingSystem);   // ← now correct
        String semesterGPALetter = resolveGradeLetterForGpa(semesterGPA, gradingSystem);
        String semesterCGPALetter = resolveGradeLetterForGpa(semesterCGPA, gradingSystem);
        PreviousAcademicTotals previousTotals = calculatePreviousAcademicTotals(
            student.getUser(),
            student.getDepartmentEnrolled(),
            historicalBCYS,
            gradingSystem
        );
        String previousCGPALetter = resolveGradeLetterForGpa(previousTotals.cgpa(), gradingSystem);
        // System.out.println("Finished calculating GPA and CGPA");

        String status = semesterGPA >= MINIMUM_PASSING_GPA ? "PASSED" : "FAILED";

        // 10. Find AcademicYear and DepartmentBCYS for this historical BCYS (to get academic year and department-specific info)
        // Find DepartmentBCYS once (used for both academicYear and studentBCYS)
        DepartmentBCYS deptBCYS = departmentBCYSRepository
                .findByBcysAndDepartment(historicalBCYS, department)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No cohort record found for department " +
                        department.getDeptName() + " and BCYS " +
                        historicalBCYS.getDisplayName()
                ));

        AcademicYear academicYear = deptBCYS.getAcademicYear();

        // 11. Build response DTO
        StudentCopyDTO dto = new StudentCopyDTO();
        dto.setStudentBCYS(deptBCYS.getDisplayName());
        // Student Information
        dto.setIdNumber(student.getUser().getUsername());
        dto.setFullName(String.join(" ",
                student.getFirstNameENG(),
                student.getFatherNameENG(),
                student.getGrandfatherNameENG()).trim());
        dto.setGender(student.getGender().name());

        // Program Information
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

        dto.setDateOfBirthGC(student.getDateOfBirthGC());

        // Academic Context
        StudentCopyDTO.ClassYearInfo classYearInfo = new StudentCopyDTO.ClassYearInfo();
        classYearInfo.setId(classYear.getId());
        classYearInfo.setName(classYear.getClassYear());
        dto.setClassyear(classYearInfo);

        StudentCopyDTO.SemesterInfo semesterInfo = new StudentCopyDTO.SemesterInfo();
        semesterInfo.setId(semester.getAcademicPeriodCode());
        semesterInfo.setName(semester.getAcademicPeriod());
        dto.setSemester(semesterInfo);

        if (academicYear != null) {
            StudentCopyDTO.AcademicYearInfo academicYearInfo = new StudentCopyDTO.AcademicYearInfo();
            academicYearInfo.setYearCode(academicYear.getYearCode());
            academicYearInfo.setYearGC(academicYear.getAcademicYearGC());
            dto.setAcademicYear(academicYearInfo);
        }

        // Course Grades
        dto.setCourses(courseGrades);

        // GPA Information
        dto.setSemesterGPA(semesterGPA);
        dto.setSemesterCGPA(semesterCGPA);
        dto.setSemesterGPALetter(semesterGPALetter);
        dto.setSemesterCGPALetter(semesterCGPALetter);
        dto.setPreviousCredit(previousTotals.totalCreditHours());
        dto.setPreviousGradePoint(previousTotals.totalGradePoints());
        dto.setPreviousCGPA(previousTotals.cgpa());
        dto.setPreviousCGPALetter(previousCGPALetter);
        dto.setStatus(status);

        return dto;
    }

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
     * Generates a simplified student copy (without student information) for grade reports.
     * 
     * @param request The request containing studentId, classYearId, and semesterId
     * @return SimplifiedStudentCopyDTO containing only academic context, courses, and GPA info
     * @throws ResourceNotFoundException if student, classyear, semester, or batch-class-year-semester not found
     */
    @Transactional(readOnly = true)
    public SimplifiedStudentCopyDTO generateSimplifiedStudentCopy(StudentCopyRequestDTO request) {
        // Generate full student copy first
        StudentCopyDTO fullCopy = generateStudentCopy(request);
        // System.out.println("Generated full student copy, now converting to simplified version ...");

        // Convert to simplified version
        SimplifiedStudentCopyDTO simplified = new SimplifiedStudentCopyDTO();
        
        // Academic Context
        SimplifiedStudentCopyDTO.ClassYearInfo classYearInfo = new SimplifiedStudentCopyDTO.ClassYearInfo();
        classYearInfo.setId(fullCopy.getClassyear().getId());
        classYearInfo.setName(fullCopy.getClassyear().getName());
        simplified.setClassyear(classYearInfo);
        
        SimplifiedStudentCopyDTO.SemesterInfo semesterInfo = new SimplifiedStudentCopyDTO.SemesterInfo();
        semesterInfo.setId(fullCopy.getSemester().getId());
        semesterInfo.setName(fullCopy.getSemester().getName());
        simplified.setSemester(semesterInfo);
        
        if (fullCopy.getAcademicYear() != null) {
            SimplifiedStudentCopyDTO.AcademicYearInfo academicYearInfo = new SimplifiedStudentCopyDTO.AcademicYearInfo();
            academicYearInfo.setYearCode(fullCopy.getAcademicYear().getYearCode());
            academicYearInfo.setYearGC(fullCopy.getAcademicYear().getYearGC());
            simplified.setAcademicYear(academicYearInfo);
        }
        
        // Course Grades
        simplified.setCourses(fullCopy.getCourses());
        
        // GPA Information
        simplified.setSemesterGPA(fullCopy.getSemesterGPA());
        simplified.setSemesterCGPA(fullCopy.getSemesterCGPA());
        simplified.setSemesterGPALetter(fullCopy.getSemesterGPALetter());
        simplified.setSemesterCGPALetter(fullCopy.getSemesterCGPALetter());
        simplified.setPreviousCredit(fullCopy.getPreviousCredit());
        simplified.setPreviousGradePoint(fullCopy.getPreviousGradePoint());
        simplified.setPreviousCGPA(fullCopy.getPreviousCGPA());
        simplified.setPreviousCGPALetter(fullCopy.getPreviousCGPALetter());
        simplified.setStatus(fullCopy.getStatus());
        
        return simplified;
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

