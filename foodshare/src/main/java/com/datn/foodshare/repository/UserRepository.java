package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneOrEmail(String phone, String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findByRole(Role role);

    Optional<User> findFirstByRole(Role role);

    List<User> findByRoleIn(Collection<Role> roles);

    @Query("""
            SELECT u FROM User u
            WHERE u.role IN :roles
              AND u.active = true
              AND u.profileCompleted = true
              AND u.latitude IS NOT NULL
              AND u.longitude IS NOT NULL
            """)
    List<User> findEligibleMatchingCandidates(Collection<Role> roles);
}
