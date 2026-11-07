package com.tulas.canteen.repo;

import com.tulas.canteen.domain.AppSecret;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSecretRepository extends JpaRepository<AppSecret, String> {
}
