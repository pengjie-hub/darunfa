package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.MeResponse;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.dto.UserDTO;
import com.example.backend.entity.Permission;
import com.example.backend.entity.Role;
import com.example.backend.entity.RolePermission;
import com.example.backend.entity.User;
import com.example.backend.repository.PermissionRepository;
import com.example.backend.repository.RolePermissionRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 认证服务：注册、登录、查询当前用户信息（含权限码）。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       PermissionRepository permissionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException(409, "该用户名已被注册");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername() : req.getNickname());
        user.setPhone(req.getPhone());
        userRepository.save(user);
        return buildLoginResponse(user);
    }

    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException(404, "该用户尚未注册"));
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "密码错误");
        }
        return buildLoginResponse(user);
    }

    /**
     * 查询当前登录用户的信息与权限码列表。
     *
     * @param userId 来自 token 的 userId（拦截器在 /api/auth/** 之外会校验；
     *               此接口在 /api/auth/** 下，controller 自己解析 token 拿 userId）
     */
    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "登录凭证无效或已过期"));
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }
        String roleName = user.getRole() == null ? "USER" : user.getRole().name();
        List<String> perms = loadPermissionCodesByRoleName(roleName);
        return new MeResponse(user.getId(), user.getUsername(), user.getNickname(),
                user.getPhone(), user.getEmail(), roleName, perms);
    }

    /** 根据角色名加载其全部权限码（"product:view" 等）；空集表示无任何权限。 */
    private List<String> loadPermissionCodesByRoleName(String roleName) {
        List<String> codes = new ArrayList<>();
        Optional<Role> opt = roleRepository.findByCode(roleName);
        if (opt.isEmpty()) return codes;
        List<RolePermission> rps = rolePermissionRepository.findAllByRoleId(opt.get().getId());
        if (rps.isEmpty()) return codes;
        List<Long> pids = rps.stream().map(RolePermission::getPermissionId).toList();
        for (Permission p : permissionRepository.findAllById(pids)) {
            if (p.getCode() != null) codes.add(p.getCode());
        }
        return codes;
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());
        return new LoginResponse(token, UserDTO.from(user));
    }
}
