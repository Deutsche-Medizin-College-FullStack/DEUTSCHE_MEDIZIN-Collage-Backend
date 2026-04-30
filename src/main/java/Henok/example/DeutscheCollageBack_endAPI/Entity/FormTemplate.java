package Henok.example.DeutscheCollageBack_endAPI.Entity;

import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// FormTemplate Entity
// Represents reusable PDF form templates used in the college system
// Examples: withdrawal request form, grade change request, exam retake application, etc.
// We enforce PDF-only format both on frontend and backend.
@Entity
@Table(name = "form_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique name/identifier of the form (e.g., "request_withdrawal", "grade_change_request")
    // Why: Used as a key in business logic, APIs, and frontend routing
    @Column(unique = true, nullable = false)
    private String name;

    // Human-readable description of what the form is used for
    // Why: Helps users/admins understand purpose without opening the file
    @Column
    private String description;

    // The actual content of the PDF form file
    // Why: @Lob for large binary objects; PDFs are typically small to medium size
    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")  // ← add this
    private byte[] fileContent;

    // We no longer store fileType as a separate column
    // Reason: We strictly enforce PDF only → always "application/pdf"
    // We validate MIME type and/or magic bytes on upload instead

    // Timestamp when this form template was created (for auditing)
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Timestamp of the last update (for auditing)
    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Roles allowed to access / use / see this form
    // Example: withdrawal form → only STUDENT
    //          grade change → TEACHER, DEPARTMENT_HEAD
    @ElementCollection(targetClass = Role.class)
    @CollectionTable(
            name = "form_template_roles",
            joinColumns = @JoinColumn(name = "form_template_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> forRoles = new HashSet<>();

    // Whether this form template is downloadable by users
    // nullable: can be null/undefined, false, or true
    @Column(nullable = true)
    private Boolean isDownloadable;

}