package com.petcommerce.user.application;

import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.domain.model.Role;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.domain.exception.LastAdminException;
import com.petcommerce.user.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registrationAlwaysCreatesRegularUser() {
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.register(new RegisterRequest(
                " Person@Example.com ", "password123", "Person", "User"
        ));

        assertEquals("person@example.com", response.email());
        assertEquals("USER", response.role());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        User admin = new User("admin@example.com", "encoded", "Admin", "User", Role.ADMIN);
        when(userRepository.findById(1)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(LastAdminException.class, () -> userService.deactivate(1));

        verify(userRepository, never()).save(any(User.class));
    }
}
