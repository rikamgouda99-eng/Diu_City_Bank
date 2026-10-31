package com.dcb.repository;

import com.dcb.entity.ProfilePhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfilePhotoRepo extends JpaRepository<ProfilePhotoEntity, String> {
}
