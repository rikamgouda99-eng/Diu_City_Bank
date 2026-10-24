package com.dcb.service;

import com.dcb.entity.UserEntity;
import com.dcb.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String bankId) throws UsernameNotFoundException {
        Optional<UserEntity> userOpt = userRepo.findById(bankId);
        if(userOpt.isEmpty()){
            log.info("User not exists with bankId: {}",bankId);
            throw new UsernameNotFoundException("User Not Found");
        }
        UserEntity user = userOpt.get();
        return User.builder()
                .username(user.getBankId())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}