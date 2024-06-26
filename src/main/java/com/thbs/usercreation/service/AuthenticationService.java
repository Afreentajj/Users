package com.thbs.usercreation.service;


import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.thbs.usercreation.dto.AuthenticationRequest;
import com.thbs.usercreation.dto.AuthenticationResponse;
import com.thbs.usercreation.dto.EmailRequest;
import com.thbs.usercreation.dto.RegisterRequest;
import com.thbs.usercreation.dto.VerifyPasswordToken;
import com.thbs.usercreation.entity.Token;
import com.thbs.usercreation.entity.User;
import com.thbs.usercreation.enumerate.Role;
import com.thbs.usercreation.enumerate.TokenType;
import com.thbs.usercreation.repository.TokenRepository;
import com.thbs.usercreation.repository.UserRepo;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepo repository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService  emailService;

    // Method to handle user registration
    public AuthenticationResponse register(RegisterRequest request) {
        // Check if a user with the given email already exists
        if (repository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with the given email already exists");
        }

        // Create a new user entity based on the registration request
        Role role = request.getRole() != null ? request.getRole() : Role.USER;

        var user = User.builder()
            .firstName(request.getFirstname())
            .lastName(request.getLastname())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .isemailverified(false)
            .role(role)
            .businessUnit(request.getBusinessUnit())
            .employeeId(request.getEmployeeId())
            .build();

        // Save the user to the repository
        var savedUser = repository.save(user);

        // Generate JWT token for the user
        var jwtToken = jwtService.generateToken(user);

    String verificationUrl = "http://localhost:4321/api/v1/auth/verifyEmailToken?token=" + jwtToken;
    emailService.sendEmail(request.getEmail(),"email verification", verificationUrl);
    System.out.println("-------------------"+verificationUrl);
        // Save the user's token in the repository
        saveUserToken(savedUser, jwtToken);

        // Return the authentication response containing the token
        return AuthenticationResponse.builder()
            .accessToken(jwtToken)
            .message("Registration successful but email has to be verified ")
            .build();
    }

    // Method to handle user authentication
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Authenticate user using Spring Security's authentication manager
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        // Retrieve user details from the repository
        var user = repository.findByEmail(request.getEmail())
            .orElseThrow();

            String message="";
        if(user.isIsemailverified()){
          message="successfully login";
        }else{
          message="email has to be verfied";
        }

        // Generate JWT token for the user
        var jwtToken = jwtService.generateToken(user);

        // Revoke all existing user tokens
        revokeAllUserTokens(user);

        // Save the user's new token in the repository
        saveUserToken(user, jwtToken);

        // Return the authentication response containing the token
        return AuthenticationResponse.builder()
            .accessToken(jwtToken)
            .message(message)
            .build();
    }


    public ResponseEntity<String> verifyEmailToken( String token) {
        System.out.println("+++++++######++++++++"+token);
    if(!jwtService.isTokenExpired(token)){
      String email=jwtService.extractUsername(token);
      User user = repository.findByEmail(email)
            .orElseThrow();
            user.setEmailVerified(true);
            repository.save(user);
      
        return ResponseEntity.ok("Email verified successfully");
    }
    return ResponseEntity.badRequest().body("Invalid token or user already verified");
    
        
    }

    // public ResponseEntity<String> forgotPassword(EmailRequest emails,HttpServletResponse response) {
    //     System.out.println("$$$$$$$$$$"+emails.getEmail());
    //     User user = repository.findByEmail(emails.getEmail()).orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + emails.getEmail()));
    
        
    //     if (user != null) {
    //         String jwt = jwtService.generateToken(user);

    //         // write react applications forgot passwords url
    //         // String verificationUrl = "http://localhost:4321/api/v1/auth/generatepassword?token=" + jwt;
    //         String resetPasswordUrl = "http://your-frontend-url/reset-password";
    //         response.setHeader("X-Token", jwt); // Set the token in a custom header
    //         // return ResponseEntity.status(HttpStatus.FOUND)
    //         //         .header(HttpHeaders.LOCATION, "http://your-react-app-url/reset-password")
    //         //         .build();
    //         response.setHeader(HttpHeaders.LOCATION, resetPasswordUrl);
    //         emailService.sendEmail(emails.getEmail(), "forgot password", resetPasswordUrl);
    //         return ResponseEntity.status(HttpStatus.OK).body("Link sent to your email for reset password");
    //     }
    
    //     return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not exists");
    // }

    public AuthenticationResponse forgotPassword( EmailRequest emails) {
        System.out.println("$$$$$$$$$$"+emails.getEmail());
        User user = repository.findByEmail(emails.getEmail()).orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + emails.getEmail()));
    
        
        if (user != null) {
            String jwt = jwtService.generateToken(user);
            String verificationUrl = "http://172.18.5.13:5173/enter-new-password?token=" + jwt;
            emailService.sendEmail(emails.getEmail(), "forgot password", verificationUrl);
            return AuthenticationResponse.builder()
            .accessToken(jwt)
            .message("link sent to your email for forgot password")
            .build();
        }
    
    return AuthenticationResponse.builder()
        .accessToken("")
        .message("User not exixts")
        .build();
    }

    public ResponseEntity<String> verifypassword(VerifyPasswordToken token) {
        if(!jwtService.isTokenExpired(token.getToken())){
            return ResponseEntity.ok("token validated successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token is expired.");
        }
      
      }

    public ResponseEntity<String> resetPassword( String token,String newPassword) {

        if(!jwtService.isTokenExpired(token)){
            String email = jwtService.extractUsername(token);
            User user = repository.findByEmail(email).orElseThrow();
            revokeAllUserTokens(user);
        
           
            user.setPassword(passwordEncoder.encode(newPassword));
            
            // user.setPassword(newPassword);
            user = repository.save(user);

      
            return ResponseEntity.ok("RESET PASSWORD successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token is expired.");
        }
      }


      public ResponseEntity<String> changePassword( String email, String oldPassword,String newPassword) {
        // String oldpasswordencoded= passwordEncoder.encode(oldPassword);
        // Optional<User> userOptional = repository.findByUsernameAndPassword(email, passwordEncoder.matches(newPassword, oldPassword)  oldPassword);
        Optional<User> userOptional = repository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if(passwordEncoder.matches(oldPassword, user.getPassword())){
                String encodednewpassword=passwordEncoder.encode(newPassword);
                user.setPassword(encodednewpassword);
            repository.save(user);
            return ResponseEntity.status(HttpStatus.OK).body("Password changed successfully for " + email);
            }else{
                return ResponseEntity.status(HttpStatus.OK).body("password doesnt match");
            }
            
            
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email ");
        }
      }

    // Method to save user token in the repository
    private void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
            .user(user)
            .token(jwtToken)
            .tokenType(TokenType.BEARER)
            .expired(false)
            .revoked(false)
            .build();
        tokenRepository.save(token);
    }

    // Method to revoke all existing user tokens
    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty()) {
            return;
        }
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }
}

