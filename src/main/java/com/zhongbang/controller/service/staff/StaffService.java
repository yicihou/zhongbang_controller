package com.zhongbang.controller.service.staff;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.auth.SysUserMapper;
import com.zhongbang.controller.mapper.technician.TechnicianMapper;
import com.zhongbang.controller.pojo.auth.entity.SysUser;
import com.zhongbang.controller.pojo.staff.dto.StaffSaveRequest;
import com.zhongbang.controller.pojo.staff.vo.StaffMemberVo;
import com.zhongbang.controller.pojo.technician.entity.Technician;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 员工和师傅放在同一个列表里管。
 * 管理员、业务员写在 {@code sys_user}，必须有用户名和密码。
 * 师傅写在 {@code technician}，可以不登录；填了用户名和密码才会同时建一条登录账号，两边用 id 互相关联。
 * 停用师傅后，报修和安装派工都会拒绝。
 *
 * <p>每个会改数据的方法都先确认当前人是管理员。普通员工令牌进得了别的管理接口，进不了这里。
 */
@Service
public class StaffService {

    private static final Set<String> ROLES = Set.of("ADMIN", "SALES", "TECHNICIAN");

    private final SysUserMapper sysUserMapper;
    private final TechnicianMapper technicianMapper;
    private final PasswordEncoder passwordEncoder;

