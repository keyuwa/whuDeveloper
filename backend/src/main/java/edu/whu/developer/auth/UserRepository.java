package edu.whu.developer.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRoleOrderByIdAsc(Role role);

    List<User> findTop50ByRoleOrderByPointsDescIdAsc(Role role);

    @Query("""
            select count(u) from User u
            where u.role = edu.whu.developer.auth.Role.STUDENT
              and (u.points > :points or (u.points = :points and u.id < :userId))
            """)
    long countStudentsAhead(@Param("points") int points, @Param("userId") Long userId);
}
