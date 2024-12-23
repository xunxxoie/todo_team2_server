package org.todo.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.todo.global.error.CustomErrorCode;
import org.todo.global.exception.CustomJwtException;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_EXPIRED_TIME = 2 * 60 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRED_TIME = 3 * 24 * 60 * 60 * 1000L;

    private final Key key;

    private final UserDetailsService userDetailsService;

    public JwtTokenProvider(@Value("${jwt.secret}")String jwtSecret, UserDetailsService userDetailsService){
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        this.userDetailsService = userDetailsService;
    }

    public String generateAccessToken(String username){
        Date now = new Date();
        Claims claims = Jwts.claims()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRED_TIME));

        return Jwts.builder()
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username){
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRED_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getJwtFromRequest(HttpServletRequest request){
        return request
                .getHeader("Authorization")
                .substring(7);
    }

    public boolean validateToken(String token) {
        if(token == null) {
            throw new JwtException("Jwt AccessToken not found");
        }

        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return true;
        } catch (ExpiredJwtException e) {
            throw new CustomJwtException(CustomErrorCode.JWT_EXPIRED);
        } catch (MalformedJwtException e) {
            throw new CustomJwtException(CustomErrorCode.JWT_MALFORMED);
        } catch (SignatureException | SecurityException e) {
            throw new CustomJwtException(CustomErrorCode.JWT_SIGNATURE);
        } catch (UnsupportedJwtException e) {
            throw new CustomJwtException(CustomErrorCode.JWT_UNSUPPORTED);
        }
    }

    public Authentication getAuthenticationJwt(String token){
        UserDetails userDetails = userDetailsService.loadUserByUsername(getUsernameFromJwt(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getUsernameFromJwt(String token){
        return Jwts
                .parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
