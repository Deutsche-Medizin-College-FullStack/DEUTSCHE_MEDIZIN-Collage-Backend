package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.UserRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.NotificationRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    @Transactional(rollbackFor = Exception.class)
    public User registerUser(UserRegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (request.getRole() == null) {
            throw new IllegalArgumentException("Role cannot be empty");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    // Enables a user account by setting the enabled flag to true.
    // Why: Allows reactivation of disabled accounts.
    // Also sets other flags to active state for completeness.
    public void enableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        userRepository.save(user);
    }

    // Disables a user account by setting the enabled flag to false.
    // Why: For temporary suspension without deleting the record.
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setEnabled(false);
        userRepository.save(user);
    }

    // Changes the password for the authenticated user (student self-service)
    // Why: Allows users to update their own password securely
    // Security: Verifies old password matches hashed version, encodes new password with BCrypt
    @Transactional
    public void changeSelfPassword(String username, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("New password cannot be empty");
        }
        User user = (User) loadUserByUsername(username);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // Resets the password for a student (admin action by registrar)
    // Why: Allows registrars to reset student passwords without old password
    // Security: Ensures target user exists and has STUDENT role, encodes new password with BCrypt
    @Transactional
    public void resetStudentPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("New password cannot be empty");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        if (user.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Can only reset passwords for students");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }


    // Resets password for a specific user (admin action)
    // Why: Allows privileged users to reset without old password
    // Security: Encodes new password, optional role check
    @Transactional
    public void resetUserPassword(Long userId, String newPassword, Role expectedRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Optional role validation
        if (expectedRole != null && user.getRole() != expectedRole) {
            throw new IllegalArgumentException("Can only reset passwords for " + expectedRole.name().toLowerCase() + "s");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }


    // Deletes a user by ID along with all their associated notifications
    // Why: Prevents foreign key constraint violations when deleting a User that has notifications
    // Order: First delete notifications (child records), then delete the User (parent)
    // All operations run in a single transaction — rollback on any failure
    @Transactional
    public void deleteUserById(Long userId) {
        // 1. Input validation
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // 2. Check if user exists (early exit if not found)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        try {
            // 3. Delete all notifications for this user first
            // Why first? Notifications reference user_id (FK constraint)
            notificationRepository.deleteByUser(user);

            // 4. Now safe to delete the user
            userRepository.delete(user);

        } catch (DataIntegrityViolationException e) {
            // Catch any remaining constraint violations (e.g. from other related tables)
            throw new DataIntegrityViolationException(
                    "Cannot delete user — there are still references to this user in other tables", e);
        } catch (Exception e) {
            // Catch-all for unexpected runtime issues
            throw new RuntimeException("Unexpected error while deleting user with id: " + userId, e);
        }
    }

    // Retrieves only userId and username for all users with STUDENT role
    // Why: Optimized query — avoids loading full User objects or related entities
    // Returns List<Map<String, Object>> so no DTO is needed
    // Uses projection to keep it lightweight
    public List<Map<String, Object>> getAllStudentUsernamesAndIds() {
        // Using a custom query with projection (very efficient)
        List<Object[]> results = userRepository.findUserIdAndUsernameByRole(Role.STUDENT);

        return results.stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("userId", row[0]);
                    map.put("username", row[1]);
                    return map;
                })
                .collect(Collectors.toList());
    }
}


