package com.tulas.canteen.repo;

import com.tulas.canteen.domain.CanteenSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<CanteenSettings, Long> {
}
