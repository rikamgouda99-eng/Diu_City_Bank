package com.dcb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "profile_photo")
public class ProfilePhotoEntity {

    @Id
    @Column(name = "bank_id")
    private String bankId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_photo_user"))
    @ToString.Exclude
    private UserEntity user;

    @Column(nullable = false, length = 30)
    private String contentType;

    private String fileName;

    private Long sizeBytes;

    // length > 16MB makes Hibernate emit LONGBLOB on MySQL
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 20_000_000)
    @ToString.Exclude
    private byte[] data;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;
}
