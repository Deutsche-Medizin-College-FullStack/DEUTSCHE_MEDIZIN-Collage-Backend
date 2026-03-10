package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.Student.StudentDashboardDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentDashboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentDetailsRepository studentDetailsRepository;

    @Autowired
    private StudentCourseScoreRepo studentCourseScoreRepo;
    @Autowired
    private DepartmentBCYSRepository departmentBCYSRepository;

    @Autowired
    private GradingSystemService gradingSystemService;

    /**
     * Gets the complete dashboard data for a student.
     * @param userId The user ID from JWT token
     * @return StudentDashboardDTO with all dashboard information
     * @throws ResourceNotFoundException if student not found
     */
    @Transactional(readOnly = true)
    public StudentDashboardDTO getStudentDashboard(Long userId) {
        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Get student details
        StudentDetails student = studentDetailsRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Student details not found for user id: " + userId));

        // Build dashboard DTO
        StudentDashboardDTO dashboard = new StudentDashboardDTO();

        // 1. Student Profile Summary
        dashboard.setProfileSummary(buildProfileSummary(student));

        // 2. Academic Progress Snapshot
        dashboard.setAcademicProgress(buildAcademicProgress(student));

        // 3. Current Semester Courses
        dashboard.setCurrentSemesterCourses(getCurrentSemesterCourses(student));

        // 4. Recent Grades
        dashboard.setRecentGrades(getRecentGrades(student, 5));

        // 5. Document Status
        dashboard.setDocumentStatus(buildDocumentStatus(student));

        // 6. Exit Exam and Graduation Information
        dashboard.setExitExamAndGraduation(buildExitExamAndGraduation(student));

        return dashboard;
    }

    /**
     * Builds the student profile summary.
     */
    private StudentDashboardDTO.StudentProfileSummary buildProfileSummary(StudentDetails student) {
        StudentDashboardDTO.StudentProfileSummary summary = new StudentDashboardDTO.StudentProfileSummary();
        summary.setStudentId(student.getId());
        
        // Build full name from English names
        String fullName = String.format("%s %s %s",
                student.getFirstNameENG() != null ? student.getFirstNameENG() : "",
                student.getFatherNameENG() != null ? student.getFatherNameENG() : "",
                student.getGrandfatherNameENG() != null ? student.getGrandfatherNameENG() : "");
        summary.setFullName(fullName.trim());
        
        summary.setDepartment(student.getDepartmentEnrolled() != null 
                ? student.getDepartmentEnrolled().getDeptName() : null);
        summary.setProgramModality(student.getProgramModality() != null 
                ? student.getProgramModality().getModality() : null);
        summary.setCurrentClassYear(student.getBatchClassYearSemester() != null 
                && student.getBatchClassYearSemester().getClassYear() != null
                ? student.getBatchClassYearSemester().getClassYear().getClassYear() : null);
        summary.setCurrentSemester(student.getBatchClassYearSemester() != null 
                && student.getBatchClassYearSemester().getSemester() != null
                ? student.getBatchClassYearSemester().getSemester().getAcademicPeriod() : null);
        summary.setAcademicStatus(student.getStudentRecentStatus() != null 
                ? student.getStudentRecentStatus().getStatusName() : null);
        summary.setProfilePhoto(student.getStudentPhoto());
        
        return summary;
    }

    /**
     * Builds academic progress snapshot (CGPA, last semester GPA, total credit hours).
     */
    private StudentDashboardDTO.AcademicProgressSnapshot buildAcademicProgress(StudentDetails student) {
        StudentDashboardDTO.AcademicProgressSnapshot progress = new StudentDashboardDTO.AcademicProgressSnapshot();
        
        // Get all released scores
        List<StudentCourseScore> allReleasedScores = studentCourseScoreRepo
                .findByStudentAndIsReleasedTrue(student.getUser());
        
        // Calculate total completed credit hours
        int totalCreditHours = 0;
        for (StudentCourseScore score : allReleasedScores) {
            if (score.getScore() != null && score.getCourse() != null) {
                Course course = score.getCourse();
                totalCreditHours += (course.getTheoryHrs() != null ? course.getTheoryHrs() : 0) +
                                   (course.getLabHrs() != null ? course.getLabHrs() : 0);
            }
        }
        progress.setTotalCompletedCreditHours(totalCreditHours);
        
        // Calculate current CGPA (all released courses)
        Department department = student.getDepartmentEnrolled();
        GradingSystem gradingSystem = null;
        try {
            gradingSystem = gradingSystemService.findApplicableGradingSystem(department);
        } catch (Exception e) {
            // If no grading system found, set CGPA to null
        }
        
        double cgpa = calculateCGPA(allReleasedScores, gradingSystem);
        progress.setCurrentCGPA(cgpa > 0 ? cgpa : null);
        
        // Calculate last semester GPA (most recent semester with released courses)
        double lastSemesterGPA = calculateLastSemesterGPA(allReleasedScores, gradingSystem, student);
        progress.setLastSemesterGPA(lastSemesterGPA > 0 ? lastSemesterGPA : null);
        
        return progress;
    }

    /**
     * Gets current semester courses for the student.
     */
    private List<StudentDashboardDTO.CurrentSemesterCourse> getCurrentSemesterCourses(StudentDetails student) {
        List<StudentDashboardDTO.CurrentSemesterCourse> courses = new ArrayList<>();
        
        if (student.getBatchClassYearSemester() == null) {
            return courses;
        }
        
        BatchClassYearSemester currentBCYS = student.getBatchClassYearSemester();
        List<StudentCourseScore> currentScores = studentCourseScoreRepo
                .findByStudentAndBatchClassYearSemester(student.getUser(), currentBCYS);
        
        for (StudentCourseScore score : currentScores) {
            if (score.getCourse() != null) {
                StudentDashboardDTO.CurrentSemesterCourse course = new StudentDashboardDTO.CurrentSemesterCourse();
                course.setCourseCode(score.getCourse().getCCode());
                course.setCourseTitle(score.getCourse().getCTitle());
                int creditHours = (score.getCourse().getTheoryHrs() != null ? score.getCourse().getTheoryHrs() : 0) +
                                 (score.getCourse().getLabHrs() != null ? score.getCourse().getLabHrs() : 0);
                course.setCreditHours(creditHours);
                courses.add(course);
            }
        }
        
        return courses;
    }

    /**
     * Gets highest grades (top 5 highest scores).
     */
    private List<StudentDashboardDTO.RecentGrade> getRecentGrades(StudentDetails student, int limit) {
        List<StudentDashboardDTO.RecentGrade> recentGrades = new ArrayList<>();
        
        // Get all released scores
        List<StudentCourseScore> allReleasedScores = studentCourseScoreRepo
                .findByStudentAndIsReleasedTrue(student.getUser());
        
        // Sort by score (highest first) and limit to top 5
        List<StudentCourseScore> sortedScores = allReleasedScores.stream()
                .filter(score -> score.getScore() != null) // Only include scores that have values
                .sorted((s1, s2) -> {
                    Double score1 = s1.getScore();
                    Double score2 = s2.getScore();
                    if (score1 == null && score2 == null) return 0;
                    if (score1 == null) return 1;
                    if (score2 == null) return -1;
                    return score2.compareTo(score1); // Highest first
                })
                .limit(limit)
                .collect(Collectors.toList());
        
        Department department = student.getDepartmentEnrolled();
        GradingSystem gradingSystem = null;
        try {
            gradingSystem = gradingSystemService.findApplicableGradingSystem(department);
        } catch (Exception e) {
            // If no grading system found, return empty list
            return recentGrades;
        }
        
        for (StudentCourseScore score : sortedScores) {
            if (score.getScore() == null || score.getCourse() == null) {
                continue;
            }
            
            StudentDashboardDTO.RecentGrade grade = new StudentDashboardDTO.RecentGrade();
            
            // Get class year and semester
            if (score.getBatchClassYearSemester() != null) {
                BatchClassYearSemester bcys = score.getBatchClassYearSemester();
                grade.setClassYear(bcys.getClassYear() != null ? bcys.getClassYear().getClassYear() : null);
                grade.setSemester(bcys.getSemester() != null ? bcys.getSemester().getAcademicPeriod() : null);
            }
            
            grade.setCourseCode(score.getCourse().getCCode());
            grade.setCourseTitle(score.getCourse().getCTitle());
            
            // Calculate letter grade and grade point
            if (gradingSystem != null && gradingSystem.getIntervals() != null) {
                MarkInterval interval = gradingSystem.getIntervals().stream()
                        .filter(i -> score.getScore() >= i.getMin() && score.getScore() <= i.getMax())
                        .findFirst()
                        .orElse(null);
                
                if (interval != null) {
                    grade.setLetterGrade(interval.getGradeLetter());
                    int creditHours = (score.getCourse().getTheoryHrs() != null ? score.getCourse().getTheoryHrs() : 0) +
                                     (score.getCourse().getLabHrs() != null ? score.getCourse().getLabHrs() : 0);
                    grade.setGradePoint(creditHours * interval.getGivenValue());
                }
            }
            
            recentGrades.add(grade);
        }
        
        return recentGrades;
    }

    /**
     * Builds document status information.
     */
    private StudentDashboardDTO.DocumentStatusInfo buildDocumentStatus(StudentDetails student) {
        StudentDashboardDTO.DocumentStatusInfo docStatus = new StudentDashboardDTO.DocumentStatusInfo();
        
        // Registration document status
        DocumentStatus status = student.getDocumentStatus();
        docStatus.setRegistrationDocumentStatus(status != null ? status.toString() : DocumentStatus.INCOMPLETE.toString());
        
        // Student photo upload status
        String photoStatus = student.getStudentPhoto() != null && student.getStudentPhoto().length > 0 
                ? "UPLOADED" : "NOT_UPLOADED";
        docStatus.setStudentPhotoUploadStatus(photoStatus);
        
        return docStatus;
    }

    /**
     * Builds exit exam and graduation information.
     */
    private StudentDashboardDTO.ExitExamAndGraduationInfo buildExitExamAndGraduation(StudentDetails student) {
        StudentDashboardDTO.ExitExamAndGraduationInfo info = new StudentDashboardDTO.ExitExamAndGraduationInfo();

        info.setExitExamUserID(student.getExitExamUserID());
        info.setExitExamScore(student.getExitExamScore());
        info.setIsStudentPassExitExam(student.getIsStudentPassExitExam());
        info.setGrade12Result(student.getGrade12Result());
        info.setYearOfExamG12(student.getYearOfExamG12());
        info.setNationalexamIdG12(student.getNationalexamIdG12());
        info.setDateClassEndGC(student.getDateClassEndGC());
        info.setDateGraduated(student.getDateGraduated());
        info.setEntryYearGC(student.getEntryYearGC());
        info.setEntryYearEC(student.getEntryYearEC());

        return info;
    }

    /**
     * Calculates CGPA from all released scores.
     */
    private double calculateCGPA(List<StudentCourseScore> scores, GradingSystem gradingSystem) {
        if (scores == null || scores.isEmpty() || gradingSystem == null) {
            return 0.0;
        }
        
        double totalGradePoints = 0.0;
        int totalCreditHours = 0;
        
        for (StudentCourseScore score : scores) {
            if (score.getScore() == null || score.getCourse() == null) {
                continue;
            }
            
            Course course = score.getCourse();
            int creditHours = (course.getTheoryHrs() != null ? course.getTheoryHrs() : 0) +
                             (course.getLabHrs() != null ? course.getLabHrs() : 0);
            
            MarkInterval interval = gradingSystem.getIntervals().stream()
                    .filter(i -> score.getScore() >= i.getMin() && score.getScore() <= i.getMax())
                    .findFirst()
                    .orElse(null);
            
            if (interval != null) {
                totalGradePoints += creditHours * interval.getGivenValue();
                totalCreditHours += creditHours;
            }
        }
        
        if (totalCreditHours == 0) {
            return 0.0;
        }
        
        return totalGradePoints / totalCreditHours;
    }

    /**
     * Calculates last semester GPA (most recent semester with released courses).
     */
    private double calculateLastSemesterGPA(List<StudentCourseScore> allScores, GradingSystem gradingSystem, StudentDetails student) {
        if (allScores == null || allScores.isEmpty() || gradingSystem == null) {
            return 0.0;
        }
        
        // Group scores by BCYS and find the most recent one
        // Find the most recent BCYS based on the student's department-specific class start date
        BatchClassYearSemester mostRecentBCYS = allScores.stream()
                .map(score -> {
                    BatchClassYearSemester bcys = score.getBatchClassYearSemester();
                    if (bcys == null) return null;

                    Department studentDept = student.getDepartmentEnrolled();
                    if (studentDept == null) return null;

                    return departmentBCYSRepository.findByBcysAndDepartment(bcys, studentDept)
                            .map(deptBCYS -> new AbstractMap.SimpleEntry<>(bcys, deptBCYS.getClassStartGC()))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .filter(entry -> entry.getValue() != null)              // has valid date
                .max(Comparator.comparing(entry -> entry.getValue()))   // max by date
                .map(Map.Entry::getKey)                                 // get back the BCYS
                .orElse(null);
        
        if (mostRecentBCYS == null) {
            return 0.0;
        }
        
        // Get scores for the most recent semester
        List<StudentCourseScore> lastSemesterScores = allScores.stream()
                .filter(score -> score.getBatchClassYearSemester() != null 
                        && score.getBatchClassYearSemester().getBcysID().equals(mostRecentBCYS.getBcysID()))
                .collect(Collectors.toList());
        
        return calculateCGPA(lastSemesterScores, gradingSystem);
    }
}

