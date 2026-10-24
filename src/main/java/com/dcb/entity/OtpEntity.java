package com.dcb.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_entity")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OtpEntity {
    @Id
    private String id;
    private String otp;
    private LocalDateTime expiry;

}
