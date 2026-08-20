package com.mthind.tentflow.security;

import com.mthind.tentflow.persistence.entity.UserAccountEntity;
import com.mthind.tentflow.persistence.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public DatabaseUserDetailsService(
            UserAccountRepository userAccountRepository
    ) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        UserAccountEntity account = userAccountRepository
                .findDetailedByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid email or password."
                ));

        return new TentFlowPrincipal(
                account.getId(),
                account.getEmail(),
                account.getPasswordHash(),
                account.getRole(),
                account.getCustomer() == null
                        ? null
                        : account.getCustomer().getId(),
                account.isEnabled()
        );
    }
}