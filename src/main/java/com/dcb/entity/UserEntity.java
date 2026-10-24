package com.dcb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@Table(name="user_login_table")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity
{
    @Id
    @Column(name="bank_id") //for customized column Name
    private  String bankId;
    private String mailId;
    private  String firstName;
    private  String middleName;
    private  String lastName;
//    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uid;
    private  String addressDetails;
    private String phoneNumber;
    private  String gender;
    private LocalDate dob;
    private  LocalDate createdDate;
    private  LocalDate updatedDate;
    private String maritalStatus;
    private  Boolean isActive;
    private  Boolean isVerified;
    private  Boolean isApproved;
    private  String status;
    private  String password;
    private  String role;

}

