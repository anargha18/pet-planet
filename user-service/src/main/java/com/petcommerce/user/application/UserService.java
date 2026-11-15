package com.petcommerce.user.application;

import com.petcommerce.user.api.dto.ChangePasswordRequest;
import com.petcommerce.user.api.dto.CreateUserRequest;
import com.petcommerce.user.api.dto.UpdateProfileRequest;
import com.petcommerce.user.api.dto.UpdateUserRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.api.mapper.UserMapper;
import com.petcommerce.user.domain.exception.LastAdminException;
import com.petcommerce.user.domain.exception.UserAlreadyExistsException;
import com.petcommerce.user.domain.exception.UserNotFoundException;
import com.petcommerce.user.domain.model.Role;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.domain.policy.EmailPolicy;
import com.petcommerce.user.persistence.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return createAccount(request.email(), request.password(), request.firstName(), request.lastName(), request.role());
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Integer id) {
        return UserMapper.toResponse(userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public UserResponse findActiveById(Integer id) {
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(id));
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserMapper::toResponse);
    }

    @Transactional
    public UserResponse update(Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (isLastAdminBeingRemoved(user, request.role(), request.active())) {
            throw new LastAdminException();
        }
        if (request.email() != null) {
            userRepository.findByEmailIgnoreCase(EmailPolicy.normalize(request.email()))
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException();
                    });
        }
        user.updateProfile(
                request.email() == null ? null : EmailPolicy.normalize(request.email()),
                request.firstName() == null ? null : request.firstName().trim(),
                request.lastName() == null ? null : request.lastName().trim(),
                request.role(),
                request.active()
        );
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateProfile(Integer id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.updateProfile(
                EmailPolicy.normalize(request.email()),
                request.firstName().trim(),
                request.lastName().trim(),
                null,
                null
        );
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Integer id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (isLastAdminBeingRemoved(user, Role.USER, false)) {
            throw new LastAdminException();
        }
        user.deactivate();
        userRepository.save(user);
    }

    /** Creates an account with the given role; used by admin creation and by self-registration. */
    public UserResponse createAccount(String email, String password, String firstName, String lastName, Role role) {
        String normalizedEmail = EmailPolicy.normalize(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException();
        }
        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                firstName.trim(),
                lastName.trim(),
                role
        );
        return UserMapper.toResponse(userRepository.save(user));
    }

    private boolean isLastAdminBeingRemoved(User user, Role nextRole, Boolean nextActive) {
        boolean demoted = nextRole != null && nextRole != Role.ADMIN;
        boolean deactivated = Boolean.FALSE.equals(nextActive);
        return user.isActive()
                && user.getRole() == Role.ADMIN
                && (demoted || deactivated)
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1;
    }
}
