package com.dcb.service;

import com.dcb.entity.UserEntity;
import com.dcb.model.ApproveUserRequest;
import com.dcb.model.UserResponse;

import java.util.List;

public interface AdminService {
    UserResponse getUserDetails(String bankId);
    List<UserEntity> getPendingApprovals();
    boolean approvePendingUsers(ApproveUserRequest request);
    void deActivateUser(String bankId);
    void activateUser(String bankId);
}