package com.tolanguage.tolanguage.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.tolanguage.tolanguage.config.security.AppUserDetails;
import com.tolanguage.tolanguage.config.security.JwtProvider;
import com.tolanguage.tolanguage.model.dto.SignInDTO;
import com.tolanguage.tolanguage.model.dto.SignUpDTO;
import com.tolanguage.tolanguage.model.dto.TokenDTO;
import com.tolanguage.tolanguage.model.dto.VerifyDTO;
import com.tolanguage.tolanguage.model.entity.User;
import com.tolanguage.tolanguage.model.enums.Role;
import com.tolanguage.tolanguage.model.exception.IllegalCheckCodeException;
import com.tolanguage.tolanguage.model.exception.InvalidCredentialException;
import com.tolanguage.tolanguage.model.exception.UserNotFoundException;
import com.tolanguage.tolanguage.repository.UserRepository;
import com.tolanguage.tolanguage.util.HashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final Cache<UUID, User> userCache;
    private final JwtProvider jwtProvider;
    private final HashService hashService;

    public void signUp(SignUpDTO signUpDTO) {
        String passwordHash = hashService.hashSHA1(signUpDTO.password());

        User user = new User(
                UUID.randomUUID(),
                signUpDTO.name(),
                signUpDTO.email(),
                passwordHash,
                Instant.now(),
                Instant.now(),
                Role.USER,
                false
        );

        userRepository.save(user);
    }

    public void verifyUser(VerifyDTO verifyDTO) {

        Optional<User> optUser = userRepository.findByEmail(verifyDTO.email(), false);

        if (optUser.isEmpty()) {
            throw new UserNotFoundException("unknown user");
        }

        User user = optUser.get();

        if (!verifyDTO.checkCode().equals(hashService.hashSHA1(user.name()))) {
            throw new IllegalCheckCodeException("illegal check code");
        }

        userRepository.updateIsEnabled(user.id());
    }

    public TokenDTO signIn(SignInDTO signInDTO) {
        String passwordHash = hashService.hashSHA1(signInDTO.password());

        User user = userRepository.findByCredentials(signInDTO.email(), passwordHash)
                .orElseThrow(() -> {
                    log.error(String.format("user not found by credentials [%s, %s]", signInDTO.email(), signInDTO.password()));
                    throw new InvalidCredentialException("user not found by credentials");
                });

        userCache.put(user.id(), user);
        String token = jwtProvider.generateToken(user.id().toString());
        log.info(String.format("user %s has signed in", user.name()));
        return new TokenDTO(token);
    }

    public User getUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AppUserDetails details = (AppUserDetails) authentication.getPrincipal();
        return getById(details.getId());
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email, true)
                .orElseThrow(() -> {
                    log.error(String.format("user not found by email [%s]", email));
                    throw new UserNotFoundException("user not found by id");
                });
    }

    public User getById(UUID id) {
        return userCache.get(id, key ->
                userRepository.findById(id)
                        .orElseThrow(() -> {
                            log.error(String.format("user not found by id [%s]", id));
                            throw new UserNotFoundException("User not found by id");
                        })
        );
    }
}
