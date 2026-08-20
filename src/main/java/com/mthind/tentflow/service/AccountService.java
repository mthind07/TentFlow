package com.mthind.tentflow.service;

import com.mthind.tentflow.api.dto.CreateUserAccountRequest;
import com.mthind.tentflow.api.dto.RegisterRequest;
import com.mthind.tentflow.api.dto.UserAccountResponse;
import com.mthind.tentflow.audit.AuditService;
import com.mthind.tentflow.exception.DuplicateResourceException;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.persistence.entity.CustomerEntity;
import com.mthind.tentflow.persistence.entity.UserAccountEntity;
import com.mthind.tentflow.persistence.repository.CustomerRepository;
import com.mthind.tentflow.persistence.repository.UserAccountRepository;
import com.mthind.tentflow.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

@Service
public class AccountService {

    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public AccountService(
            UserAccountRepository userAccountRepository,
            CustomerRepository customerRepository,
            BookingWorkflowService bookingWorkflowService,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.customerRepository = customerRepository;
        this.bookingWorkflowService = bookingWorkflowService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public UserAccountResponse registerCustomer(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        rejectExistingEmail(email);

        Customer customer = bookingWorkflowService.registerCustomer(
                request.fullName(),
                email,
                request.phone()
        );
        CustomerEntity customerEntity = customerRepository
                .findById(customer.getId())
                .orElseThrow();

        UserAccountEntity account = saveAccount(
                email,
                request.password(),
                Role.CUSTOMER,
                customerEntity
        );

        auditService.successAs(
                account.getId(),
                account.getEmail(),
                "REGISTER_CUSTOMER_ACCOUNT",
                "USER_ACCOUNT",
                account.getId(),
                null
        );
        return toResponse(account);
    }

    @Transactional
    public UserAccountResponse createPrivilegedAccount(
            CreateUserAccountRequest request
    ) {
        if (request.role() == Role.CUSTOMER) {
            throw new IllegalArgumentException(
                    "Customer accounts must use the public registration flow."
            );
        }

        String email = normalizeEmail(request.email());
        rejectExistingEmail(email);
        UserAccountEntity account = saveAccount(
                email,
                request.password(),
                request.role(),
                null
        );
        auditService.success(
                "CREATE_USER_ACCOUNT",
                "USER_ACCOUNT",
                account.getId(),
                "role=" + account.getRole()
        );
        return toResponse(account);
    }

    @Transactional
    public void ensureBootstrapAdministrator(
            String emailValue,
            String password
    ) {
        String email = normalizeEmail(emailValue);
        UserAccountEntity existing = userAccountRepository
                .findDetailedByEmail(email)
                .orElse(null);

        if (existing != null) {
            if (existing.getRole() != Role.ADMIN) {
                throw new IllegalStateException(
                        "The bootstrap administrator email belongs to "
                                + "a non-administrator account."
                );
            }
            return;
        }

        validatePassword(password);
        UserAccountEntity account = saveAccount(
                email,
                password,
                Role.ADMIN,
                null
        );
        auditService.successAs(
                account.getId(),
                account.getEmail(),
                "BOOTSTRAP_ADMIN",
                "USER_ACCOUNT",
                account.getId(),
                null
        );
    }

    @Transactional(readOnly = true)
    public UserAccountEntity requireByEmail(String email) {
        return userAccountRepository.findDetailedByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account no longer exists."
                ));
    }

    @Transactional(readOnly = true)
    public List<UserAccountResponse> getAllAccounts() {
        return userAccountRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAccountResponse getAccount(long accountId) {
        return userAccountRepository.findById(accountId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account " + accountId + " was not found."
                ));
    }

    public UserAccountResponse toResponse(UserAccountEntity account) {
        return new UserAccountResponse(
                Objects.requireNonNull(account.getId()),
                account.getEmail(),
                account.getRole(),
                account.getCustomer() == null
                        ? null
                        : account.getCustomer().getId(),
                account.isEnabled(),
                account.getCreatedAt()
        );
    }

    private UserAccountEntity saveAccount(
            String email,
            String password,
            Role role,
            CustomerEntity customer
    ) {
        validatePassword(password);
        UserAccountEntity account = new UserAccountEntity(
                email,
                passwordEncoder.encode(password),
                role,
                customer,
                Instant.now(clock)
        );

        try {
            return userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw duplicateEmail(email);
            }
            throw exception;
        }
    }

    private void rejectExistingEmail(String email) {
        if (userAccountRepository.existsByNormalizedEmail(email)) {
            throw duplicateEmail(email);
        }
    }

    private DuplicateResourceException duplicateEmail(String email) {
        return new DuplicateResourceException(
                "User account email " + email + " already exists."
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters and "
                            + "at most 72 UTF-8 bytes."
            );
        }
    }

    private boolean isUniqueViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException
                && "23505".equals(sqlException.getSQLState());
    }
}