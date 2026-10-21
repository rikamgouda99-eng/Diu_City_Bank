package com.dcb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private String gmailId;
    private  String firstName;
    private  String middleName;
    private  String lastName;
    private UUID uid;
    private  String addressDetails;
    private String phoneNumber;
    private  String gender;
    private LocalDate dob;
    private  LocalDate crateDate;
    private  LocalDate updateDate;
    private String maritalStatus;
    private  Boolean isActive;
    private  Boolean isVerified;
    private  Boolean isApproved;
    private  String comments;
    private  String password;
    private  String userRole;

}

