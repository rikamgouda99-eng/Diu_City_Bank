package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.ApproveUserRequest;
import com.dcb.model.UserResponse;
import com.dcb.service.AdminService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final Util util;

    //get admin profile
    @GetMapping
    public ResponseEntity<APIResponse<?>> profile(
            @AuthenticationPrincipal UserDetails userDetails
    ){
        UserResponse response = adminService.getUserDetails(userDetails.getUsername());
        return ResponseEntity.ok(util.getSuccessResponse(response,"Success"));
    }

    // get all pending approvals
    @GetMapping("/approvals")
    public ResponseEntity<APIResponse<?>> getApprovalRequest(){
        return ResponseEntity.ok(util.getSuccessResponse(adminService.getPendingApprovals(),"Successfully fetched pending users for approvals."));
    }

    //approve a particular user and open account or reject the request
    @PostMapping("/approve")
    public ResponseEntity<APIResponse<?>> approveUsers(
            @RequestBody @Valid ApproveUserRequest request
    ){
        boolean isAccountOpened = adminService.approvePendingUsers(request);
        if(isAccountOpened)
            return ResponseEntity.status(HttpStatus.CREATED).body(util.getSuccessResponse(null, "Account opening request approved for bank Id: "+request.getBankId()));
        else
            return ResponseEntity.ok(util.getSuccessResponse(null, "Account opening request rejected for bank Id: "+request.getBankId()));
    }

    // deactivate a particular user
    @GetMapping("/deactivate")
    public ResponseEntity<APIResponse<?>> blockUser(
            @RequestParam String bankId
    ) {
        adminService.deActivateUser(bankId);
        return ResponseEntity.ok(util.getSuccessResponse(null, "User deactivated successfully."));
    }

    //activate a particular user
    @GetMapping("/activate")
    public ResponseEntity<APIResponse<?>> unBlockUser(
            @RequestParam String bankId
    ) {
        adminService.activateUser(bankId);
        return ResponseEntity.ok(util.getSuccessResponse(null, "User activated successfully."));
    }

}
