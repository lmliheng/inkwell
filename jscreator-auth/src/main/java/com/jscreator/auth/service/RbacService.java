package com.jscreator.auth.service;

import com.jscreator.auth.mapper.RbacMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RBAC 业务规则，对齐 Express 版 modules/rbac/rbac.controller + rbac.dao。
 *
 * <p>SQL 失败不在这里吞掉：交给 GlobalExceptionHandler 渲染成
 * HTTP 500 + {@code {code:500,success:false,message:'服务器内部错误'}}，与原版一致。
 */
@Service
public class RbacService {

    private final RbacMapper rbacMapper;

    public RbacService(RbacMapper rbacMapper) {
        this.rbacMapper = rbacMapper;
    }

    public List<LinkedHashMap<String, Object>> roleGetAll() {
        return rbacMapper.roleGetAll();
    }

    public List<Object> rolePermissionIds(Object roleId) {
        return rbacMapper.rolePermissionIds(roleId);
    }

    /** 先清空再写入，同一事务；原版跳过 null/undefined/'' 这三种元素。 */
    @Transactional
    public void setRolePermission(Object roleId, List<?> permissionIdList) {
        rbacMapper.deleteRolePermissions(roleId);
        for (Object permissionId : permissionIdList) {
            if (permissionId == null || "".equals(permissionId)) {
                continue;
            }
            rbacMapper.insertRolePermission(roleId, permissionId);
        }
    }

    public void roleAdd(String roleName) {
        rbacMapper.roleAdd(roleName);
    }

    public void roleUpdateName(Object roleId, String roleName) {
        rbacMapper.roleUpdateName(roleId, roleName);
    }

    public void roleDelete(Object roleId) {
        rbacMapper.roleDelete(roleId);
    }

    public List<LinkedHashMap<String, Object>> permissionGetAll() {
        return rbacMapper.permissionGetAll();
    }

    public void permissionUpdate(Object permissionId, String permissionName, Object permissionDescription) {
        rbacMapper.permissionUpdate(permissionId, permissionName, permissionDescription);
    }
}