    public StaffService(SysUserMapper sysUserMapper, TechnicianMapper technicianMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.technicianMapper = technicianMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<StaffMemberVo> list(String keyword, String role, String status, int page, int size) {
        requireAdmin();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        List<StaffMemberVo> all = loadAll();
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String roleFilter = blankToNull(role);
        String statusFilter = blankToNull(status);
        List<StaffMemberVo> filtered = all.stream().filter(row -> {
            if (roleFilter != null && !roleFilter.equalsIgnoreCase(row.role())) {
                return false;
            }
            if (statusFilter != null && !statusFilter.equalsIgnoreCase(row.status())) {
                return false;
            }
            if (key.isEmpty()) {
                return true;
            }
            return contains(row.name(), key)
                    || contains(row.mobile(), key)
                    || contains(row.username(), key)
                    || contains(row.remark(), key);
        }).toList();
        int from = Math.min((p - 1) * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        return PageResult.of(filtered.subList(from, to), filtered.size(), p, s);
    }

    @Transactional
    public StaffMemberVo create(StaffSaveRequest request) {
        requireAdmin();
        Parsed parsed = parse(request, true, null);
        if ("TECHNICIAN".equals(parsed.role)) {
            Technician tech = newTechnician(parsed);
            technicianMapper.insert(tech);
            if (parsed.username != null) {
                SysUser user = newUser(parsed, tech.getId());
                insertUser(user);
                tech.setUserId(user.getId());
                tech.setUpdatedAt(DateTimes.now());
                technicianMapper.updateById(tech);
                return toVo(user, tech);
            }
            return toVo(null, tech);
        }
        SysUser user = newUser(parsed, null);
        insertUser(user);
        return toVo(user, null);
    }

    @Transactional
    public StaffMemberVo update(String ref, StaffSaveRequest request) {
        requireAdmin();
        Located located = locate(ref);
        Parsed parsed = parse(request, false, located.user);
        LocalDateTime now = DateTimes.now();
        if (located.user != null) {
            return updateUser(located, parsed, now);
        }
        return updateTechOnly(located.tech, parsed, now);
    }

    @Transactional
    public StaffMemberVo changeStatus(String ref, String status) {
        requireAdmin();
        String next = normalizeStatus(status);
        Located located = locate(ref);
        LocalDateTime now = DateTimes.now();
        if (located.user != null) {
            assertNotSelf(located.user, next);
            assertKeepsAdmin(located.user, located.user.getRole(), next);
            located.user.setStatus(next);
            located.user.setUpdatedAt(now);
            sysUserMapper.updateById(located.user);
            Technician tech = linkedTech(located.user, located.tech);
            if (tech != null && "TECHNICIAN".equals(located.user.getRole())) {
                tech.setStatus(next);
                tech.setUpdatedAt(now);
                technicianMapper.updateById(tech);
            }
            return toVo(reloadUser(located.user.getId()), tech);
        }
        located.tech.setStatus(next);
        located.tech.setUpdatedAt(now);
        technicianMapper.updateById(located.tech);
        return toVo(null, located.tech);
    }

    @Transactional
    public void resetPassword(String ref, String password) {
        requireAdmin();
        String raw = checkPassword(password);
        Located located = locate(ref);
        SysUser user = located.user;
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这个员工还没有登录账号，请先填写用户名和密码");
        }
        user.setPassword(passwordEncoder.encode(raw));
        user.setUpdatedAt(DateTimes.now());
        sysUserMapper.updateById(user);
    }

    private StaffMemberVo updateUser(Located located, Parsed parsed, LocalDateTime now) {
        SysUser user = located.user;
        assertNotSelf(user, parsed.status);
        assertKeepsAdmin(user, parsed.role, parsed.status);
        if (parsed.username != null && !parsed.username.equals(user.getUsername())) {
            assertUsernameFree(parsed.username);
            user.setUsername(parsed.username);
        }
        if (parsed.password != null) {
            user.setPassword(passwordEncoder.encode(parsed.password));
        }
        user.setName(parsed.name);
        user.setMobile(parsed.mobile);
        user.setRemark(parsed.remark);
        user.setStatus(parsed.status);
        user.setRole(parsed.role);
        user.setUpdatedAt(now);
        Technician tech = linkedTech(user, located.tech);
        if ("TECHNICIAN".equals(parsed.role)) {
            if (tech == null) {
                tech = newTechnician(parsed);
                tech.setUserId(user.getId());
                tech.setStatus(parsed.status);
                technicianMapper.insert(tech);
                user.setTechnicianId(tech.getId());
            } else {
                applyTech(tech, parsed);
                tech.setUserId(user.getId());
                tech.setStatus(parsed.status);
                tech.setUpdatedAt(now);
                technicianMapper.updateById(tech);
                user.setTechnicianId(tech.getId());
            }
        } else if (tech != null) {
            tech.setStatus("DISABLED");
            tech.setUpdatedAt(now);
            technicianMapper.updateById(tech);
        }
        sysUserMapper.updateById(user);
        return toVo(user, "TECHNICIAN".equals(parsed.role) ? tech : null);
    }

    private StaffMemberVo updateTechOnly(Technician tech, Parsed parsed, LocalDateTime now) {
        if (!"TECHNICIAN".equals(parsed.role)) {
            if (parsed.username == null || parsed.password == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "改成管理员或业务员时，请填写用户名和密码");
            }
            assertUsernameFree(parsed.username);
            tech.setStatus("DISABLED");
            tech.setName(parsed.name);
            tech.setMobile(parsed.mobile);
            tech.setRemark(parsed.remark);
            tech.setUpdatedAt(now);
            SysUser user = newUser(parsed, tech.getId());
            insertUser(user);
            tech.setUserId(user.getId());
            technicianMapper.updateById(tech);
            return toVo(user, tech);
        }
        applyTech(tech, parsed);
        tech.setStatus(parsed.status);
        tech.setUpdatedAt(now);
        if (parsed.username != null) {
            if (parsed.password == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "开通登录请设置密码");
            }
            assertUsernameFree(parsed.username);
            SysUser user = newUser(parsed, tech.getId());
            insertUser(user);
            tech.setUserId(user.getId());
            technicianMapper.updateById(tech);
            return toVo(user, tech);
        }
        technicianMapper.updateById(tech);
        return toVo(null, tech);
    }

    private List<StaffMemberVo> loadAll() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        List<Technician> techs = technicianMapper.selectList(new LambdaQueryWrapper<Technician>().orderByAsc(Technician::getId));
        Set<Long> linked = new HashSet<>();
        List<StaffMemberVo> rows = new ArrayList<>();
        for (SysUser user : users) {
            Technician tech = null;
            if (user.getTechnicianId() != null) {
                linked.add(user.getTechnicianId());
                tech = findTech(techs, user.getTechnicianId());
            }
            rows.add(toVo(user, tech));
        }
        for (Technician tech : techs) {
            if (tech.getUserId() != null || linked.contains(tech.getId())) {
                continue;
            }
            rows.add(toVo(null, tech));
        }
        return rows;
    }

    private static Technician findTech(List<Technician> techs, Long id) {
        for (Technician tech : techs) {
            if (id.equals(tech.getId())) {
                return tech;
            }
        }
        return null;
    }

    private Technician linkedTech(SysUser user, Technician already) {
        if (already != null) {
            return already;
        }
        if (user.getTechnicianId() == null) {
            return null;
        }
        return technicianMapper.selectById(user.getTechnicianId());
    }

    private Located locate(String ref) {
        if (ref == null || !ref.matches("(user|tech)-\\d+")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        String kind = ref.substring(0, ref.indexOf('-'));
        long id = Long.parseLong(ref.substring(ref.indexOf('-') + 1));
        if ("user".equals(kind)) {
            SysUser user = sysUserMapper.selectById(id);
            if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
            }
            Technician tech = user.getTechnicianId() == null ? null : technicianMapper.selectById(user.getTechnicianId());
            return new Located(user, tech);
        }
        Technician tech = technicianMapper.selectById(id);
        if (tech == null || (tech.getDeleted() != null && tech.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        if (tech.getUserId() != null) {
            SysUser user = sysUserMapper.selectById(tech.getUserId());
            if (user != null && (user.getDeleted() == null || user.getDeleted() == 0)) {
                return new Located(user, tech);
            }
        }
        return new Located(null, tech);
    }

    private Parsed parse(StaffSaveRequest request, boolean creating, SysUser existingUser) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写员工信息");
        }
        String name = requireText(request.getName(), "姓名", 50);
        String mobile = requireText(request.getMobile(), "手机号", 20);
        if (!mobile.matches("1\\d{10}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号应为 11 位数字");
        }
        String role = request.getRole() == null ? "" : request.getRole().trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色只能是管理员、业务员或师傅");
        }
        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? "ENABLED"
                : normalizeStatus(request.getStatus());
        String remark = limit(blankToNull(request.getRemark()), "备注", 500);
        String username = blankToNull(request.getUsername());
        if (username == null && existingUser != null && !"TECHNICIAN".equals(role)) {
            username = existingUser.getUsername();
        }
        if (username == null && existingUser != null && "TECHNICIAN".equals(role) && existingUser.getUsername() != null) {
            username = existingUser.getUsername();
        }
        if (username != null) {
            if (username.length() < 3 || username.length() > 64 || username.chars().anyMatch(Character::isWhitespace)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名需要 3 到 64 位，且不能有空格");
            }
        }
        String password = blankToNull(request.getPassword());
        boolean needLogin = "ADMIN".equals(role) || "SALES".equals(role);
        if (needLogin && username == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员和业务员必须填写用户名和密码");
        }
        if (creating && needLogin && password == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员和业务员必须填写用户名和密码");
        }
        if (creating && "TECHNICIAN".equals(role) && username != null && password == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开通登录请设置密码");
        }
        if (password != null) {
            password = checkPassword(password);
        }
        String skills = skills(request.getSkills());
        String region = limit(blankToNull(request.getRegion()), "区域", 128);
        return new Parsed(name, mobile, role, status, remark, username, password, skills, region);
    }

    private SysUser newUser(Parsed parsed, Long technicianId) {
        if (parsed.username == null || parsed.password == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写用户名和密码");
        }
        assertUsernameFree(parsed.username);
        LocalDateTime now = DateTimes.now();
        SysUser user = new SysUser();
        user.setTenantId(1L);
        user.setUsername(parsed.username);
        user.setPassword(passwordEncoder.encode(parsed.password));
        user.setRole(parsed.role);
        user.setName(parsed.name);
        user.setMobile(parsed.mobile);
        user.setStatus(parsed.status);
        user.setRemark(parsed.remark);
        user.setTechnicianId(technicianId);
        user.setDeleted(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private void insertUser(SysUser user) {
        try {
            sysUserMapper.insert(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
    }

    private Technician newTechnician(Parsed parsed) {
        LocalDateTime now = DateTimes.now();
        Technician tech = new Technician();
        tech.setTenantId(1L);
        tech.setName(parsed.name);
        tech.setMobile(parsed.mobile);
        tech.setSkills(parsed.skills);
        tech.setRegion(parsed.region);
        tech.setRemark(parsed.remark);
        tech.setStatus(parsed.status);
        tech.setRating(new BigDecimal("5.00"));
        tech.setDeleted(0);
        tech.setCreatedAt(now);
        tech.setUpdatedAt(now);
        return tech;
    }

    private void applyTech(Technician tech, Parsed parsed) {
        tech.setName(parsed.name);
        tech.setMobile(parsed.mobile);
        tech.setSkills(parsed.skills);
        tech.setRegion(parsed.region);
        tech.setRemark(parsed.remark);
    }

    private void assertUsernameFree(String username) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
    }

    private void assertKeepsAdmin(SysUser user, String nextRole, String nextStatus) {
        boolean was = "ADMIN".equals(user.getRole()) && !"DISABLED".equalsIgnoreCase(user.getStatus());
        boolean still = "ADMIN".equals(nextRole) && !"DISABLED".equalsIgnoreCase(nextStatus);
        if (!was || still) {
            return;
        }
        Long others = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "ADMIN")
                .eq(SysUser::getStatus, "ENABLED")
                .ne(SysUser::getId, user.getId()));
        if (others == null || others == 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "不能停用或改掉最后一位管理员");
        }
    }

    private void assertNotSelf(SysUser user, String nextStatus) {
        if (!"DISABLED".equalsIgnoreCase(nextStatus)) {
            return;
        }
        LoginUser current = UserContext.require();
        if (user.getId().equals(current.getUserId())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "不能停用自己的账号");
        }
    }

    private SysUser reloadUser(Long id) {
        return sysUserMapper.selectById(id);
    }

    private StaffMemberVo toVo(SysUser user, Technician tech) {
        if (user != null) {
            String role = user.getRole() == null ? "STAFF" : user.getRole();
            Technician shown = "TECHNICIAN".equals(role) ? tech : null;
            return new StaffMemberVo(
                    "user-" + user.getId(),
                    user.getId(),
                    shown == null ? null : shown.getId(),
                    user.getName() == null ? user.getUsername() : user.getName(),
                    user.getMobile() == null ? "" : user.getMobile(),
                    role,
                    user.getStatus() == null || user.getStatus().isBlank() ? "ENABLED" : user.getStatus(),
                    user.getRemark() == null ? "" : user.getRemark(),
                    user.getUsername(),
                    true,
                    shown == null ? List.of() : Jsons.splitComma(shown.getSkills()),
                    shown == null || shown.getRegion() == null ? "" : shown.getRegion()
            );
        }
        return new StaffMemberVo(
                "tech-" + tech.getId(),
                null,
                tech.getId(),
                tech.getName(),
                tech.getMobile() == null ? "" : tech.getMobile(),
                "TECHNICIAN",
                tech.getStatus() == null || tech.getStatus().isBlank() ? "ENABLED" : tech.getStatus(),
                tech.getRemark() == null ? "" : tech.getRemark(),
                null,
                false,
                Jsons.splitComma(tech.getSkills()),
                tech.getRegion() == null ? "" : tech.getRegion()
        );
    }

    private static void requireAdmin() {
        LoginUser user = UserContext.require();
        boolean admin = user.getAuthorities().stream().anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
        if (!admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有管理员可以管理员工");
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择状态");
        }
        String text = status.trim().toUpperCase(Locale.ROOT);
        if (!"ENABLED".equals(text) && !"DISABLED".equals(text)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能是启用或停用");
        }
        return text;
    }

    private static String checkPassword(String password) {
        String raw = password == null ? "" : password.trim();
        if (raw.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码至少 6 位");
        }
        if (raw.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能超过 64 位");
        }
        return raw;
    }

    private static String requireText(String value, String label, int max) {
        String text = blankToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写" + label);
        }
        if (text.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + max + " 个字");
        }
        return text;
    }

    private static String limit(String value, String label, int max) {
        if (value != null && value.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + max + " 个字");
        }
        return value;
    }

    private static String skills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        String joined = String.join(",", skills.stream().map(item -> item == null ? "" : item.trim()).filter(item -> !item.isEmpty()).toList());
        if (joined.isEmpty()) {
            return null;
        }
        if (joined.length() > 255) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能太长");
        }
        return joined;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean contains(String value, String key) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(key);
    }

    private record Parsed(
            String name,
            String mobile,
            String role,
            String status,
            String remark,
            String username,
            String password,
            String skills,
            String region
    ) {
    }

    private record Located(SysUser user, Technician tech) {
    }
}
